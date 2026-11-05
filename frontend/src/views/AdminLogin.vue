<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, ApiError } from '../api/client'

const router = useRouter()
const username = ref('admin')
const password = ref('Demo@123')
const busy = ref(false)
const error = ref('')

async function login() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    await authApi.login(username.value.trim(), password.value)
    await router.replace('/admin')
  } catch (exception) {
    error.value = exception instanceof ApiError ? exception.message : '登录失败，请稍后重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-visual" aria-hidden="true">
      <div class="grid-plane">
        <span v-for="i in 8" :key="i" :style="{ '--delay': `${i * 90}ms` }" />
      </div>
      <div class="visual-copy">
        <span class="overline">CAMPUS SIGNAL / ADMIN</span>
        <h1>校园内容<br />控制台</h1>
        <p>点位、道路、私有知识库与数字人行为在同一套审核边界内维护。</p>
      </div>
      <div class="coordinate-stamp">
        <span>HBEUTC</span>
        <strong>31.92°N</strong>
        <small>XIAOGAN · HUBEI</small>
      </div>
    </section>

    <section class="login-panel">
      <a class="back-link" href="/">← 返回公众导览</a>
      <div class="login-card">
        <span class="section-label">AUTHORIZED PERSONNEL ONLY</span>
        <h2>管理员登录</h2>
        <p class="intro">演示账号已预填。正式部署前必须更换默认密码，并使用独立的生产 JWT 密钥。</p>
        <form @submit.prevent="login">
          <label>
            <span>账号</span>
            <input v-model="username" autocomplete="username" />
          </label>
          <label>
            <span>密码</span>
            <input v-model="password" type="password" autocomplete="current-password" />
          </label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button type="submit" :disabled="busy || !username || !password">
            {{ busy ? '正在验证…' : '进入内容控制台' }}
          </button>
        </form>
        <div class="demo-accounts">
          <span>DEMO ACCOUNTS</span>
          <p>admin / editor / reviewer / analyst</p>
          <p>统一初始密码：Demo@123</p>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  grid-template-columns: minmax(0, 1.15fr) minmax(24rem, 0.85fr);
  color: var(--ivory);
  background: var(--ink);
}

.login-visual {
  position: relative;
  overflow: hidden;
  border-right: 1px solid rgb(217 181 101 / 20%);
  background:
    radial-gradient(circle at 30% 20%, rgb(72 125 105 / 28%), transparent 34%),
    linear-gradient(135deg, #0c2928, #06191c 62%);
}

.login-visual::after {
  position: absolute;
  inset: 8%;
  border: 1px solid rgb(217 181 101 / 16%);
  content: '';
  transform: rotate(-3deg);
}

.grid-plane {
  position: absolute;
  right: -10%;
  bottom: -30%;
  left: -10%;
  height: 84%;
  transform: perspective(600px) rotateX(62deg);
  transform-origin: center bottom;
}

.grid-plane span:nth-child(1) { top: 0; }
.grid-plane span:nth-child(2) { top: 20%; }
.grid-plane span:nth-child(3) { top: 40%; }
.grid-plane span:nth-child(4) { top: 60%; }

.grid-plane span {
  position: absolute;
  right: 0;
  left: 0;
  border-top: 1px solid rgb(217 181 101 / 20%);
  animation: draw-line 1.1s both cubic-bezier(0.2, 0.7, 0.2, 1);
  animation-delay: var(--delay);
}

.grid-plane span:nth-child(n + 5) {
  top: 0;
  bottom: 0;
  width: 1px;
  border-top: 0;
  border-left: 1px solid rgb(217 181 101 / 18%);
}

.grid-plane span:nth-child(5) { left: 0; }
.grid-plane span:nth-child(6) { left: 20%; }
.grid-plane span:nth-child(7) { left: 40%; }
.grid-plane span:nth-child(8) { left: 60%; }

.visual-copy {
  position: absolute;
  z-index: 2;
  top: 12vh;
  left: 8vw;
}

.overline,
.section-label {
  color: var(--amber);
  font: 600 0.64rem/1.2 var(--font-mono);
  letter-spacing: 0.18em;
}

.visual-copy h1 {
  margin: 1.3rem 0;
  font: 400 clamp(3.4rem, 6.8vw, 7rem)/0.96 var(--font-display);
  letter-spacing: -0.05em;
}

.visual-copy p {
  max-width: 28rem;
  color: rgb(238 233 218 / 56%);
  font-size: 0.88rem;
  line-height: 1.8;
}

.coordinate-stamp {
  position: absolute;
  z-index: 2;
  bottom: 8vh;
  left: 8vw;
  display: grid;
  grid-template-columns: auto auto;
  gap: 0.25rem 1.2rem;
  padding-left: 1rem;
  border-left: 1px solid var(--amber);
}

.coordinate-stamp span,
.coordinate-stamp small {
  color: var(--muted);
  font: 0.58rem/1.4 var(--font-mono);
  letter-spacing: 0.12em;
}

.coordinate-stamp strong {
  grid-row: span 2;
  color: var(--ivory);
  font: 500 1.5rem/1 var(--font-display);
}

.login-panel {
  display: grid;
  place-items: center;
  padding: 4rem;
  background:
    linear-gradient(145deg, rgb(255 255 255 / 2%), transparent),
    var(--ink);
}

.back-link {
  position: absolute;
  top: 2rem;
  right: 2.5rem;
  color: var(--muted);
  font-size: 0.72rem;
  text-decoration: none;
}

.login-card {
  width: min(100%, 25rem);
  padding: 2.2rem 0;
  border-top: 1px solid rgb(217 181 101 / 30%);
  border-bottom: 1px solid rgb(217 181 101 / 16%);
}

.login-card h2 {
  margin: 0.8rem 0 0.5rem;
  font: 500 2.2rem/1.1 var(--font-display);
}

.intro {
  margin: 0 0 1.8rem;
  color: var(--muted);
  font-size: 0.76rem;
  line-height: 1.7;
}

form {
  display: grid;
  gap: 1rem;
}

label span {
  display: block;
  margin-bottom: 0.35rem;
  color: var(--muted);
  font: 0.62rem/1 var(--font-mono);
}

input {
  width: 100%;
  border: 0;
  border-bottom: 1px solid rgb(238 233 218 / 22%);
  outline: 0;
  padding: 0.72rem 0;
  color: var(--ivory);
  background: transparent;
  font: 0.9rem/1.2 var(--font-body);
}

input:focus {
  border-color: var(--amber);
}

form button {
  margin-top: 0.7rem;
  border: 1px solid var(--amber);
  padding: 0.9rem 1rem;
  color: #071d20;
  background: var(--amber);
  font-weight: 700;
  cursor: pointer;
}

.form-error {
  margin: 0;
  color: #f1a58d;
  font-size: 0.72rem;
}

.demo-accounts {
  margin-top: 1.8rem;
  color: var(--muted);
  font: 0.62rem/1.6 var(--font-mono);
}

.demo-accounts span {
  color: var(--amber);
  letter-spacing: 0.12em;
}

.demo-accounts p {
  margin: 0.2rem 0;
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@keyframes draw-line {
  from {
    opacity: 0;
    transform: scale(0.92);
  }
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-visual {
    min-height: 38vh;
  }

  .visual-copy {
    top: 8vh;
    left: 7vw;
  }

  .visual-copy h1 {
    font-size: 3.3rem;
  }

  .coordinate-stamp {
    display: none;
  }

  .login-panel {
    min-height: 62vh;
    padding: 3rem 7vw;
  }

  .back-link {
    top: auto;
    right: 7vw;
    bottom: 1.2rem;
  }
}
</style>
