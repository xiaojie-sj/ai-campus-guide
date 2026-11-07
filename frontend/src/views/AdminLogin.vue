<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as THREE from 'three'
import { authApi, ApiError } from '../api/client'
import { safeAdminRedirect } from '../auth'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()
const route = useRoute()
const demoMode = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
const username = ref(demoMode ? 'admin' : '')
const password = ref(demoMode ? 'Demo@123' : '')
const busy = ref(false)
const error = ref('')
const visualHost = ref<HTMLDivElement>()
const showPassword = ref(false)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let group: THREE.Group | null = null
let particleCloud: THREE.Points | null = null
let frame = 0
let observer: ResizeObserver | null = null
const pointer = { x: 0, y: 0 }
const reducedMotion = typeof window !== 'undefined'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches

async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await authApi.login(username.value.trim(), password.value)
    await router.replace(safeAdminRedirect(route.query.redirect))
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

function fillDemo(account: string) {
  username.value = account
  password.value = 'Demo@123'
}

function initVisual() {
  const host = visualHost.value
  if (!host) return

  renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(host.clientWidth, host.clientHeight, false)
  host.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(40, host.clientWidth / Math.max(1, host.clientHeight), 0.1, 60)
  camera.position.set(0, 0.15, 9.8)

  group = new THREE.Group()
  group.rotation.x = -0.18
  scene.add(group)

  const coreGeometry = new THREE.IcosahedronGeometry(2.65, 3)
  const wire = new THREE.LineSegments(
    new THREE.WireframeGeometry(coreGeometry),
    new THREE.LineBasicMaterial({ color: 0x2dd4bf, transparent: true, opacity: 0.2 }),
  )
  group.add(wire)

  const positions = coreGeometry.getAttribute('position')
  const corePoints = new THREE.Points(
    new THREE.BufferGeometry().setAttribute('position', positions.clone()),
    new THREE.PointsMaterial({ color: 0x67e8f9, size: 0.038, transparent: true, opacity: 0.82 }),
  )
  group.add(corePoints)

  const glow = new THREE.Mesh(
    new THREE.SphereGeometry(2.28, 40, 40),
    new THREE.MeshBasicMaterial({
      color: 0x0ea5e9,
      transparent: true,
      opacity: 0.075,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    }),
  )
  group.add(glow)

  const ringA = new THREE.Mesh(
    new THREE.TorusGeometry(3.7, 0.012, 8, 180),
    new THREE.MeshBasicMaterial({
      color: 0x38bdf8,
      transparent: true,
      opacity: 0.38,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    }),
  )
  ringA.rotation.x = Math.PI / 2.45
  group.add(ringA)

  const ringB = new THREE.Mesh(
    new THREE.TorusGeometry(4.25, 0.008, 8, 180),
    new THREE.MeshBasicMaterial({
      color: 0x8b5cf6,
      transparent: true,
      opacity: 0.32,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
    }),
  )
  ringB.rotation.x = Math.PI / 1.65
  ringB.rotation.y = Math.PI / 4.8
  group.add(ringB)

  const particlePositions = new Float32Array(340 * 3)
  for (let index = 0; index < 340; index += 1) {
    const radius = 4.8 + Math.random() * 6.2
    const theta = Math.random() * Math.PI * 2
    const phi = Math.acos(2 * Math.random() - 1)
    particlePositions[index * 3] = radius * Math.sin(phi) * Math.cos(theta)
    particlePositions[index * 3 + 1] = radius * Math.cos(phi) * 0.75
    particlePositions[index * 3 + 2] = radius * Math.sin(phi) * Math.sin(theta)
  }
  const particleGeometry = new THREE.BufferGeometry()
  particleGeometry.setAttribute('position', new THREE.BufferAttribute(particlePositions, 3))
  particleCloud = new THREE.Points(
    particleGeometry,
    new THREE.PointsMaterial({ color: 0x7dd3fc, size: 0.02, transparent: true, opacity: 0.48 }),
  )
  scene.add(particleCloud)

  const startedAt = performance.now()
  const animate = () => {
    frame = requestAnimationFrame(animate)
    if (!renderer || !scene || !camera || !group || !particleCloud || document.hidden) return
    const time = (performance.now() - startedAt) / 1000
    group.rotation.y = time * 0.11 + pointer.x * 0.28
    group.rotation.x = -0.18 + pointer.y * 0.16
    group.rotation.z = Math.sin(time * 0.16) * 0.04
    particleCloud.rotation.y = -time * 0.012
    renderer.render(scene, camera)
  }

  if (reducedMotion) renderer.render(scene, camera)
  else animate()

  const onPointerMove = (event: PointerEvent) => {
    const rect = host.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / Math.max(1, rect.width) - 0.5) * 2
    pointer.y = ((event.clientY - rect.top) / Math.max(1, rect.height) - 0.5) * 2
  }
  host.addEventListener('pointermove', onPointerMove)
  host.addEventListener('pointerleave', () => {
    pointer.x = 0
    pointer.y = 0
  })

  observer = new ResizeObserver(() => {
    if (!renderer || !camera || !host) return
    const width = host.clientWidth
    const height = host.clientHeight
    if (!width || !height) return
    renderer.setSize(width, height, false)
    camera.aspect = width / height
    camera.updateProjectionMatrix()
  })
  observer.observe(host)
}

function disposeVisual() {
  cancelAnimationFrame(frame)
  observer?.disconnect()
  if (scene) {
    scene.traverse((object) => {
      const drawable = object as THREE.Mesh
      drawable.geometry?.dispose()
      const material = drawable.material as THREE.Material | THREE.Material[] | undefined
      if (Array.isArray(material)) material.forEach((item) => item.dispose())
      else material?.dispose()
    })
  }
  renderer?.dispose()
  renderer?.domElement.remove()
  renderer = null
  scene = null
  camera = null
  group = null
  particleCloud = null
}

onMounted(initVisual)
onBeforeUnmount(disposeVisual)
</script>

<template>
  <main class="login-page">
    <div class="ambient-grid" aria-hidden="true" />
    <div class="scanlines" aria-hidden="true" />

    <section class="brand-stage">
      <header class="brand-lockup">
        <span class="brand-emblem"><AppIcon name="spark" :size="20" /></span>
        <span>
          <strong>HBEUTC</strong>
          <small>CAMPUS INTELLIGENCE SYSTEM</small>
        </span>
      </header>

      <div class="orbital-stage" aria-hidden="true">
        <div ref="visualHost" class="visual-canvas" />
        <span class="orbit-ring orbit-ring-a" />
        <span class="orbit-ring orbit-ring-b" />
        <span class="orbit-tag tag-a">SPATIAL DATA</span>
        <span class="orbit-tag tag-b">PRIVATE RAG</span>
        <span class="orbit-tag tag-c">3D DIGITAL HUMAN</span>
      </div>

      <div class="brand-content">
        <span class="brand-kicker"><i /> 澜境智航 · 校园数字运营中枢</span>
        <h1>让校园知识<br /><em>进入三维空间</em></h1>
        <p>统一管理校园点位、路网、私有知识库、数字人行为与对话分析，在高安全边界内驱动可信的校园智能服务。</p>
        <div class="capabilities">
          <article><b>01</b><span>SPATIAL</span><strong>三维校园底座</strong></article>
          <article><b>02</b><span>KNOWLEDGE</span><strong>私有 RAG 引擎</strong></article>
          <article><b>03</b><span>OBSERVABILITY</span><strong>实时运营驾驶舱</strong></article>
        </div>
      </div>

      <footer class="signal-footer">
        <span><i /> SYSTEM HEALTH 99.99%</span>
        <span>31.92°N / 114.92°E</span>
        <span>SESSION ENCRYPTED · TLS 1.3</span>
      </footer>
    </section>

    <section class="auth-stage">
      <a class="back-link" href="/"><AppIcon name="arrowLeft" :size="16" /> 返回公众导览</a>

      <div class="auth-card">
        <div class="auth-head">
          <span class="security-chip"><i /> SECURE ADMIN CHANNEL</span>
          <span class="sequence">AUTH-01 / 04</span>
        </div>

        <h2>管理员身份验证</h2>
        <p id="login-intro" class="intro">
          {{ demoMode ? '演示账号已预填。所有管理操作将写入审计轨迹，请勿在生产环境沿用默认口令。' : '管理会话通过 HttpOnly Cookie 保存，未登录访问后台路径会统一回到本页。' }}
        </p>

        <form @submit.prevent="login" aria-describedby="login-intro">
          <label>
            <span>操作员账号 <em>OPERATOR ID</em></span>
            <div class="input-shell">
              <i aria-hidden="true"><AppIcon name="user" :size="17" /></i>
              <input v-model="username" autocomplete="username" maxlength="64" placeholder="请输入管理员账号" required />
            </div>
          </label>
          <label>
            <span>访问口令 <em>ACCESS KEY</em></span>
            <div class="input-shell">
              <i aria-hidden="true"><AppIcon name="lock" :size="17" /></i>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                maxlength="200"
                placeholder="请输入访问口令"
                required
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="showPassword ? '隐藏访问口令' : '显示访问口令'"
                :aria-pressed="showPassword"
                @click="showPassword = !showPassword"
              >
                <AppIcon :name="showPassword ? 'eyeOff' : 'eye'" :size="17" />
              </button>
            </div>
          </label>

          <p v-if="error" class="form-error" role="alert" aria-live="polite"><i><AppIcon name="shield" :size="15" /></i>{{ error }}</p>

          <button type="submit" class="login-submit" :disabled="busy || !username || !password">
            <span>{{ busy ? '正在进行安全校验…' : '建立安全会话' }}</span>
            <b :class="{ spinning: busy }"><AppIcon :name="busy ? 'refresh' : 'arrow'" :size="18" /></b>
          </button>
        </form>

        <div class="trust-row">
          <span><i /> HttpOnly Cookie</span>
          <span><i /> RBAC 最小权限</span>
          <span><i /> 审计留痕</span>
        </div>

        <div v-if="demoMode" class="demo-panel">
          <header>
            <span>DEMO OPERATORS</span>
            <small>统一口令 Demo@123</small>
          </header>
          <div class="demo-grid">
            <button type="button" @click="fillDemo('admin')"><b>admin</b><small>系统管理员</small></button>
            <button type="button" @click="fillDemo('editor')"><b>editor</b><small>内容管理员</small></button>
            <button type="button" @click="fillDemo('reviewer')"><b>reviewer</b><small>内容审核员</small></button>
            <button type="button" @click="fillDemo('analyst')"><b>analyst</b><small>数据分析师</small></button>
          </div>
        </div>

        <p class="auth-note">管理账号采用邀请制开通，如需新增权限请联系系统管理员。</p>
      </div>

      <footer class="auth-footer">
        <span>© 2026 HBEUTC · CAMPUS GUIDE</span>
        <span>SECURITY LEVEL / INTERNAL</span>
      </footer>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  --login-bg: #040d29;
  --login-line: rgb(125 150 190 / 14%);
  --login-text: #e6f1ff;
  --login-muted: #7185a5;
  --login-cyan: #00dcf6;
  position: relative;
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  grid-template-columns: minmax(0, 1.12fr) minmax(26rem, 0.88fr);
  overflow: hidden;
  color: var(--login-text);
  background:
    radial-gradient(58rem 38rem at 8% 2%, rgb(0 220 246 / 11%), transparent 62%),
    radial-gradient(48rem 40rem at 88% 100%, rgb(73 116 235 / 12%), transparent 62%),
    linear-gradient(145deg, #040d29, #051436 55%, #030a1c);
}

.ambient-grid,
.scanlines {
  position: absolute;
  z-index: 0;
  inset: 0;
  pointer-events: none;
}

.ambient-grid {
  background-image:
    linear-gradient(rgb(125 150 190 / 5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(125 150 190 / 5%) 1px, transparent 1px);
  background-size: 62px 62px;
  mask-image: radial-gradient(ellipse at 35% 40%, #000 12%, transparent 72%);
}

.scanlines {
  opacity: 0.22;
  background: repeating-linear-gradient(180deg, transparent 0 3px, rgb(125 150 190 / 2.6%) 3px 4px);
}

.brand-stage {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--login-line);
  padding: 2rem clamp(2rem, 4.5vw, 5rem) 1.4rem;
}

.brand-lockup {
  display: flex;
  align-items: center;
  gap: 0.78rem;
}

.brand-emblem {
  display: grid;
  width: 2.65rem;
  height: 2.65rem;
  place-items: center;
  border: 1px solid rgb(0 220 246 / 40%);
  border-radius: 0.72rem;
  color: #05121e;
  background: linear-gradient(135deg, #6ff5ff, #4974eb 64%, #2c8fc0);
  box-shadow: 0 0 28px rgb(0 220 246 / 26%);
  font-size: 1.1rem;
}

.brand-lockup > span:last-child {
  display: grid;
  gap: 0.16rem;
}

.brand-lockup strong {
  color: #f0f9ff;
  font: 700 1rem/1 var(--font-mono);
  letter-spacing: 0.11em;
}

.brand-lockup small {
  color: #607694;
  font: 0.46rem/1 var(--font-mono);
  letter-spacing: 0.16em;
}

.orbital-stage {
  position: absolute;
  z-index: 0;
  top: 7%;
  right: -8%;
  width: min(46vw, 44rem);
  height: min(46vw, 44rem);
  opacity: 0.9;
  pointer-events: none;
}

.visual-canvas {
  position: absolute;
  inset: 0;
  pointer-events: auto;
}

.visual-canvas :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
}

.orbit-ring {
  position: absolute;
  border: 1px solid rgb(0 220 246 / 10%);
  border-radius: 50%;
}

.orbit-ring-a {
  inset: 13%;
  box-shadow: inset 0 0 60px rgb(0 220 246 / 4%);
}

.orbit-ring-b {
  inset: 2%;
  border-style: dashed;
  border-color: rgb(73 116 235 / 10%);
  animation: orbit-spin 60s linear infinite;
}

.orbit-tag {
  position: absolute;
  border: 1px solid rgb(0 220 246 / 18%);
  border-radius: 999px;
  padding: 0.28rem 0.54rem;
  color: #6c87a8;
  background: rgb(4 12 25 / 66%);
  font: 0.46rem/1 var(--font-mono);
  letter-spacing: 0.08em;
  backdrop-filter: blur(8px);
}

.tag-a { top: 18%; left: 4%; }
.tag-b { top: 42%; right: 1%; }
.tag-c { right: 8%; bottom: 18%; }

.brand-content {
  position: relative;
  z-index: 2;
  display: grid;
  width: min(39rem, 76%);
  margin-top: auto;
  margin-bottom: clamp(2rem, 5vh, 4rem);
  gap: 1rem;
}

.brand-kicker {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  color: #6ff5ff;
  font: 0.58rem/1.2 var(--font-mono);
  letter-spacing: 0.16em;
}

.brand-kicker i {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 12px #34d399;
  animation: pulse 2.4s infinite;
}

.brand-content h1 {
  margin: 0;
  color: #edf9ff;
  font: 600 clamp(3rem, 5.6vw, 6.1rem)/0.98 var(--font-body);
  letter-spacing: -0.065em;
  text-shadow: 0 0 52px rgb(0 220 246 / 9%);
}

.brand-content h1 em {
  color: transparent;
  font-style: normal;
  background: linear-gradient(100deg, #6ff5ff, #60a5fa 44%, #c4b5fd);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.brand-content > p {
  max-width: 34rem;
  margin: 0;
  color: #7388a8;
  font-size: 0.78rem;
  line-height: 1.85;
}

.capabilities {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.55rem;
  margin-top: 0.45rem;
}

.capabilities article {
  display: grid;
  gap: 0.18rem;
  border: 1px solid var(--login-line);
  border-radius: 0.72rem;
  padding: 0.65rem 0.72rem;
  background: linear-gradient(145deg, rgb(13 28 52 / 64%), rgb(5 14 29 / 66%));
  backdrop-filter: blur(12px);
}

.capabilities b {
  color: #4d6686;
  font: 0.48rem/1 var(--font-mono);
}

.capabilities span {
  color: #4fd1e8;
  font: 0.45rem/1.2 var(--font-mono);
  letter-spacing: 0.1em;
}

.capabilities strong {
  color: #a9bedb;
  font-size: 0.62rem;
  font-weight: 500;
}

.signal-footer {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  border-top: 1px solid var(--login-line);
  padding-top: 0.9rem;
  color: #4f6483;
  font: 0.45rem/1.3 var(--font-mono);
  letter-spacing: 0.09em;
}

.signal-footer span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  color: #5d8d87;
}

.signal-footer i,
.security-chip i,
.trust-row i {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 8px #34d399;
}

.auth-stage {
  position: relative;
  z-index: 2;
  display: grid;
  min-height: 100vh;
  min-height: 100dvh;
  place-items: center;
  padding: 4rem clamp(1.5rem, 4vw, 4.2rem) 3.2rem;
  background:
    linear-gradient(180deg, rgb(125 150 190 / 2.5%), transparent 24%),
    rgb(4 10 20 / 38%);
  backdrop-filter: blur(6px);
}

.back-link {
  position: absolute;
  top: 1.55rem;
  right: clamp(1.5rem, 4vw, 3.2rem);
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: #667c9a;
  font-size: 0.62rem;
  text-decoration: none;
  transition: color 160ms ease;
}

.back-link:hover {
  color: #b8e9f5;
}

.back-link span {
  color: #4ecbdf;
}

.auth-card {
  width: min(100%, 27rem);
  border: 1px solid rgb(125 150 190 / 16%);
  border-radius: 1.15rem;
  padding: clamp(1.35rem, 3vw, 2rem);
  background:
    linear-gradient(145deg, rgb(14 29 53 / 88%), rgb(5 13 27 / 94%));
  box-shadow:
    0 32px 90px rgb(0 0 0 / 44%),
    inset 0 1px 0 rgb(255 255 255 / 3%),
    0 0 70px rgb(0 220 246 / 4%);
  backdrop-filter: blur(28px);
}

.auth-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.7rem;
}

.security-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
  border: 1px solid rgb(52 211 153 / 22%);
  border-radius: 999px;
  padding: 0.32rem 0.52rem;
  color: #7de8bd;
  background: rgb(16 185 129 / 7%);
  font: 0.45rem/1 var(--font-mono);
  letter-spacing: 0.1em;
}

.sequence {
  color: #4d6382;
  font: 0.46rem/1 var(--font-mono);
  letter-spacing: 0.08em;
}

.auth-card h2 {
  margin: 1.15rem 0 0.45rem;
  color: #eff9ff;
  font: 600 clamp(1.65rem, 2.4vw, 2.1rem)/1.1 var(--font-body);
  letter-spacing: -0.04em;
}

.intro {
  margin: 0 0 1.35rem;
  color: #7085a5;
  font-size: 0.67rem;
  line-height: 1.75;
}

form {
  display: grid;
  gap: 0.85rem;
}

form label {
  display: grid;
  gap: 0.35rem;
}

form label > span {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #8ba0bf;
  font-size: 0.59rem;
}

form label em {
  color: #465b79;
  font: 0.44rem/1 var(--font-mono);
  font-style: normal;
  letter-spacing: 0.1em;
}

.input-shell {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  border: 1px solid rgb(125 150 190 / 17%);
  border-radius: 0.65rem;
  padding: 0 0.7rem;
  background: rgb(3 10 22 / 56%);
  transition: border-color 170ms ease, box-shadow 170ms ease, background 170ms ease;
}

.input-shell:focus-within {
  border-color: rgb(0 220 246 / 54%);
  background: rgb(4 13 27 / 82%);
  box-shadow: 0 0 0 3px rgb(0 220 246 / 7%), 0 0 24px rgb(0 220 246 / 7%);
}

.input-shell i {
  color: #4c8ba0;
  font-size: 0.7rem;
  font-style: normal;
}

input {
  width: 100%;
  border: 0;
  outline: 0;
  padding: 0.74rem 0;
  color: #e4f2ff;
  background: transparent;
  font: 0.73rem/1.2 var(--font-body);
}

input::placeholder {
  color: #3f5471;
}

.form-error {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  margin: 0;
  border: 1px solid rgb(251 113 133 / 25%);
  border-radius: 0.52rem;
  padding: 0.55rem 0.62rem;
  color: #fecdd3;
  background: rgb(244 63 94 / 8%);
  font-size: 0.6rem;
  line-height: 1.5;
}

.form-error i {
  display: grid;
  width: 0.9rem;
  height: 0.9rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 50%;
  color: #2a0710;
  background: #fb7185;
  font: 700 0.52rem/1 var(--font-mono);
}

.login-submit {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin-top: 0.2rem;
  border: 1px solid rgb(0 220 246 / 40%);
  border-radius: 0.68rem;
  padding: 0.82rem 0.9rem;
  color: #04131e;
  background: linear-gradient(110deg, #6ff5ff, #4974eb 58%, #4974eb);
  font-size: 0.68rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 8px 30px rgb(0 220 246 / 14%);
  transition: transform 170ms ease, box-shadow 170ms ease;
}

.login-submit:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 10px 38px rgb(0 220 246 / 24%);
}

.login-submit b {
  font: 600 0.95rem/1 var(--font-mono);
}

.trust-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.42rem 0.8rem;
  margin-top: 0.9rem;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid var(--login-line);
}

.trust-row span {
  display: inline-flex;
  align-items: center;
  gap: 0.32rem;
  color: #536a89;
  font: 0.43rem/1.2 var(--font-mono);
}

.trust-row i {
  width: 0.26rem;
  height: 0.26rem;
  background: #00dcf6;
  box-shadow: 0 0 6px #00dcf6;
}

.demo-panel {
  margin-top: 0.85rem;
  border: 1px solid var(--login-line);
  border-radius: 0.68rem;
  padding: 0.62rem;
  background: rgb(3 10 22 / 38%);
}

.demo-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.7rem;
  margin-bottom: 0.5rem;
}

.demo-panel header span {
  color: #4ecbdf;
  font: 0.44rem/1 var(--font-mono);
  letter-spacing: 0.12em;
}

.demo-panel header small {
  color: #4e6380;
  font: 0.43rem/1 var(--font-mono);
}

.demo-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.3rem;
}

.demo-grid button {
  display: grid;
  gap: 0.16rem;
  border: 1px solid rgb(125 150 190 / 12%);
  border-radius: 0.42rem;
  padding: 0.38rem 0.2rem;
  color: #7590b2;
  background: rgb(125 150 190 / 4%);
  cursor: pointer;
  transition: border-color 150ms ease, background 150ms ease, color 150ms ease;
}

.demo-grid button:hover {
  border-color: rgb(0 220 246 / 24%);
  color: #c8f4fb;
  background: rgb(0 220 246 / 7%);
}

.demo-grid b {
  font: 600 0.48rem/1 var(--font-mono);
}

.demo-grid small {
  color: #4e6380;
  font-size: 0.4rem;
}

.auth-note {
  margin: 0.72rem 0 0;
  color: #455a77;
  font-size: 0.52rem;
  line-height: 1.55;
  text-align: center;
}

.auth-footer {
  position: absolute;
  right: clamp(1.5rem, 4vw, 3.2rem);
  bottom: 1.35rem;
  left: clamp(1.5rem, 4vw, 3.2rem);
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  color: #3d506b;
  font: 0.4rem/1.2 var(--font-mono);
  letter-spacing: 0.08em;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgb(52 211 153 / 52%); }
  70% { box-shadow: 0 0 0 7px rgb(52 211 153 / 0%); }
  100% { box-shadow: 0 0 0 0 rgb(52 211 153 / 0%); }
}

@keyframes orbit-spin {
  to { transform: rotate(360deg); }
}

@media (max-width: 1100px) {
  .login-page {
    grid-template-columns: minmax(0, 1fr) minmax(24rem, 0.9fr);
  }

  .brand-content {
    width: 92%;
  }

  .brand-content h1 {
    font-size: clamp(2.7rem, 5.2vw, 4.4rem);
  }

  .capabilities {
    grid-template-columns: 1fr;
    max-width: 14rem;
  }
}

@media (max-width: 900px) {
  .login-page {
    display: block;
    min-height: 100dvh;
    overflow: auto;
  }

  .brand-stage {
    min-height: 13.5rem;
    border-right: 0;
    border-bottom: 1px solid var(--login-line);
    padding: 1.15rem 1.35rem 1.05rem;
  }

  .orbital-stage {
    top: -46%;
    right: -18%;
    width: 24rem;
    height: 24rem;
    opacity: 0.32;
  }

  .brand-content {
    width: 100%;
    margin-top: auto;
    margin-bottom: 0;
  }

  .brand-content h1 {
    font-size: clamp(1.9rem, 7.2vw, 2.6rem);
  }

  .brand-content > p {
    max-width: 29rem;
    font-size: 0.7rem;
  }

  .capabilities {
    display: none;
  }

  .signal-footer {
    margin-top: 0.55rem;
  }

  .signal-footer span:nth-child(2),
  .signal-footer span:nth-child(3) {
    display: none;
  }

  .auth-stage {
    min-height: auto;
    padding: 2.1rem 1.25rem 2.75rem;
  }

  .auth-card {
    border-radius: 1rem;
  }

  .back-link {
    top: 1.35rem;
    right: 1.25rem;
  }

  .auth-footer {
    right: 1.25rem;
    bottom: 1.15rem;
    left: 1.25rem;
  }
}

@media (max-width: 480px) {
  .brand-stage {
    min-height: 11.5rem;
  }

  .brand-lockup small {
    display: none;
  }

  .orbital-stage {
    top: -34%;
    right: -46%;
    opacity: 0.3;
  }

  .brand-content h1 {
    font-size: 1.95rem;
  }

  .brand-content > p {
    display: none;
  }

  .brand-kicker {
    font-size: 0.5rem;
    letter-spacing: 0.1em;
  }

  .auth-stage {
    padding: 1.6rem 0.9rem 2.2rem;
  }

  .auth-card {
    padding: 1.15rem;
  }

  .demo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .auth-footer span:last-child {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .orbit-ring-b,
  .brand-kicker i {
    animation: none;
  }

  .login-submit {
    transition: none;
  }
}

/* Cohesive login refinement: clearer hierarchy, larger type, accessible controls */
.login-page {
  --login-bg: #020817;
  --login-line: rgb(138 169 214 / 15%);
  --login-text: #f3f7ff;
  --login-muted: #8295b4;
  --login-cyan: #35d7ff;
  background:
    radial-gradient(58rem 38rem at 8% 2%, rgb(53 215 255 / 10%), transparent 62%),
    radial-gradient(48rem 40rem at 88% 100%, rgb(92 124 250 / 11%), transparent 62%),
    linear-gradient(145deg, #020817, #061226 55%, #020817);
}

.ambient-grid {
  background-size: 72px 72px;
  opacity: 0.72;
}

.scanlines { opacity: 0.08; }

.brand-stage {
  padding: 2.15rem clamp(2rem, 4.6vw, 5.2rem) 1.55rem;
}

.brand-emblem {
  width: 2.8rem;
  height: 2.8rem;
  border-color: rgb(53 215 255 / 44%);
  border-radius: 0.82rem;
  color: #03101f;
  background: linear-gradient(145deg, #8bf4ff, #35d7ff 46%, #5c7cfa);
  box-shadow: 0 10px 30px rgb(53 215 255 / 22%);
}

.brand-lockup strong {
  font-size: 1.02rem;
}

.brand-lockup small {
  color: #7890b0;
  font-size: 0.59rem;
  letter-spacing: 0.13em;
}

.orbit-tag {
  border-color: rgb(53 215 255 / 20%);
  color: #819aba;
  background: rgb(3 13 29 / 72%);
  font-size: 0.54rem;
}

.brand-kicker {
  color: #87f1ff;
  font-size: 0.67rem;
}

.brand-content {
  gap: 1.15rem;
}

.brand-content > p {
  color: #90a3c0;
  font-size: 0.87rem;
  line-height: 1.85;
}

.capabilities {
  gap: 0.65rem;
}

.capabilities article {
  border-color: rgb(138 169 214 / 15%);
  border-radius: 0.85rem;
  padding: 0.75rem 0.8rem;
  background: linear-gradient(145deg, rgb(9 29 57 / 70%), rgb(3 13 29 / 72%));
}

.capabilities b {
  color: #6983a5;
  font-size: 0.56rem;
}

.capabilities span {
  color: #72e5f5;
  font-size: 0.54rem;
}

.capabilities strong {
  color: #cbd9ed;
  font-size: 0.72rem;
}

.signal-footer {
  color: #6b81a1;
  font-size: 0.55rem;
}

.auth-stage {
  padding: 4.2rem clamp(1.5rem, 4vw, 4.4rem) 3.5rem;
  background:
    linear-gradient(180deg, rgb(138 169 214 / 3%), transparent 24%),
    rgb(2 8 20 / 48%);
}

.back-link {
  min-height: 2.75rem;
  align-items: center;
  color: #8198b8;
  font-size: 0.76rem;
}

.back-link:hover { color: #d9f8ff; }

.auth-card {
  width: min(100%, 29rem);
  border-color: rgb(138 169 214 / 17%);
  border-radius: 1.35rem;
  padding: clamp(1.55rem, 3vw, 2.2rem);
  background: linear-gradient(145deg, rgb(10 30 58 / 92%), rgb(3 12 27 / 96%));
  box-shadow:
    0 34px 96px rgb(0 0 0 / 46%),
    inset 0 1px 0 rgb(255 255 255 / 4%),
    0 0 80px rgb(53 215 255 / 5%);
}

.security-chip {
  border-color: rgb(52 211 153 / 24%);
  padding: 0.38rem 0.6rem;
  color: #8cf0c8;
  font-size: 0.56rem;
}

.sequence {
  color: #667e9f;
  font-size: 0.56rem;
}

.auth-card h2 {
  margin-top: 1.25rem;
  font-size: clamp(1.8rem, 2.5vw, 2.2rem);
}

.intro {
  margin-bottom: 1.5rem;
  color: #8fa3c1;
  font-size: 0.82rem;
  line-height: 1.75;
}

form {
  gap: 1rem;
}

form label > span {
  color: #a7b7cf;
  font-size: 0.78rem;
}

form label em {
  color: #657c9c;
  font-size: 0.56rem;
}

.input-shell {
  min-height: 3.25rem;
  gap: 0.65rem;
  border-color: rgb(138 169 214 / 18%);
  border-radius: 0.82rem;
  padding: 0 0.85rem;
  background: rgb(2 10 24 / 64%);
}

.input-shell:focus-within {
  border-color: rgb(53 215 255 / 56%);
  background: rgb(3 14 29 / 88%);
  box-shadow: 0 0 0 3px rgb(53 215 255 / 8%), 0 0 28px rgb(53 215 255 / 8%);
}

.input-shell > i {
  display: grid;
  width: 1.2rem;
  place-items: center;
  color: #61cde1;
  font-size: initial;
}

.input-shell input {
  min-height: 3.1rem;
  padding: 0.8rem 0;
  color: #eef6ff;
  font-size: 0.9rem;
}

.input-shell input::placeholder { color: #637897; }

.password-toggle {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  flex: 0 0 auto;
  place-items: center;
  border: 0;
  border-radius: 0.55rem;
  color: #7890b1;
  background: transparent;
}

.password-toggle:hover {
  color: #a9f4ff;
  background: rgb(53 215 255 / 8%);
}

.form-error {
  border-radius: 0.7rem;
  padding: 0.7rem 0.8rem;
  font-size: 0.78rem;
}

.form-error i {
  display: grid;
  width: 1.2rem;
  height: 1.2rem;
  flex: 0 0 auto;
  place-items: center;
  font-style: normal;
}

.login-submit {
  min-height: 3.25rem;
  border-radius: 0.85rem;
  font-size: 0.86rem;
}

.login-submit b { display: inline-flex; align-items: center; }
.login-submit b.spinning { animation: submit-spin 900ms linear infinite; }

.trust-row {
  gap: 0.5rem 0.9rem;
  margin-top: 1.05rem;
  padding-bottom: 1rem;
}

.trust-row span {
  color: #7e94b4;
  font-size: 0.62rem;
}

.demo-panel {
  border-color: rgb(138 169 214 / 15%);
  border-radius: 0.85rem;
  padding: 0.75rem;
  background: rgb(2 10 24 / 48%);
}

.demo-panel header span { font-size: 0.57rem; }
.demo-panel header small { color: #7087a7; font-size: 0.56rem; }

.demo-grid { gap: 0.42rem; }

.demo-grid button {
  min-height: 3.1rem;
  border-color: rgb(138 169 214 / 14%);
  border-radius: 0.6rem;
  padding: 0.48rem 0.25rem;
  color: #9db1cc;
  background: rgb(138 169 214 / 4%);
}

.demo-grid button:hover {
  border-color: rgb(53 215 255 / 28%);
  color: #e3fbff;
  background: rgb(53 215 255 / 8%);
}

.demo-grid b { font-size: 0.66rem; }
.demo-grid small { color: #758baa; font-size: 0.55rem; }

.auth-note {
  color: #7187a7;
  font-size: 0.68rem;
}

.auth-footer {
  color: #5e7595;
  font-size: 0.53rem;
}

@keyframes submit-spin { to { transform: rotate(360deg); } }

@media (max-width: 900px) {
  .brand-stage { padding: 1.5rem 1.35rem 1.25rem; }
  .brand-content > p { font-size: 0.82rem; }
  .auth-stage { padding: 4rem 1.25rem 4rem; }
}

@media (max-width: 480px) {
  .auth-card { padding: 1.35rem; }
  .demo-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .auth-footer span:last-child { display: none; }
}

</style>
