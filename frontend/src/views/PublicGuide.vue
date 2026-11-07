<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { publicApi, ApiError } from '../api/client'
import AvatarCompanion from '../components/AvatarCompanion.vue'
import CampusScene from '../components/CampusScene.vue'
import ChatPanel, { type ChatMessage } from '../components/ChatPanel.vue'
import AppIcon from '../components/AppIcon.vue'
import type {
  BootstrapResponse,
  CameraView,
  ChatResponse,
  PointView,
  RoutePlan,
  TourView,
} from '../types'

interface SceneHandle {
  focusPoint: (pointId: number) => void
  setCamera: (camera: CameraView) => void
  resetCamera: () => void
  showRoute: (route: RoutePlan) => void
  clearRoute: () => void
}

interface AvatarHandle {
  playGesture: (name: string, durationMs?: number) => void
}

interface ChatPanelHandle {
  focusInput: () => void
}

const scene = ref<SceneHandle>()
const avatar = ref<AvatarHandle>()
const chatPanel = ref<ChatPanelHandle>()
const data = ref<BootstrapResponse>()
const loadError = ref('')
const sceneReady = ref(false)
const loading = computed(() => (!data.value && !loadError.value) || (!!data.value && !sceneReady.value))
const busy = ref(false)
const chatOpen = ref(false)
const sessionId = ref<string>(crypto.randomUUID())
const selectedPoint = ref<PointView>()
const activeTour = ref<TourView>()
const activeStop = ref(0)
const tourPaused = ref(false)
const showCameras = ref(false)
const showPoints = ref(true)
// Short laptop viewports start with the route rail collapsed so the 3D scene,
// the route tab and the digital-human companion all stay usable.
const showTours = ref(!window.matchMedia('(max-width: 760px)').matches && window.innerHeight > 980)
const latestChatId = ref<number>()
const messages = ref<ChatMessage[]>([])
let resumeTourAfterAnswer = false
let tourTimer: number | undefined

const CATEGORY_LABELS: Record<string, string> = {
  GATE: '校门',
  LIBRARY: '图书馆',
  TEACHING: '教学楼',
  LAB: '实验楼',
  DORM: '宿舍',
  CANTEEN: '食堂',
  SPORTS: '运动场',
  ADMIN: '行政楼',
  SERVICE: '服务设施',
  PLAZA: '广场',
  OTHER: '其他',
}

function categoryLabel(category: string) {
  return CATEGORY_LABELS[category] ?? category
}

const modeLabel = computed(() => {
  const mode = data.value?.config.mode
  if (mode === 'DEMO') return '演示数据'
  if (mode === 'LIVE') return '实时数据'
  return mode ? `${mode} 数据` : '数据连接中'
})

const suggestions = [
  '学校是什么类型的学校？',
  '学校的地址和联系电话？',
  '学校有哪些专业？',
  '从校门去图书馆怎么走？',
]

const defaultMessage = computed(() => ({
  id: 'welcome',
  role: 'guide' as const,
  text:
    '你好，我是校园导览员。已为你连接校园知识库，可以问我学校概况、专业设置、地址电话或校内路线。回答会标注资料来源；无法核验的信息，我会明确说明。',
  status: data.value?.config.mode ?? 'DEMO',
}))

onMounted(async () => {
  try {
    data.value = await publicApi.bootstrap()
    messages.value = [defaultMessage.value]
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '校园场景加载失败'
  }
})

function onSceneModelReady() {
  sceneReady.value = true
}

function onSceneReady() {
  nextTick(() => scene.value?.resetCamera())
}

async function openChat() {
  chatOpen.value = true
  // Compact screens cannot host the tall route rail and the chat console at once.
  if (isCompactLayout()) showTours.value = false
  await nextTick()
  chatPanel.value?.focusInput()
}

async function sendMessage(raw: string) {
  const text = raw.trim()
  if (!text || busy.value || !data.value) return
  if (activeTour.value && !tourPaused.value) {
    resumeTourAfterAnswer = true
    pauseTour()
  }
  messages.value.push({ id: crypto.randomUUID(), role: 'visitor', text })
  busy.value = true
  try {
    const response = await publicApi.chat({
      sessionId: sessionId.value,
      message: text,
      currentPointId: selectedPoint.value?.id,
      inputType: 'TEXT',
    })
    await handleResponse(response)
  } catch (error) {
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'guide',
      text:
        error instanceof ApiError
          ? `${error.message}${error.status === 401 ? '（该接口应使用公众入口）' : ''}`
          : '服务暂时不可用，请稍后重试。',
      status: 'ERROR',
    })
  } finally {
    busy.value = false
  }
}

async function handleResponse(response: ChatResponse) {
  sessionId.value = response.sessionId
  latestChatId.value = response.logId
  selectedPoint.value = data.value?.points.find((point) => point.id === response.command.pointId)
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'guide',
    text: response.answer,
    media: response.media,
    status: response.answerStatus,
    sources: response.sources,
  })
  await applyCommand(response)
  if (resumeTourAfterAnswer && activeTour.value && tourPaused.value) {
    resumeTourAfterAnswer = false
    resumeTour()
  }
}

async function applyCommand(response: ChatResponse) {
  const command = response.command
  if (!command) return
  if (command.type === 'FOCUS_POINT' && command.pointId) {
    scene.value?.focusPoint(command.pointId)
    avatar.value?.playGesture(response.intent === 'BUILDING_INTRO' ? 'point' : 'wave')
  } else if (command.type === 'SHOW_ROUTE' && command.route) {
    scene.value?.showRoute(command.route)
    avatar.value?.playGesture('point', 2200)
  } else if (command.type === 'RESET_VIEW') {
    scene.value?.resetCamera()
  } else if (command.type === 'CLEAR_ROUTE') {
    scene.value?.clearRoute()
  } else if (command.type === 'OPEN_CAMERAS') {
    showPoints.value = true
    showCameras.value = true
  } else if (command.type === 'PAUSE_TOUR') {
    pauseTour()
  } else if (command.type === 'RESUME_TOUR') {
    resumeTour()
  } else if (command.type === 'START_TOUR') {
    const tour = data.value?.tours.find((item) => item.key === command.action)
    if (tour) startTour(tour)
  }
}

function selectPoint(point: PointView) {
  selectedPoint.value = point
  scene.value?.focusPoint(point.id)
  avatar.value?.playGesture('point')
  void sendMessage(`介绍一下${point.name}`)
}

function switchCamera(camera: CameraView) {
  scene.value?.setCamera(camera)
  showCameras.value = false
}

function isCompactLayout() {
  return window.matchMedia('(max-width: 760px)').matches
}

function togglePoints() {
  showPoints.value = !showPoints.value
  if (showPoints.value && isCompactLayout()) {
    showTours.value = false
  }
}

function toggleTours() {
  showTours.value = !showTours.value
  if (showTours.value && isCompactLayout()) {
    showPoints.value = false
    chatOpen.value = false
  }
}

function startTour(tour: TourView) {
  clearTourTimer()
  activeTour.value = tour
  activeStop.value = 0
  tourPaused.value = false
  void playTourStop()
}

function playTourStop() {
  const tour = activeTour.value
  if (!tour || tourPaused.value) return
  const stop = tour.stops[activeStop.value]
  if (!stop) {
    endTour()
    return
  }
  selectedPoint.value = data.value?.points.find((point) => point.id === stop.pointId)
  scene.value?.focusPoint(stop.pointId)
  avatar.value?.playGesture('point', 2200)
  messages.value.push({
    id: crypto.randomUUID(),
    role: 'guide',
    text: stop.narration,
    status: `${tour.name} · ${activeStop.value + 1}/${tour.stops.length}`,
  })
  clearTourTimer()
  tourTimer = window.setTimeout(
    () => {
      if (!tourPaused.value && activeTour.value) {
        activeStop.value += 1
        playTourStop()
      }
    },
    Math.min(7000, Math.max(2800, stop.narration.length * 70)),
  )
}

function pauseTour() {
  if (!activeTour.value) return
  tourPaused.value = true
  clearTourTimer()
}

function resumeTour() {
  if (!activeTour.value) return
  tourPaused.value = false
  void playTourStop()
}

function nextTourStop() {
  if (!activeTour.value) return
  clearTourTimer()
  tourPaused.value = false
  activeStop.value = Math.min(activeTour.value.stops.length - 1, activeStop.value + 1)
  void playTourStop()
}

function previousTourStop() {
  if (!activeTour.value) return
  clearTourTimer()
  tourPaused.value = false
  activeStop.value = Math.max(0, activeStop.value - 1)
  void playTourStop()
}

function endTour() {
  clearTourTimer()
  activeTour.value = undefined
  activeStop.value = 0
  tourPaused.value = false
}

function clearTourTimer() {
  window.clearTimeout(tourTimer)
  tourTimer = undefined
}

async function submitFeedback(feedback: 'HELPFUL' | 'UNHELPFUL' | 'OUTDATED') {
  if (!latestChatId.value) return
  try {
    await publicApi.feedback(latestChatId.value, feedback, sessionId.value)
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'guide',
      text: '已记录反馈，后台会将其纳入待处理清单。',
      status: 'FEEDBACK',
    })
  } catch {
    // Feedback is non-blocking for the main guide flow.
  }
}
</script>

<template>
  <main class="guide-shell" :class="{ 'tours-open': showTours }">
    <CampusScene
      v-if="data"
      ref="scene"
      :points="data.points"
      :buildings="data.buildings"
      :road-nodes="data.roadNodes"
      :road-edges="data.roadEdges"
      :cameras="data.cameras"
      :config="data.config"
      @point-selected="selectPoint"
      @model-ready="onSceneModelReady"
      @ready="onSceneReady"
    />

    <div v-if="loading" class="loading-veil">
      <div class="loader-mark">
        <span />
        <span />
      </div>
      <p>正在展开校园测绘场景</p>
      <small>LOADING CAMPUS FIELD / THREE.JS</small>
    </div>

    <div v-if="loadError" class="load-error">
      <span>场景暂不可用</span>
      <h1>校园导览服务未连接</h1>
      <p>{{ loadError }}</p>
      <p>请确认后端已在 8080 端口启动。</p>
    </div>

    <template v-if="data">
      <header class="top-rail">
        <a class="brand" href="/" title="新技术学院校园数字导览 · 返回首页">
          <span class="brand-mark" aria-hidden="true"><AppIcon name="cube" :size="18" /></span>
          <span class="brand-copy"><strong>新技术学院</strong><small>校园数字导览</small></span>
        </a>
        <div class="motto">
          <span>明德明志</span>
          <i />
          <span>求是求新</span>
        </div>
        <div class="top-status">
          <span class="status-dot" />
          <span>{{ modeLabel }}</span>
          <a class="admin-entry" href="/admin/login" aria-label="进入后台管理端">
            <span>管理端</span>
            <AppIcon name="arrow" :size="15" />
          </a>
        </div>
      </header>

      <aside
        id="points-panel"
        class="location-rail"
        :class="{ collapsed: !showPoints, 'chat-open': chatOpen }"
        aria-label="校园点位索引"
      >
        <div class="rail-heading">
          <span class="section-kicker">FIELD INDEX</span>
          <h1>校园点位</h1>
          <small>{{ data.points.length }} 个公开点位</small>
          <button
            class="collapse-button rail-collapse"
            type="button"
            :aria-expanded="showPoints"
            aria-controls="points-panel"
            :aria-label="showPoints ? '收起点位栏' : '展开点位栏'"
            :title="showPoints ? '收起点位栏' : '展开点位栏'"
            @click="togglePoints"
          >
            <AppIcon :name="showPoints ? 'chevronUp' : 'chevronDown'" :size="16" />
          </button>
        </div>
        <div v-if="showPoints" class="rail-body">
          <ol>
            <li v-for="(point, index) in data.points" :key="point.id">
              <button
                type="button"
                :class="{ active: selectedPoint?.id === point.id }"
                @click="selectPoint(point)"
              >
                <span class="point-number">{{ String(index + 1).padStart(2, '0') }}</span>
                <span class="point-name">{{ point.name }}</span>
                <span class="point-type">{{ categoryLabel(point.category) }}</span>
              </button>
            </li>
          </ol>
          <button class="camera-toggle" type="button" :aria-expanded="showCameras" @click="showCameras = !showCameras">
            <span>{{ showCameras ? '收起机位' : '切换预置机位' }}</span>
            <AppIcon :name="showCameras ? 'chevronUp' : 'chevronDown'" :size="16" />
          </button>
        </div>
      </aside>

      <div v-if="showCameras" class="camera-popover">
        <header>
          <span class="section-kicker">CAMERA PRESETS</span>
          <button type="button" aria-label="关闭机位列表" @click="showCameras = false"><AppIcon name="close" :size="16" /></button>
        </header>
        <button
          v-for="camera in data.cameras"
          :key="camera.id"
          type="button"
          @click="switchCamera(camera)"
        >
          <span>{{ camera.name }}</span>
          <small>{{ camera.key }}</small>
        </button>
        <button type="button" @click="scene?.resetCamera(); showCameras = false">
          <span>恢复默认视角</span>
          <small>RESET</small>
        </button>
      </div>

      <aside
        id="tours-panel"
        class="tour-rail"
        :class="{ collapsed: !showTours }"
        aria-label="精品游览路线"
      >
        <header>
          <div>
            <span class="section-kicker">CURATED JOURNEYS</span>
            <h2>精品路线</h2>
          </div>
          <button
            type="button"
            class="collapse-button"
            :aria-expanded="showTours"
            aria-controls="tours-panel"
            :aria-label="showTours ? '收起路线栏' : '展开路线栏'"
            :title="showTours ? '收起路线栏' : '展开路线栏'"
            @click="toggleTours"
          >
            <AppIcon :name="showTours ? 'chevronUp' : 'chevronDown'" :size="16" />
          </button>
        </header>
        <div v-if="showTours" class="tour-list">
          <article v-for="tour in data.tours" :key="tour.id">
            <button type="button" @click="startTour(tour)">
              <span>{{ tour.estimatedMinutes }} MIN</span>
              <h3>{{ tour.name }}</h3>
              <p>{{ tour.summary }}</p>
              <small>{{ tour.stops.length }} 站 · {{ tour.audience }}</small>
            </button>
          </article>
        </div>
        <div v-if="activeTour" class="tour-controls">
          <div class="tour-progress">
            <span :style="{ width: `${((activeStop + 1) / activeTour.stops.length) * 100}%` }" />
          </div>
          <p>{{ activeStop + 1 }} / {{ activeTour.stops.length }} · {{ activeTour.stops[activeStop]?.pointName }}</p>
          <div>
            <button type="button" @click="previousTourStop">上一站</button>
            <button type="button" @click="tourPaused ? resumeTour() : pauseTour()">
              {{ tourPaused ? '继续' : '暂停' }}
            </button>
            <button type="button" @click="nextTourStop">下一站</button>
            <button type="button" @click="endTour">退出</button>
          </div>
        </div>
      </aside>

      <div class="notice-line">
        <span>DATA NOTE</span>
        <p>{{ data.config.notice }}</p>
      </div>

      <div class="feedback-actions">
        <span>刚才的回答</span>
        <button type="button" @click="submitFeedback('HELPFUL')">有帮助</button>
        <button type="button" @click="submitFeedback('UNHELPFUL')">没帮助</button>
        <button type="button" @click="submitFeedback('OUTDATED')">信息过期</button>
      </div>

      <AvatarCompanion
        ref="avatar"
        :active="chatOpen"
        @activate="openChat"
      />

      <Transition name="chat-drawer">
        <ChatPanel
          v-if="chatOpen"
          ref="chatPanel"
          class="guide-chat"
          :messages="messages"
          :suggestions="suggestions"
          :busy="busy"
          @send="sendMessage"
          @close="chatOpen = false"
        />
      </Transition>
    </template>
  </main>
</template>

<style scoped>
.guide-shell {
  position: fixed;
  inset: 0;
  overflow: hidden;
  color: var(--ivory);
  background: #040d29;
}

.top-rail {
  position: absolute;
  z-index: 20;
  top: 0;
  right: 0;
  left: 0;
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 1rem 1.25rem 0.9rem;
  background: linear-gradient(180deg, rgb(4 13 41 / 86%), transparent);
  pointer-events: none;
}

.brand,
.admin-entry {
  pointer-events: auto;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
  text-decoration: none;
}

.brand-mark {
  color: var(--amber);
  font: 600 0.7rem/1 var(--font-mono);
  letter-spacing: 0.18em;
}

.brand-title {
  color: var(--ivory);
  font: 600 1rem/1.2 var(--font-display);
  letter-spacing: 0.04em;
}

.motto {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.7rem;
  color: rgb(202 225 245 / 62%);
  font: 0.68rem/1 var(--font-display);
  letter-spacing: 0.2em;
}

.motto i {
  width: 1.6rem;
  height: 1px;
  background: rgb(0 220 246 / 38%);
}

.top-status {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--muted);
  font: 0.63rem/1 var(--font-mono);
}

.admin-entry {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  margin-left: 0.6rem;
  border: 1px solid rgb(0 220 246 / 55%);
  padding: 0.44rem 0.66rem;
  color: #d6f4ff;
  background:
    linear-gradient(135deg, rgb(0 220 246 / 14%), rgb(7 26 59 / 82%));
  box-shadow: 0 8px 24px rgb(0 10 12 / 28%);
  text-decoration: none;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;
}

.admin-entry span {
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.admin-entry b {
  color: var(--amber);
  font-size: 0.78rem;
  font-weight: 500;
}

.admin-entry:hover {
  border-color: var(--amber);
  background-color: rgb(0 220 246 / 12%);
  transform: translateY(-1px);
}

.status-dot {
  width: 0.43rem;
  height: 0.43rem;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 0 0.25rem rgb(98 181 141 / 12%);
}

.location-rail {
  position: absolute;
  z-index: 15;
  top: 6.2rem;
  bottom: 1.2rem;
  left: 1.1rem;
  width: 16.2rem;
  overflow: auto;
  border-left: 1px solid rgb(0 220 246 / 28%);
  background: linear-gradient(90deg, rgb(4 13 41 / 86%), rgb(4 13 41 / 34%) 82%, transparent);
  scrollbar-width: thin;
  scrollbar-color: rgb(0 220 246 / 42%) transparent;
  transition:
    width 220ms cubic-bezier(0.22, 1, 0.36, 1),
    bottom 220ms ease;
}

.location-rail.chat-open:not(.collapsed) {
  bottom: calc(min(27rem, 48vh) + 1.2rem);
}

.location-rail::-webkit-scrollbar,
.tour-rail::-webkit-scrollbar {
  width: 4px;
}

.location-rail::-webkit-scrollbar-thumb,
.tour-rail::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgb(0 220 246 / 42%);
}

.rail-heading {
  position: relative;
  padding: 0.3rem 2.4rem 0.55rem 0.9rem;
}

.rail-body {
  min-height: 0;
}

.section-kicker {
  color: var(--amber);
  font: 600 0.6rem/1.2 var(--font-mono);
  letter-spacing: 0.15em;
}

.rail-heading h1,
.tour-rail h2 {
  margin: 0.38rem 0 0.2rem;
  color: var(--ivory);
  font: 600 1.35rem/1.15 var(--font-display);
}

.rail-heading small,
.tour-rail small {
  color: var(--muted);
  font: 0.64rem/1.5 var(--font-mono);
}

.location-rail ol {
  margin: 0;
  padding: 0 0.8rem;
  list-style: none;
}

.location-rail li + li {
  border-top: 1px solid rgb(120 170 215 / 10%);
}

.location-rail li button {
  display: grid;
  width: 100%;
  grid-template-columns: 2rem 1fr auto;
  gap: 0.55rem;
  align-items: center;
  border: 0;
  padding: 0.28rem 0.2rem;
  color: var(--ivory);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.point-number {
  color: rgb(0 220 246 / 55%);
  font: 0.63rem/1 var(--font-mono);
}

.point-name {
  overflow: hidden;
  font: 0.78rem/1.25 var(--font-body);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-type {
  color: rgb(150 185 215 / 40%);
  font: 0.52rem/1 var(--font-mono);
}

.location-rail li button:hover,
.location-rail li button.active {
  color: var(--amber);
  background: linear-gradient(90deg, rgb(0 220 246 / 10%), transparent);
}

.camera-toggle {
  width: calc(100% - 2rem);
  margin: 0.55rem 1rem;
  border: 1px solid rgb(120 170 215 / 18%);
  padding: 0.55rem 0.8rem;
  color: #dceeff;
  background: rgb(5 20 54 / 70%);
  text-align: left;
  cursor: pointer;
}

.camera-toggle span {
  float: right;
  color: var(--amber);
}

.camera-popover {
  position: absolute;
  z-index: 26;
  top: 9rem;
  left: 18rem;
  width: 15rem;
  border: 1px solid rgb(120 170 215 / 20%);
  padding: 0.8rem;
  background: rgb(4 13 41 / 95%);
  box-shadow: 0 24px 60px rgb(0 0 0 / 38%);
  backdrop-filter: blur(18px);
}

.camera-popover header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.camera-popover header button,
.collapse-button {
  border: 0;
  color: var(--muted);
  background: transparent;
  font-size: 1.1rem;
  cursor: pointer;
}

.collapse-button,
.rail-collapse {
  transition:
    color 160ms ease,
    border-color 160ms ease,
    background-color 160ms ease;
}

.collapse-button:hover,
.rail-collapse:hover {
  color: var(--ivory);
  border-color: rgb(0 220 246 / 45%);
  background: rgb(0 220 246 / 8%);
}

.rail-collapse {
  position: absolute;
  top: 0.18rem;
  right: 0.35rem;
  display: grid;
  width: 1.7rem;
  height: 1.7rem;
  place-items: center;
  border: 1px solid rgb(120 170 215 / 16%);
  line-height: 1;
}

.camera-popover > button {
  display: flex;
  width: 100%;
  justify-content: space-between;
  border: 0;
  border-top: 1px solid rgb(120 170 215 / 10%);
  padding: 0.65rem 0;
  color: var(--ivory);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.camera-popover small {
  color: var(--amber);
  font: 0.56rem/1 var(--font-mono);
}

.tour-rail {
  position: absolute;
  z-index: 16;
  top: 6.2rem;
  right: 1.1rem;
  width: 17.5rem;
  max-height: calc(100vh - 13.5rem);
  overflow: auto;
  border-right: 1px solid rgb(0 220 246 / 28%);
  padding: 0.4rem 0 0.4rem 0.8rem;
  background: linear-gradient(270deg, rgb(4 13 41 / 86%), rgb(4 13 41 / 24%) 82%, transparent);
  scrollbar-width: thin;
  scrollbar-color: rgb(0 220 246 / 42%) transparent;
  transition: width 220ms cubic-bezier(0.22, 1, 0.36, 1);
}

.location-rail.collapsed,
.tour-rail.collapsed {
  width: 3.05rem;
  max-height: none;
  overflow: hidden;
  border: 1px solid rgb(0 220 246 / 26%);
  padding: 0.35rem;
  background: rgb(5 20 54 / 92%);
  box-shadow: 0 16px 40px rgb(0 0 0 / 26%);
  backdrop-filter: blur(14px);
}

.location-rail.collapsed {
  bottom: auto;
}

.location-rail.collapsed > .rail-heading,
.tour-rail.collapsed > header {
  display: grid;
  justify-content: center;
  padding: 0;
  place-items: center;
}

.location-rail.collapsed > .rail-heading > :not(.rail-collapse),
.tour-rail.collapsed > header > div {
  display: none;
}

.location-rail.collapsed .rail-collapse,
.tour-rail.collapsed .collapse-button {
  position: static;
  width: 100%;
  height: 2rem;
  border: 1px solid transparent;
  font-size: 1.3rem;
}

.tour-rail > header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 0 0.75rem 0.7rem;
}

.tour-list article {
  border-top: 1px solid rgb(120 170 215 / 10%);
}

.tour-list article > button {
  width: 100%;
  border: 0;
  padding: 0.8rem 0.75rem;
  color: var(--ivory);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.tour-list article > button:hover {
  background: rgb(0 220 246 / 8%);
}

.tour-list article span {
  color: var(--amber);
  font: 0.55rem/1 var(--font-mono);
  letter-spacing: 0.1em;
}

.tour-list h3 {
  margin: 0.4rem 0;
  font: 600 0.92rem/1.3 var(--font-display);
}

.tour-list p {
  margin: 0 0 0.45rem;
  color: rgb(160 195 225 / 66%);
  font-size: 0.69rem;
  line-height: 1.55;
}

.tour-controls {
  margin-top: 0.5rem;
  border-top: 1px solid rgb(0 220 246 / 22%);
  padding: 0.8rem 0.75rem;
  background: rgb(5 20 54 / 76%);
}

.tour-progress {
  height: 2px;
  background: rgb(255 255 255 / 8%);
}

.tour-progress span {
  display: block;
  height: 100%;
  background: var(--amber);
  transition: width 300ms ease;
}

.tour-controls p {
  margin: 0.55rem 0;
  color: var(--muted);
  font: 0.62rem/1.3 var(--font-mono);
}

.tour-controls > div:last-child {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
}

.tour-controls button {
  border: 1px solid rgb(120 170 215 / 16%);
  padding: 0.32rem 0.5rem;
  color: var(--ivory);
  background: transparent;
  font-size: 0.62rem;
  cursor: pointer;
}

.notice-line {
  position: absolute;
  z-index: 14;
  right: 19rem;
  bottom: 1rem;
  left: 18rem;
  display: flex;
  justify-content: center;
  gap: 0.7rem;
  pointer-events: none;
}

.notice-line span {
  color: var(--amber);
  font: 0.55rem/1.5 var(--font-mono);
}

.notice-line p {
  max-width: 42rem;
  margin: 0;
  color: rgb(160 195 225 / 56%);
  font-size: 0.64rem;
}

.feedback-actions {
  position: absolute;
  z-index: 21;
  right: 19rem;
  bottom: 3.2rem;
  display: flex;
  gap: 0.35rem;
  align-items: center;
}

.feedback-actions span {
  color: rgb(160 195 225 / 48%);
  font: 0.55rem/1 var(--font-mono);
}

.feedback-actions button {
  border: 0;
  border-bottom: 1px solid rgb(120 170 215 / 22%);
  padding: 0.15rem 0.2rem;
  color: var(--muted);
  background: transparent;
  font-size: 0.6rem;
  cursor: pointer;
}

.guide-chat {
  position: absolute;
  z-index: 24;
  bottom: 1rem;
  left: 1.1rem;
  width: min(31rem, calc(100vw - 2.2rem));
  height: min(27rem, 48vh);
}

.chat-drawer-enter-active,
.chat-drawer-leave-active {
  transition:
    opacity 220ms ease,
    transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}

.chat-drawer-enter-from,
.chat-drawer-leave-to {
  opacity: 0;
  transform: translateY(1.2rem);
}

.loading-veil,
.load-error {
  position: absolute;
  inset: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #040d29;
}

.loading-veil p {
  margin: 1.4rem 0 0.35rem;
  font: 500 1rem/1.3 var(--font-display);
}

.loading-veil small {
  color: var(--muted);
  font: 0.58rem/1 var(--font-mono);
  letter-spacing: 0.16em;
}

.loader-mark {
  position: relative;
  width: 4.5rem;
  height: 4.5rem;
  border: 1px solid rgb(0 220 246 / 38%);
  border-radius: 50%;
  animation: rotate 3.8s linear infinite;
}

.loader-mark::after {
  position: absolute;
  inset: 0.75rem;
  border: 1px solid rgb(0 220 246 / 68%);
  content: '';
}

.loader-mark span {
  position: absolute;
  top: 50%;
  left: -0.35rem;
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 50%;
  background: var(--amber);
}

.loader-mark span:last-child {
  right: -0.35rem;
  left: auto;
}

.load-error {
  align-items: flex-start;
  padding: 14vw;
}

.load-error span {
  color: var(--amber);
  font: 0.65rem/1 var(--font-mono);
}

.load-error h1 {
  margin: 1rem 0;
  font: 500 clamp(2rem, 5vw, 4rem)/1.05 var(--font-display);
}

.load-error p {
  max-width: 34rem;
  color: var(--muted);
}

@keyframes rotate {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1080px) {
  .location-rail {
    width: 13rem;
  }

  .location-rail.chat-open:not(.collapsed) {
    bottom: calc(min(26rem, 48vh) + 1.2rem);
  }

  .tour-rail {
    width: 14.5rem;
  }

  .location-rail.collapsed,
  .tour-rail.collapsed {
    width: 3.05rem;
  }

  .guide-chat {
    width: min(29rem, calc(100vw - 2rem));
  }

  .notice-line,
  .feedback-actions {
    display: none;
  }
}

@media (max-width: 760px) {
  .top-rail {
    grid-template-columns: 1fr auto;
    padding: 0.8rem;
  }

  .motto {
    display: none;
  }

  .brand-title {
    font-size: 0.82rem;
  }

  .top-status {
    font-size: 0.55rem;
  }

  .top-status > span:not(.status-dot) {
    display: none;
  }

  .admin-entry {
    margin-left: 0;
    padding: 0.38rem 0.5rem;
  }

  .location-rail {
    top: 4.6rem;
    bottom: auto;
    left: 0.5rem;
    width: calc(100vw - 5.5rem);
    max-height: none;
    overflow: visible;
    border-left: 0;
    border-top: 1px solid rgb(0 220 246 / 28%);
    padding-bottom: 0.35rem;
    background: rgb(4 13 41 / 86%);
    backdrop-filter: blur(12px);
  }

  .location-rail.chat-open {
    bottom: auto;
  }

  .location-rail.chat-open:not(.collapsed) {
    bottom: auto;
  }

  .location-rail.collapsed {
    width: 3.05rem;
    padding: 0.35rem;
    border: 1px solid rgb(0 220 246 / 26%);
  }

  .rail-heading {
    display: flex;
    align-items: baseline;
    gap: 0.55rem;
    padding: 0.55rem 0.7rem 0.35rem;
  }

  .rail-heading .section-kicker {
    display: none;
  }

  .rail-heading h1 {
    margin: 0;
    font-size: 0.95rem;
  }

  .rail-heading small {
    font-size: 0.54rem;
  }

  .location-rail ol {
    display: flex;
    gap: 0.32rem;
    overflow-x: auto;
    padding: 0 0.55rem 0.45rem;
    scroll-snap-type: x proximity;
  }

  .location-rail li {
    flex: 0 0 8.6rem;
    border: 1px solid rgb(120 170 215 / 12%);
    background: rgb(255 255 255 / 2%);
    scroll-snap-align: start;
  }

  .location-rail li + li {
    border-top: 1px solid rgb(120 170 215 / 12%);
  }

  .location-rail li button {
    grid-template-columns: 1.35rem 1fr;
    min-height: 2.45rem;
    padding: 0.42rem 0.5rem;
  }

  .point-type {
    display: none;
  }

  .camera-toggle {
    width: calc(100% - 1rem);
    margin: 0.15rem 0.5rem 0.35rem;
    padding: 0.42rem 0.6rem;
    font-size: 0.62rem;
  }

  .tour-rail {
    top: 4.6rem;
    right: 0.5rem;
    width: 12.2rem;
    max-height: 40vh;
    background: rgb(4 13 41 / 86%);
    backdrop-filter: blur(12px);
  }

  .tour-rail h2,
  .tour-rail .section-kicker {
    font-size: 0.7rem;
  }

  .tour-list p,
  .tour-list small {
    display: none;
  }

  .camera-popover {
    top: 8rem;
    left: 0.5rem;
  }

  .guide-chat {
    right: 0.5rem;
    bottom: 0.5rem;
    left: 0.5rem;
    width: auto;
    height: min(26rem, 48vh);
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}

/* Public experience refinement: calm glass surfaces, clear hierarchy */
.top-rail {
  padding: 1rem 1.35rem 1.1rem;
  background: linear-gradient(180deg, rgb(2 8 20 / 72%), rgb(2 8 20 / 10%) 72%, transparent);
}

.brand {
  align-items: center;
  gap: 0.72rem;
}

.brand-mark {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  place-items: center;
  border: 1px solid rgb(53 215 255 / 38%);
  border-radius: 0.78rem;
  color: #07132a;
  background: linear-gradient(145deg, #8bf4ff, #35d7ff 48%, #5c7cfa);
  box-shadow: 0 10px 26px rgb(53 215 255 / 18%);
}

.brand-copy {
  display: grid;
  gap: 0.08rem;
}

.brand-copy strong {
  color: #f4f9ff;
  font: 650 0.95rem/1.1 var(--font-display);
  letter-spacing: 0.02em;
}

.brand-copy small {
  color: #8ca2c0;
  font-size: 0.66rem;
  letter-spacing: 0.06em;
}

.motto {
  color: rgb(198 218 242 / 64%);
  font-size: 0.74rem;
  letter-spacing: 0.18em;
}

.top-status {
  gap: 0.65rem;
  color: #9aafca;
  font-size: 0.72rem;
}

.admin-entry {
  min-height: 2.65rem;
  gap: 0.48rem;
  margin-left: 0.55rem;
  border-color: rgb(53 215 255 / 42%);
  border-radius: 0.78rem;
  padding: 0.52rem 0.78rem;
  color: #e5fbff;
  background: linear-gradient(135deg, rgb(53 215 255 / 12%), rgb(7 26 59 / 82%));
  box-shadow: 0 10px 26px rgb(0 6 18 / 26%);
}

.admin-entry span {
  font-size: 0.78rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.admin-entry:hover {
  border-color: rgb(53 215 255 / 76%);
  background-color: rgb(53 215 255 / 12%);
}

.location-rail,
.tour-rail {
  border: 1px solid rgb(138 169 214 / 16%);
  border-radius: 1.15rem;
  background: linear-gradient(155deg, rgb(7 25 51 / 90%), rgb(2 11 25 / 91%));
  box-shadow: 0 24px 64px rgb(0 4 16 / 28%), inset 0 1px 0 rgb(255 255 255 / 3%);
  backdrop-filter: blur(22px);
}

.location-rail {
  top: 6.15rem;
  bottom: 1.4rem;
  left: 1.25rem;
  width: 17.2rem;
  padding: 0.2rem 0;
}

.location-rail.chat-open:not(.collapsed) {
  bottom: calc(min(31rem, 56vh) + 1.35rem);
}

.rail-heading {
  padding: 1rem 3rem 0.72rem 1rem;
}

.section-kicker {
  color: #62e4f7;
  font-size: 0.65rem;
  letter-spacing: 0.13em;
}

.rail-heading h1,
.tour-rail h2 {
  margin-top: 0.42rem;
  font-size: 1.45rem;
  letter-spacing: -0.02em;
}

.rail-heading small,
.tour-rail small {
  color: #849ab8;
  font-size: 0.7rem;
}

.location-rail ol {
  padding: 0 0.7rem 0.65rem;
}

.location-rail li + li {
  border-top-color: transparent;
}

.location-rail li button {
  min-height: 2.95rem;
  grid-template-columns: 2.15rem minmax(0, 1fr) auto;
  gap: 0.58rem;
  border-radius: 0.68rem;
  padding: 0.42rem 0.55rem;
  transition: color 170ms var(--ease-out), background 170ms var(--ease-out), transform 170ms var(--ease-out);
}

.location-rail li button:hover,
.location-rail li button.active {
  color: #f0fdff;
  background: linear-gradient(90deg, rgb(53 215 255 / 13%), rgb(92 124 250 / 5%));
  box-shadow: inset 3px 0 0 rgb(53 215 255 / 85%);
  transform: translateX(2px);
}

.point-number {
  color: #6ce8f8;
  font-size: 0.68rem;
}

.point-name {
  font-size: 0.87rem;
  font-weight: 500;
}

.point-type {
  color: #8fa5c2;
  font-size: 0.7rem;
}

.camera-toggle {
  display: flex;
  width: calc(100% - 1.4rem);
  min-height: 2.8rem;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  margin: 0.55rem 0.7rem 0.7rem;
  border-color: rgb(138 169 214 / 16%);
  border-radius: 0.7rem;
  padding: 0.58rem 0.75rem;
  color: #c7d7ec;
  background: rgb(53 215 255 / 5%);
  font-size: 0.76rem;
}

.camera-toggle:hover {
  border-color: rgb(53 215 255 / 30%);
  color: #effcff;
  background: rgb(53 215 255 / 9%);
}

.camera-toggle > .app-icon { color: #71e8f8; }

.rail-collapse {
  top: 0.85rem;
  right: 0.75rem;
  width: 2.75rem;
  height: 2.75rem;
  border-color: rgb(138 169 214 / 16%);
  border-radius: 0.65rem;
}

.camera-popover {
  top: 8.6rem;
  left: 19.2rem;
  width: 16.5rem;
  border-color: rgb(138 169 214 / 18%);
  border-radius: 1rem;
  padding: 0.9rem;
  background: rgb(4 15 34 / 96%);
  box-shadow: 0 28px 70px rgb(0 4 16 / 42%), inset 0 1px 0 rgb(255 255 255 / 3%);
}

.camera-popover header button {
  display: grid;
  width: 2.2rem;
  height: 2.2rem;
  place-items: center;
  border: 1px solid rgb(138 169 214 / 14%);
  border-radius: 0.55rem;
}

.camera-popover > button {
  min-height: 2.8rem;
  align-items: center;
  border-top-color: rgb(138 169 214 / 11%);
  font-size: 0.78rem;
}

.camera-popover small {
  color: #66e3f5;
  font-size: 0.62rem;
}

.tour-rail {
  top: 6.15rem;
  right: 1.25rem;
  width: 18rem;
  max-height: calc(100vh - 13rem);
  padding: 0.15rem 0;
}

.tour-rail > header {
  padding: 0.85rem 0.9rem 0.7rem 1rem;
}

.collapse-button {
  display: grid;
  width: 2.75rem;
  height: 2.75rem;
  place-items: center;
  border: 1px solid rgb(138 169 214 / 14%);
  border-radius: 0.65rem;
}

.tour-list article {
  border-top-color: rgb(138 169 214 / 10%);
}

.tour-list article > button {
  min-height: 6.6rem;
  padding: 0.95rem 0.9rem 0.95rem 1rem;
  border-radius: 0.6rem;
}

.tour-list article > button:hover {
  background: linear-gradient(90deg, rgb(53 215 255 / 9%), rgb(92 124 250 / 4%));
}

.tour-list article span {
  color: #68e4f6;
  font-size: 0.63rem;
}

.tour-list h3 {
  margin: 0.42rem 0;
  font-size: 1rem;
}

.tour-list p {
  color: #9bafc9;
  font-size: 0.77rem;
  line-height: 1.6;
}

.tour-controls {
  margin: 0.55rem 0.7rem 0.75rem;
  border: 1px solid rgb(138 169 214 / 13%);
  border-radius: 0.75rem;
  padding: 0.8rem;
  background: rgb(53 215 255 / 5%);
}

.tour-controls p {
  color: #8da5c2;
  font-size: 0.68rem;
}

.tour-controls button {
  min-height: 2.15rem;
  border-color: rgb(138 169 214 / 16%);
  border-radius: 0.55rem;
  padding: 0.4rem 0.6rem;
  color: #b9cbe1;
  font-size: 0.7rem;
}

.tour-controls button:hover {
  border-color: rgb(53 215 255 / 28%);
  color: #effcff;
  background: rgb(53 215 255 / 8%);
}

.notice-line span {
  color: #65e7fa;
  font-size: 0.62rem;
}

.notice-line p {
  color: rgb(165 190 219 / 72%);
  font-size: 0.72rem;
}

.feedback-actions span {
  color: #7f96b5;
  font-size: 0.63rem;
}

.feedback-actions button {
  min-height: 2rem;
  border-color: rgb(138 169 214 / 20%);
  color: #9eb3ce;
  font-size: 0.68rem;
}

.guide-chat {
  bottom: 1.25rem;
  left: 1.25rem;
  width: min(33rem, calc(100vw - 2.5rem));
  height: min(31rem, 56vh);
}

.location-rail.collapsed,
.tour-rail.collapsed {
  width: 3.35rem;
  border-color: rgb(53 215 255 / 24%);
  border-radius: 0.9rem;
  background: rgb(5 20 44 / 94%);
}

.location-rail.collapsed .rail-collapse,
.tour-rail.collapsed .collapse-button {
  width: 100%;
  height: 2.55rem;
}

.loading-veil,
.load-error {
  background:
    radial-gradient(48rem 30rem at 50% 20%, rgb(53 215 255 / 8%), transparent 68%),
    #020817;
}

.loading-veil p {
  font-size: 1.05rem;
  letter-spacing: 0.02em;
}

.loading-veil small {
  color: #758cab;
  font-size: 0.66rem;
}

.load-error span { color: #6ee9fa; font-size: 0.72rem; }
.load-error p { color: #8ea3c0; font-size: 0.9rem; }

@media (max-width: 1080px) {
  .location-rail { width: 14.2rem; }
  .tour-rail { width: 15.2rem; }
  .guide-chat { width: min(30rem, calc(100vw - 2rem)); }
  .camera-popover { left: 15.8rem; }
  .location-rail.chat-open:not(.collapsed) {
    bottom: calc(min(30rem, 56vh) + 1.35rem);
  }
}

/* Short desktop viewports cannot host the route rail and the floating
   companion side by side; fade the companion out while routes are open. */
@media (min-width: 761px) and (max-height: 980px) {
  .guide-shell.tours-open .avatar-companion {
    visibility: hidden;
    opacity: 0;
    transform: translateY(0.9rem) scale(0.94);
    pointer-events: none;
  }
}

@media (max-width: 760px) {
  .top-rail {
    grid-template-columns: 1fr auto;
    padding: 0.75rem;
  }

  .brand-mark {
    width: 2.2rem;
    height: 2.2rem;
    border-radius: 0.65rem;
  }

  .brand-copy strong { font-size: 0.86rem; }
  .brand-copy small { display: none; }
  .top-status { font-size: 0.66rem; }

  .admin-entry {
    min-height: 2.75rem;
    padding: 0.45rem 0.62rem;
  }

  .location-rail {
    top: 4.7rem;
    bottom: auto;
    left: 0.55rem;
    width: calc(100vw - 5.8rem);
    max-height: none;
    overflow: visible;
    border: 1px solid rgb(138 169 214 / 16%);
    border-radius: 0.95rem;
    padding: 0;
  }

  .rail-heading {
    padding: 0.72rem 2.85rem 0.45rem 0.8rem;
  }

  .rail-heading h1 { font-size: 1.02rem; }
  .rail-heading small { font-size: 0.62rem; }
  .rail-collapse { top: 0.55rem; right: 0.55rem; }

  .location-rail ol {
    padding: 0 0.5rem 0.5rem;
  }

  .location-rail li {
    flex-basis: 9.3rem;
    border-color: rgb(138 169 214 / 13%);
    border-radius: 0.6rem;
    background: rgb(138 169 214 / 4%);
  }

  .location-rail li button {
    min-height: 2.75rem;
    grid-template-columns: 1.35rem minmax(0, 1fr);
    padding: 0.45rem 0.55rem;
  }

  .point-name { font-size: 0.8rem; }

  .camera-toggle {
    width: calc(100% - 1rem);
    min-height: 2.75rem;
    margin: 0.2rem 0.5rem 0.5rem;
    font-size: 0.7rem;
  }

  .tour-rail {
    top: 4.7rem;
    right: 0.55rem;
    width: 13rem;
    max-height: 40vh;
    border-radius: 0.95rem;
  }

  .tour-rail h2 { font-size: 0.88rem; }
  .camera-popover { top: 8.2rem; left: 0.55rem; }
  .guide-chat { right: 0.55rem; bottom: 0.55rem; left: 0.55rem; width: auto; height: min(30rem, 58vh); }
}

</style>
