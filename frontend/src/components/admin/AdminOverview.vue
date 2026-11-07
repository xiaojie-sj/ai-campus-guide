<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import type { ECharts, EChartsCoreOption } from 'echarts/core'
import { BarChart, LineChart, PieChart, RadarChart } from 'echarts/charts'
import { GridComponent, LegendComponent, RadarComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import * as THREE from 'three'
import AppIcon from '../AppIcon.vue'
import type { DashboardResponse, StatsResponse } from '../../types'

echarts.use([LineChart, BarChart, PieChart, RadarChart, GridComponent, TooltipComponent, LegendComponent, RadarComponent, TitleComponent, CanvasRenderer])

const props = defineProps<{ dashboard?: DashboardResponse | null; stats?: StatsResponse | null; role?: string }>()

const PALETTE = ['#00dcf6', '#4974eb', '#16b8d9', '#cc860f', '#b13452', '#6aa7ff', '#2dd4bf', '#f06449']
const CATEGORY_LABEL: Record<string, string> = {
  GATE: '校门', LIBRARY: '图书馆', TEACHING: '教学楼', LAB: '实验楼', DORM: '宿舍', CANTEEN: '食堂', SPORTS: '运动场', ADMIN: '行政楼', SERVICE: '服务设施', OTHER: '其他',
}
const INTENT_LABEL: Record<string, string> = {
  BUILDING_INTRO: '建筑讲解', ROUTE: '路线导航', INFO: '信息咨询', SCENE_CONTROL: '场景控制', CLARIFY: '意图澄清', UNKNOWN: '未识别',
}
const KNOWLEDGE_LABEL: Record<string, string> = {
  PUBLISHED: '已发布', READY: '待发布', DRAFT: '草稿', PROCESSING: '解析中', FAILED: '解析失败', ARCHIVED: '已归档', UPLOADED: '待解析', UNKNOWN: '未知',
}
const AUDIT_ACTION_LABEL: Record<string, string> = {
  LOGIN: '登录成功', LOGOUT: '退出登录',
  POINT_CREATE: '新建点位', POINT_UPDATE: '修改点位', POINT_DELETE: '删除点位',
  FAQ_CREATE: '新建问答', FAQ_UPDATE: '修改问答', FAQ_DELETE: '删除问答',
  TOUR_CREATE: '新建路线', TOUR_UPDATE: '修改路线', TOUR_DELETE: '删除路线',
  CAMERA_CREATE: '新建机位', CAMERA_UPDATE: '修改机位', CAMERA_DELETE: '删除机位',
  ROAD_NODE_CREATE: '新建路点', ROAD_NODE_UPDATE: '修改路点', ROAD_NODE_DELETE: '删除路点',
  ROAD_EDGE_CREATE: '新建路段', ROAD_EDGE_UPDATE: '修改路段', ROAD_EDGE_DELETE: '删除路段',
  CONFIG_UPDATE: '修改参数',
  KNOWLEDGE_UPLOAD: '上传知识文件', KNOWLEDGE_REBUILD: '重建知识索引', KNOWLEDGE_PUBLISH: '发布知识',
  KNOWLEDGE_RETRACT: '撤回知识', KNOWLEDGE_ARCHIVE: '归档知识', KNOWLEDGE_DELETE: '删除知识',
  CHAT_FEEDBACK: '问答反馈', FEEDBACK_SUBMIT: '问答反馈',
}
const AUDIT_TARGET_LABEL: Record<string, string> = {
  SYSTEM: '系统', POINT: '点位', FAQ: '问答', TOUR: '路线', CAMERA: '机位', ROAD: '路网',
  ROAD_NODE: '路点', ROAD_EDGE: '路段', CONFIG: '参数', KNOWLEDGE: '知识库', ADMIN: '管理员',
  CHAT: '对话', FEEDBACK: '反馈', MEDIA: '媒体', CONTENT: '内容', KNOWLEDGE_DOC: '知识文档',
}
function auditAction(value?: string | null) {
  const key = (value ?? '').toUpperCase()
  return AUDIT_ACTION_LABEL[key] ?? value ?? '系统操作'
}
function auditTarget(value?: string | null) {
  const key = (value ?? '').toUpperCase()
  return AUDIT_TARGET_LABEL[key] ?? value ?? 'SYSTEM'
}

const trendEl = ref<HTMLDivElement>()
const intentEl = ref<HTMLDivElement>()
const pointEl = ref<HTMLDivElement>()
const hourEl = ref<HTMLDivElement>()
const radarEl = ref<HTMLDivElement>()
const knowledgeEl = ref<HTMLDivElement>()
const mapHost = ref<HTMLDivElement>()

const chartMap = new Map<string, ECharts>()
const observers: ResizeObserver[] = []
let mapRenderer: THREE.WebGLRenderer | null = null
let mapScene: THREE.Scene | null = null
let mapCamera: THREE.PerspectiveCamera | null = null
let mapRoot: THREE.Group | null = null
let campusGroup: THREE.Group | null = null
let scanMesh: THREE.Mesh | null = null
let haloRings: THREE.Mesh[] = []
let mapFrame = 0
let mapObserver: ResizeObserver | null = null
const pointer = { x: 0, y: 0 }
const reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
const number = new Intl.NumberFormat('zh-CN')

function fmt(value?: number | null) { return number.format(Math.round(Number(value ?? 0))) }
function pct(part: number, total: number) { return total ? Math.round((part / total) * 1000) / 10 : 0 }

const coverage = computed(() => {
  const total = props.dashboard?.totalConversations ?? props.stats?.totalConversations ?? 0
  const miss = props.dashboard?.unanswerableCount ?? props.stats?.unanswerableCount ?? 0
  return pct(Math.max(0, total - miss), total)
})
const tableRows = computed(() => props.dashboard?.tableStats.reduce((sum, row) => sum + row.rows, 0) ?? 0)
const maxTableRows = computed(() => Math.max(1, ...(props.dashboard?.tableStats.map((row) => row.rows) ?? [1])))

function sparkPoints(values: number[]) {
  if (values.length < 2) return '0,26 100,18'
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = Math.max(1, max - min)
  return values.map((value, index) => {
    const x = (index / (values.length - 1)) * 100
    const y = 28 - ((value - min) / span) * 22
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

const primaryKpis = computed(() => {
  const d = props.dashboard
  const s = props.stats
  const trend = d?.dailyTrend ?? []
  const hourly = d?.hourlyActivity ?? []
  const tables = d?.tableStats ?? []
  return [
    {
      code: 'CONVERSATION_TOTAL', label: '累计对话', value: fmt(d?.totalConversations ?? s?.totalConversations), unit: '次',
      hint: `今日新增 ${fmt(d?.todayConversations)} 次`, tone: 'cyan', icon: 'chat',
      width: Math.min(100, 12 + ((d?.todayConversations ?? 0) / 120) * 88),
      spark: sparkPoints(trend.map((item) => item.total)),
    },
    {
      code: 'ACTIVE_SESSION', label: '活跃会话', value: fmt(d?.activeSessions), unit: '个',
      hint: '近 24 小时去重', tone: 'blue', icon: 'activity',
      width: Math.min(100, ((d?.activeSessions ?? 0) / 60) * 100),
      spark: sparkPoints(hourly.map((item) => item.count)),
    },
    {
      code: 'KNOWLEDGE_COVERAGE', label: '知识覆盖率', value: coverage.value.toFixed(1), unit: '%',
      hint: `未命中 ${fmt(d?.unanswerableCount ?? s?.unanswerableCount)} 次`, tone: 'mint', icon: 'shield',
      width: coverage.value,
      spark: sparkPoints(trend.map((item) => (item.total ? ((item.total - item.unanswerable) / item.total) * 100 : 0))),
    },
    {
      code: 'DATA_ASSET_ROWS', label: '数据资产', value: fmt(tableRows.value), unit: '行',
      hint: `${d?.tableStats.length ?? 0} 张业务表`, tone: 'violet', icon: 'database',
      width: Math.min(100, (tableRows.value / 2200) * 100),
      spark: sparkPoints([...tables].map((item) => item.rows).sort((a, b) => a - b)),
    },
  ]
})

const telemetry = computed(() => {
  const d = props.dashboard
  return [
    { label: 'AVG_LATENCY', value: `${(d?.averageLatencyMs ?? 0).toFixed(1)} ms` },
    { label: 'P95_LATENCY', value: `${fmt(d?.p95LatencyMs)} ms` },
    { label: 'RAG_CHUNKS', value: fmt(d?.assets.chunks) },
    { label: 'CAMERA_RIG', value: fmt(d?.assets.cameras) },
    { label: 'ROAD_EDGE', value: fmt(d?.assets.roadEdges) },
    { label: 'ADMIN_NODE', value: fmt(d?.assets.admins) },
  ]
})

const pointCategories = computed(() => Object.entries(props.dashboard?.pointCategoryCounts ?? {}).slice(0, 5).map(([name, value], index) => ({ name, label: CATEGORY_LABEL[name] ?? name, value, color: PALETTE[index % PALETTE.length] })))
const topPoints = computed(() => (props.dashboard?.popularPoints ?? props.stats?.popularPoints ?? []).slice(0, 5))
const isSensitiveRole = computed(() => ['SYSTEM_ADMIN', 'DATA_ANALYST'].includes(props.role ?? ''))

function chart(key: string, el?: HTMLDivElement) {
  if (!el || el.clientWidth === 0 || el.clientHeight === 0) return null
  let instance = chartMap.get(key)
  if (!instance) {
    instance = echarts.init(el, undefined, { renderer: 'canvas' })
    chartMap.set(key, instance)
    const observer = new ResizeObserver(() => instance?.resize())
    observer.observe(el)
    observers.push(observer)
  }
  return instance
}

function baseTooltip() {
  return { backgroundColor: 'rgba(3, 15, 39, 0.96)', borderColor: 'rgba(0, 220, 246, 0.38)', borderWidth: 1, padding: [8, 12], textStyle: { color: '#e2f3ff', fontSize: 12 }, extraCssText: 'box-shadow: 0 12px 34px rgba(0,0,0,.36); border-radius:2px;' }
}
function axisCommon() {
  return { axisLine: { lineStyle: { color: 'rgba(84, 156, 205, .32)' } }, axisTick: { show: false }, axisLabel: { color: '#7fa0ba', fontSize: 10 }, splitLine: { lineStyle: { color: 'rgba(66, 118, 157, .14)' } } }
}

function renderTrend() {
  const instance = chart('trend', trendEl.value)
  if (!instance) return
  const trend = props.dashboard?.dailyTrend ?? []
  instance.setOption({
    backgroundColor: 'transparent', tooltip: { ...baseTooltip(), trigger: 'axis' },
    legend: { top: 0, right: 8, itemWidth: 14, itemHeight: 6, textStyle: { color: '#8bafc8', fontSize: 11 } },
    grid: { left: 46, right: 20, top: 32, bottom: 26 },
    xAxis: { type: 'category', boundaryGap: false, data: trend.map((day) => day.date), ...axisCommon(), splitLine: { show: false } },
    yAxis: { type: 'value', ...axisCommon(), axisLabel: { color: '#7fa0ba', fontSize: 11 } },
    series: [
      { name: '对话量', type: 'line', smooth: 0.32, symbol: 'circle', showSymbol: false, symbolSize: 5, data: trend.map((day) => day.total), lineStyle: { width: 2.4, color: '#00dcf6', shadowColor: 'rgba(0,220,246,.55)', shadowBlur: 14 }, itemStyle: { color: '#bdfbff', borderColor: '#00dcf6' }, areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(0, 220, 246, .28)' }, { offset: 0.55, color: 'rgba(43, 142, 172, .08)' }, { offset: 1, color: 'rgba(0, 220, 246, 0)' }]) } },
      { name: '未命中', type: 'line', smooth: true, symbol: 'circle', showSymbol: false, symbolSize: 3, data: trend.map((day) => day.unanswerable), lineStyle: { width: 1.5, color: '#ff6b8a', type: 'dashed' }, itemStyle: { color: '#ff9db2' } },
    ],
  } satisfies EChartsCoreOption)
}

function renderIntent() {
  const instance = chart('intent', intentEl.value)
  if (!instance) return
  const counts = props.dashboard?.intentCounts ?? props.stats?.intentCounts ?? {}
  const data = Object.entries(counts).map(([name, value]) => ({ name: INTENT_LABEL[name] ?? name, value }))
  const total = data.reduce((sum, item) => sum + Number(item.value || 0), 0)
  instance.setOption({
    color: PALETTE, tooltip: { ...baseTooltip(), trigger: 'item' },
    title: { text: fmt(total), subtext: 'INTENTS', left: '35%', top: '37%', textAlign: 'center', textStyle: { color: '#f2fdff', fontFamily: 'Cascadia Mono, monospace', fontSize: 22, fontWeight: 700 }, subtextStyle: { color: '#5f8fb1', fontFamily: 'Cascadia Mono, monospace', fontSize: 9 } },
    legend: { type: 'scroll', orient: 'vertical', right: 2, top: 'middle', itemWidth: 8, itemHeight: 8, itemGap: 11, textStyle: { color: '#88aac3', fontSize: 11 } },
    series: [{ type: 'pie', radius: ['48%', '70%'], center: ['35%', '52%'], itemStyle: { borderColor: '#040d29', borderWidth: 3 }, label: { show: false }, emphasis: { scaleSize: 5 }, data: data.length ? data : [{ name: '暂无数据', value: 1 }] }],
  } satisfies EChartsCoreOption)
}

function renderPoints() {
  const instance = chart('point', pointEl.value)
  if (!instance) return
  const rows = (props.dashboard?.popularPoints ?? props.stats?.popularPoints ?? []).slice(0, 6).reverse()
  instance.setOption({
    tooltip: { ...baseTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 78, right: 30, top: 10, bottom: 18 },
    xAxis: { type: 'value', ...axisCommon() },
    yAxis: { type: 'category', data: rows.map((row) => row.name), ...axisCommon(), splitLine: { show: false }, axisLabel: { color: '#a4c2d6', fontSize: 11, width: 68, overflow: 'truncate' } },
    series: [{ type: 'bar', data: rows.map((row) => row.count), barWidth: 9, showBackground: true, backgroundStyle: { color: 'rgba(73, 116, 235, .08)', borderRadius: 6 }, itemStyle: { borderRadius: [0, 5, 5, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: 'rgba(73, 116, 235, .24)' }, { offset: 0.55, color: '#16b8d9' }, { offset: 1, color: '#55efff' }]) }, label: { show: true, position: 'right', color: '#7cf3ff', fontSize: 10, fontWeight: 600 } }],
  } satisfies EChartsCoreOption)
}

function renderHours() {
  const instance = chart('hour', hourEl.value)
  if (!instance) return
  const rows = props.dashboard?.hourlyActivity ?? []
  instance.setOption({
    tooltip: { ...baseTooltip(), trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 38, right: 14, top: 14, bottom: 24 },
    xAxis: { type: 'category', data: rows.map((row) => row.name), ...axisCommon(), splitLine: { show: false }, axisLabel: { color: '#7499b5', fontSize: 10, interval: 2 } },
    yAxis: { type: 'value', ...axisCommon() },
    series: [{ type: 'bar', data: rows.map((row) => row.count), barWidth: '48%', itemStyle: { borderRadius: [3, 3, 0, 0], color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#66e9ff' }, { offset: 0.45, color: '#2f8cff' }, { offset: 1, color: 'rgba(0, 220, 246, .05)' }]) }, emphasis: { itemStyle: { color: '#9ff7ff' } } }],
  } satisfies EChartsCoreOption)
}

function renderRadar() {
  const instance = chart('radar', radarEl.value)
  if (!instance) return
  const a = props.dashboard?.assets
  const norm = (value: number, target: number) => Math.min(100, Math.round((value / target) * 100))
  const values = a ? [norm(a.points, 40), norm(a.roadEdges, 80), norm(a.cameras, 10), norm(a.tours, 8), norm(a.documents, 30), norm(a.faqs, 30)] : [0, 0, 0, 0, 0, 0]
  instance.setOption({
    tooltip: baseTooltip(),
    radar: { center: ['50%', '51%'], radius: '62%', indicator: [{ name: '点位', max: 100 }, { name: '路网', max: 100 }, { name: '机位', max: 100 }, { name: '路线', max: 100 }, { name: '知识', max: 100 }, { name: 'FAQ', max: 100 }], axisName: { color: '#8baec6', fontSize: 11 }, splitLine: { lineStyle: { color: 'rgba(73, 116, 235, .2)' } }, splitArea: { areaStyle: { color: ['rgba(7, 26, 59, .26)', 'rgba(4, 16, 42, .4)'] } }, axisLine: { lineStyle: { color: 'rgba(73, 116, 235, .18)' } } },
    series: [{ type: 'radar', symbol: 'circle', symbolSize: 4, data: [{ value: values, name: '资产覆盖', lineStyle: { color: '#42eaff', width: 1.8, shadowColor: 'rgba(0,220,246,.45)', shadowBlur: 10 }, itemStyle: { color: '#c4fdff' }, areaStyle: { color: new echarts.graphic.RadialGradient(0.5, 0.5, 1, [{ offset: 0, color: 'rgba(0, 220, 246, .38)' }, { offset: 1, color: 'rgba(73, 116, 235, .05)' }]) } }] }],
  } satisfies EChartsCoreOption)
}

function renderKnowledge() {
  const instance = chart('knowledge', knowledgeEl.value)
  if (!instance) return
  const status = props.dashboard?.knowledgeStatusCounts ?? {}
  const parse = props.dashboard?.parseStatusCounts ?? {}
  const mapEntries = (source: Record<string, number>) => Object.entries(source).map(([name, value]) => ({ name: KNOWLEDGE_LABEL[name] ?? name, value }))
  const outer = mapEntries(status)
  const inner = mapEntries(parse)
  const total = (Object.keys(status).length ? outer : inner).reduce((sum, item) => sum + Number(item.value || 0), 0)
  const series: Record<string, unknown>[] = []
  if (inner.length) series.push({ type: 'pie', radius: ['26%', '40%'], center: ['50%', '45%'], silent: true, itemStyle: { borderColor: '#040d29', borderWidth: 2 }, label: { show: false }, data: inner })
  if (outer.length) series.push({ type: 'pie', radius: ['48%', '64%'], center: ['50%', '45%'], itemStyle: { borderColor: '#040d29', borderWidth: 2 }, label: { color: '#bcd8e8', fontSize: 10, formatter: '{d}%' }, labelLine: { length: 5, length2: 4, lineStyle: { color: 'rgba(73,116,235,.4)' } }, data: outer })
  instance.setOption({
    color: ['#00dcf6', '#4974eb', '#2dd4bf', '#7aa7ff', '#cc860f', '#b13452'], tooltip: { ...baseTooltip(), trigger: 'item' },
    title: { text: fmt(total), subtext: 'DOCS', left: '50%', top: '36%', textAlign: 'center', textStyle: { color: '#f0fdff', fontFamily: 'Cascadia Mono, monospace', fontSize: 22, fontWeight: 700 }, subtextStyle: { color: '#5f8fb1', fontFamily: 'Cascadia Mono, monospace', fontSize: 9 } },
    legend: { bottom: 0, icon: 'rect', itemWidth: 7, itemHeight: 5, itemGap: 10, textStyle: { color: '#88aac3', fontSize: 10 } },
    series: series.length ? series : [{ type: 'pie', radius: ['48%', '64%'], center: ['50%', '45%'], label: { show: false }, data: [{ name: '暂无文档', value: 1 }] }],
  } satisfies EChartsCoreOption)
}

function renderAll() { renderTrend(); renderIntent(); renderPoints(); renderHours(); renderRadar(); renderKnowledge() }

function disposeGroup(group: THREE.Group | null) {
  if (!group) return
  group.traverse((object) => {
    const drawable = object as THREE.Mesh
    drawable.geometry?.dispose()
    const material = drawable.material as THREE.Material | THREE.Material[] | undefined
    const materials = Array.isArray(material) ? material : material ? [material] : []
    materials.forEach((item) => {
      const mapped = item as THREE.Material & { map?: THREE.Texture | null }
      mapped.map?.dispose()
      item.dispose()
    })
  })
}

function makeGlowTexture() {
  const size = 256
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (!context) return null
  const gradient = context.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(0, 176, 255, 0.34)')
  gradient.addColorStop(0.42, 'rgba(0, 132, 255, 0.12)')
  gradient.addColorStop(1, 'rgba(0, 84, 190, 0)')
  context.fillStyle = gradient
  context.fillRect(0, 0, size, size)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function makeLabelSprite(text: string, color: number) {
  const ratio = 2
  const width = 360
  const height = 80
  const canvas = document.createElement('canvas')
  canvas.width = width * ratio
  canvas.height = height * ratio
  const context = canvas.getContext('2d')
  if (!context) return null
  context.scale(ratio, ratio)
  const stroke = `#${color.toString(16).padStart(6, '0')}`
  const x = 1
  const y = 1
  const w = width - 2
  const h = height - 2
  const radius = 8
  context.beginPath()
  context.moveTo(x + radius, y)
  context.lineTo(x + w - radius, y)
  context.quadraticCurveTo(x + w, y, x + w, y + radius)
  context.lineTo(x + w, y + h - radius)
  context.quadraticCurveTo(x + w, y + h, x + w - radius, y + h)
  context.lineTo(x + radius, y + h)
  context.quadraticCurveTo(x, y + h, x, y + h - radius)
  context.lineTo(x, y + radius)
  context.quadraticCurveTo(x, y, x + radius, y)
  context.closePath()
  context.fillStyle = 'rgba(3, 13, 38, 0.88)'
  context.fill()
  context.strokeStyle = stroke
  context.globalAlpha = 0.8
  context.lineWidth = 1.5
  context.stroke()
  context.globalAlpha = 1
  context.fillStyle = '#dff8ff'
  context.font = '600 24px "Microsoft YaHei", "PingFang SC", sans-serif'
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  context.fillText(text, width / 2, height / 2 + 1)
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.minFilter = THREE.LinearFilter
  const material = new THREE.SpriteMaterial({ map: texture, transparent: true, depthWrite: false, depthTest: false })
  const sprite = new THREE.Sprite(material)
  sprite.scale.set(3.1, 0.69, 1)
  return sprite
}

function initMap() {
  const host = mapHost.value
  if (!host) return
  try {
    mapRenderer = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: 'high-performance' })
  } catch {
    host.classList.add('map-unavailable')
    return
  }
  mapRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  mapRenderer.setSize(host.clientWidth, host.clientHeight, false)
  host.appendChild(mapRenderer.domElement)

  mapScene = new THREE.Scene()
  mapScene.fog = new THREE.FogExp2(0x040d29, 0.018)
  mapCamera = new THREE.PerspectiveCamera(37, host.clientWidth / Math.max(1, host.clientHeight), 0.1, 120)
  mapCamera.position.set(0, 13.8, 17.1)
  mapCamera.lookAt(0, 1.05, 0)

  mapScene.add(new THREE.AmbientLight(0x4e9fbd, 1.05))
  const keyLight = new THREE.DirectionalLight(0xbdf7ff, 1.5)
  keyLight.position.set(7, 14, 9)
  mapScene.add(keyLight)
  const rimLight = new THREE.PointLight(0x2f86ff, 1.25, 42, 1.4)
  rimLight.position.set(-7, 5, -6)
  mapScene.add(rimLight)

  mapRoot = new THREE.Group()
  mapRoot.rotation.x = -0.03
  mapScene.add(mapRoot)

  const disc = new THREE.Mesh(new THREE.CircleGeometry(9.2, 96), new THREE.MeshBasicMaterial({ color: 0x041633, transparent: true, opacity: 0.94, depthWrite: false }))
  disc.rotation.x = -Math.PI / 2
  mapRoot.add(disc)

  const glowTexture = makeGlowTexture()
  if (glowTexture) {
    const glow = new THREE.Mesh(new THREE.PlaneGeometry(19.4, 19.4), new THREE.MeshBasicMaterial({ map: glowTexture, transparent: true, opacity: 0.95, depthWrite: false, blending: THREE.AdditiveBlending }))
    glow.rotation.x = -Math.PI / 2
    glow.position.y = 0.006
    mapRoot.add(glow)
  }

  const ringSpecs = [
    { radius: 8.1, color: 0x00dcf6, opacity: 0.4, speed: 0.05 },
    { radius: 6.2, color: 0x4974eb, opacity: 0.3, speed: -0.07 },
    { radius: 4.15, color: 0x16b8d9, opacity: 0.25, speed: 0.09 },
  ]
  haloRings = ringSpecs.map((spec) => {
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(spec.radius, spec.radius + 0.035, 128, 1, 0, Math.PI * 1.55),
      new THREE.MeshBasicMaterial({ color: spec.color, transparent: true, opacity: spec.opacity, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending }),
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.018
    ring.userData.speed = spec.speed
    mapRoot?.add(ring)
    return ring
  })

  const grid = new THREE.GridHelper(19, 28, 0x0a759b, 0x0a3558)
  const gridMaterial = grid.material as THREE.Material
  gridMaterial.transparent = true
  gridMaterial.opacity = 0.26
  mapRoot.add(grid)

  const boundary = new THREE.Mesh(new THREE.RingGeometry(8.88, 8.94, 128), new THREE.MeshBasicMaterial({ color: 0x00dcf6, transparent: true, opacity: 0.45, side: THREE.DoubleSide }))
  boundary.rotation.x = -Math.PI / 2
  boundary.position.y = 0.015
  mapRoot.add(boundary)

  scanMesh = new THREE.Mesh(new THREE.RingGeometry(0.45, 8.86, 96, 1, 0, Math.PI / 4), new THREE.MeshBasicMaterial({ color: 0x00dcf6, transparent: true, opacity: 0.065, side: THREE.DoubleSide, depthWrite: false, blending: THREE.AdditiveBlending }))
  scanMesh.rotation.x = -Math.PI / 2
  scanMesh.position.y = 0.025
  mapRoot.add(scanMesh)

  const particlePositions = new Float32Array(320 * 3)
  for (let index = 0; index < 320; index += 1) {
    const radius = 3.8 + Math.random() * 8.2
    const angle = Math.random() * Math.PI * 2
    particlePositions[index * 3] = Math.cos(angle) * radius
    particlePositions[index * 3 + 1] = 0.12 + Math.random() * 4.8
    particlePositions[index * 3 + 2] = Math.sin(angle) * radius
  }
  const particleGeometry = new THREE.BufferGeometry()
  particleGeometry.setAttribute('position', new THREE.BufferAttribute(particlePositions, 3))
  mapRoot.add(new THREE.Points(particleGeometry, new THREE.PointsMaterial({ color: 0x4cc9f0, size: 0.025, transparent: true, opacity: 0.42 })))

  const startedAt = performance.now()
  const animate = () => {
    mapFrame = requestAnimationFrame(animate)
    if (!mapRenderer || !mapScene || !mapCamera || !mapRoot || document.hidden) return
    const time = (performance.now() - startedAt) / 1000
    mapRoot.rotation.y = Math.sin(time * 0.08) * 0.035 + pointer.x * 0.075
    mapRoot.rotation.x = -0.03 + pointer.y * 0.018
    if (scanMesh) scanMesh.rotation.z = -time * 0.24
    haloRings.forEach((ring) => { ring.rotation.z = time * Number(ring.userData.speed || 0) })
    mapCamera.position.x = pointer.x * 0.5
    mapCamera.lookAt(0, 1.05, 0)
    mapRenderer.render(mapScene, mapCamera)
  }

  if (reducedMotion) mapRenderer.render(mapScene, mapCamera)
  else animate()

  const onPointerMove = (event: PointerEvent) => {
    const rect = host.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / Math.max(1, rect.width) - 0.5) * 2
    pointer.y = ((event.clientY - rect.top) / Math.max(1, rect.height) - 0.5) * 2
  }
  host.addEventListener('pointermove', onPointerMove)
  host.addEventListener('pointerleave', () => { pointer.x = 0; pointer.y = 0 })

  mapObserver = new ResizeObserver(() => {
    if (!mapRenderer || !mapCamera || !host) return
    const width = host.clientWidth
    const height = host.clientHeight
    if (!width || !height) return
    mapRenderer.setSize(width, height, false)
    mapCamera.aspect = width / height
    mapCamera.updateProjectionMatrix()
  })
  mapObserver.observe(host)
  buildCampusModel()
}

function buildCampusModel() {
  if (!mapRoot) return
  if (campusGroup) {
    mapRoot.remove(campusGroup)
    disposeGroup(campusGroup)
  }
  campusGroup = new THREE.Group()
  mapRoot.add(campusGroup)

  const points = props.dashboard?.pointMarkers ?? []
  const nodes = props.dashboard?.roadNodes ?? []
  const edges = props.dashboard?.roadEdges ?? []
  const xs = [...points.map((point) => point.x), ...nodes.map((node) => node.x)]
  const zs = [...points.map((point) => point.z), ...nodes.map((node) => node.z)]
  const minX = xs.length ? Math.min(...xs) : -10
  const maxX = xs.length ? Math.max(...xs) : 10
  const minZ = zs.length ? Math.min(...zs) : -10
  const maxZ = zs.length ? Math.max(...zs) : 10
  const centerX = (minX + maxX) / 2
  const centerZ = (minZ + maxZ) / 2
  const scale = 14.6 / Math.max(8, maxX - minX, maxZ - minZ)
  const toScene = (x: number, z: number) => ({ x: (x - centerX) * scale, z: (z - centerZ) * scale })

  const nodeMap = new Map(nodes.map((node) => [node.id, node]))
  const roadPositions: number[] = []
  for (const edge of edges) {
    const from = nodeMap.get(edge.fromNodeId)
    const to = nodeMap.get(edge.toNodeId)
    if (!from || !to) continue
    const a = toScene(from.x, from.z)
    const b = toScene(to.x, to.z)
    roadPositions.push(a.x, 0.04, a.z, b.x, 0.04, b.z)
  }
  if (roadPositions.length) {
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(roadPositions, 3))
    campusGroup.add(new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ color: 0x0b4f7a, transparent: true, opacity: 0.9 })))
    campusGroup.add(new THREE.LineSegments(geometry, new THREE.LineBasicMaterial({ color: 0x3ad4ff, transparent: true, opacity: 0.42, blending: THREE.AdditiveBlending })))
  }

  const categoryColors: Record<string, number> = {
    GATE: 0x00dcf6, LIBRARY: 0x4974eb, TEACHING: 0x16b8d9, LAB: 0x2dd4bf, DORM: 0xcc860f,
    CANTEEN: 0xf06449, SPORTS: 0xb13452, ADMIN: 0x6aa7ff,
  }

  const spotlight = new Map(topPoints.value.map((item, index) => [item.name, index]))

  for (const point of points) {
    const position = toScene(point.x, point.z)
    const height = Math.max(0.3, Math.min(4.4, (point.height || 6) * 0.23))
    const color = categoryColors[point.category] ?? 0x4cc9f0
    const published = point.status === 'PUBLISHED'
    const width = point.category === 'GATE' ? 0.12 : 0.19
    const geometry = new THREE.BoxGeometry(width, height, width)
    const building = new THREE.Mesh(geometry, new THREE.MeshPhongMaterial({ color, emissive: color, emissiveIntensity: published ? 0.16 : 0.05, shininess: 92, specular: 0x9feeff, transparent: true, opacity: published ? 0.44 : 0.18, depthWrite: false, side: THREE.DoubleSide }))
    building.position.set(position.x, height / 2, position.z)
    campusGroup.add(building)

    const outline = new THREE.LineSegments(new THREE.EdgesGeometry(geometry), new THREE.LineBasicMaterial({ color, transparent: true, opacity: published ? 0.92 : 0.45 }))
    outline.position.copy(building.position)
    campusGroup.add(outline)

    const beacon = new THREE.Mesh(new THREE.SphereGeometry(published ? 0.055 : 0.04, 10, 10), new THREE.MeshBasicMaterial({ color: published ? 0x8ff8ff : 0x5e7f9b }))
    beacon.position.set(position.x, height + 0.06, position.z)
    campusGroup.add(beacon)

    const baseRing = new THREE.Mesh(new THREE.RingGeometry(0.11, 0.14, 28), new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.55, side: THREE.DoubleSide }))
    baseRing.rotation.x = -Math.PI / 2
    baseRing.position.set(position.x, 0.03, position.z)
    campusGroup.add(baseRing)

    const rank = spotlight.get(point.name)
    if (rank !== undefined && published) {
      const beamHeight = 1.35 + rank * 0.16
      const beam = new THREE.Mesh(
        new THREE.CylinderGeometry(0.018, 0.026, beamHeight, 6, 1, true),
        new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.5, depthWrite: false, blending: THREE.AdditiveBlending }),
      )
      beam.position.set(position.x, height + beamHeight / 2, position.z)
      campusGroup.add(beam)

      const crown = new THREE.Mesh(new THREE.SphereGeometry(0.075, 12, 12), new THREE.MeshBasicMaterial({ color: 0xb8fbff, transparent: true, opacity: 0.95 }))
      crown.position.set(position.x, height + beamHeight, position.z)
      campusGroup.add(crown)

      const hoverRing = new THREE.Mesh(new THREE.RingGeometry(0.16, 0.2, 32), new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.72, side: THREE.DoubleSide, depthWrite: false }))
      hoverRing.rotation.x = -Math.PI / 2
      hoverRing.position.set(position.x, height + beamHeight - 0.07, position.z)
      campusGroup.add(hoverRing)

      const label = makeLabelSprite(point.name, color)
      if (label) {
        label.position.set(position.x, height + beamHeight + 0.48, position.z)
        campusGroup.add(label)
      }
    }
  }
}

function disposeMap() {
  cancelAnimationFrame(mapFrame)
  mapObserver?.disconnect()
  if (mapScene) {
    mapScene.traverse((object) => {
      const drawable = object as THREE.Mesh & { material?: THREE.Material | THREE.Material[] }
      drawable.geometry?.dispose()
      const materials = Array.isArray(drawable.material) ? drawable.material : drawable.material ? [drawable.material] : []
      materials.forEach((item) => {
        const mapped = item as THREE.Material & { map?: THREE.Texture | null }
        mapped.map?.dispose()
        item.dispose()
      })
    })
  }
  mapRenderer?.dispose()
  mapRenderer?.domElement.remove()
  mapRenderer = null
  mapScene = null
  mapCamera = null
  mapRoot = null
  campusGroup = null
  scanMesh = null
  haloRings = []
}

onMounted(async () => {
  await nextTick()
  renderAll()
  initMap()
})

onBeforeUnmount(() => {
  observers.forEach((observer) => observer.disconnect())
  chartMap.forEach((instance) => instance.dispose())
  chartMap.clear()
  disposeMap()
})

watch(() => props.dashboard, async () => {
  await nextTick()
  renderAll()
  buildCampusModel()
}, { deep: true })
</script>

<template>
  <section class="data-wall">
    <section class="kpi-ribbon">
      <article v-for="kpi in primaryKpis" :key="kpi.code" class="kpi-card" :class="`tone-${kpi.tone}`">
        <span class="kpi-glyph" aria-hidden="true"><AppIcon :name="kpi.icon" :size="20" /></span>
        <div class="kpi-copy">
          <header><span>{{ kpi.label }}</span><small>{{ kpi.code }}</small></header>
          <div class="kpi-value"><strong>{{ kpi.value }}</strong><em>{{ kpi.unit }}</em></div>
          <p>{{ kpi.hint }}</p>
        </div>
        <svg class="kpi-spark" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden="true">
          <polygon :points="`0,32 ${kpi.spark} 100,32`" />
          <polyline :points="kpi.spark" />
        </svg>
        <i class="kpi-meter"><b :style="{ width: `${Math.min(100, Math.max(2, kpi.width))}%` }" /></i>
      </article>
    </section>

    <section class="command-grid">
      <div class="side-column">
        <article class="wall-panel panel-chart">
          <header class="panel-head"><div><span>24H_SIGNAL</span><h2>时段对话活跃度</h2></div><small>LIVE STREAM</small></header>
          <div ref="hourEl" class="chart-body" />
        </article>
        <article class="wall-panel panel-chart">
          <header class="panel-head"><div><span>INTENT_MATRIX</span><h2>用户意图分布</h2></div><small>30D WINDOW</small></header>
          <div ref="intentEl" class="chart-body" />
        </article>
      </div>

      <article class="map-panel">
        <header class="map-head">
          <div><span>SPATIAL_SITUATION / 3D DIGITAL TWIN</span><h2>校园三维态势总览</h2></div>
          <div class="map-head-right">
            <span class="twin-chip">{{ fmt(dashboard?.assets.points) }} POINTS</span>
            <span class="twin-state"><i /> LIVE TWIN</span>
          </div>
        </header>
        <div ref="mapHost" class="map-host" aria-hidden="true" />
        <div class="map-scanline" aria-hidden="true" />
        <div class="map-overlay map-top-left">
          <span>SPATIAL COVERAGE</span>
          <strong>{{ pointCategories.length ? '空间模型已接入' : '正在建立三维映射' }}</strong>
          <small>{{ fmt(dashboard?.assets.roadNodes) }} NODES / {{ fmt(dashboard?.assets.roadEdges) }} EDGES</small>
        </div>
        <div class="map-overlay map-top-right">
          <span>TOP SIGNAL / 30D</span>
          <ol><li v-for="(point, index) in topPoints" :key="point.name"><b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ point.name }}</span><em>{{ fmt(point.count) }}</em></li></ol>
        </div>
        <div class="map-legend">
          <span v-for="item in pointCategories" :key="item.name" :style="{ '--legend-color': item.color }"><i />{{ item.label }} {{ item.value }}</span>
        </div>
        <footer class="map-footer">
          <span v-for="item in telemetry" :key="item.label"><small>{{ item.label }}</small><b>{{ item.value }}</b></span>
        </footer>
      </article>

      <div class="side-column">
        <article class="wall-panel panel-chart">
          <header class="panel-head"><div><span>POINT_SIGNAL</span><h2>热门点位 Top 6</h2></div><small>30D RANKING</small></header>
          <div ref="pointEl" class="chart-body" />
        </article>
        <article class="wall-panel panel-chart">
          <header class="panel-head"><div><span>KNOWLEDGE_PIPELINE</span><h2>知识库生命周期</h2></div><small>{{ fmt(dashboard?.assets.chunks) }} CHUNKS</small></header>
          <div ref="knowledgeEl" class="chart-body" />
        </article>
      </div>
    </section>

    <section class="lower-grid">
      <article class="wall-panel panel-chart span-5">
        <header class="panel-head"><div><span>CONVERSATION_FLOW</span><h2>对话流量与未命中趋势</h2></div><small>LAST 14 DAYS</small></header>
        <div ref="trendEl" class="chart-body chart-wide" />
      </article>
      <article class="wall-panel table-panel span-4">
        <header class="panel-head"><div><span>DATA_MATRIX</span><h2>业务表实时行数</h2></div><small>{{ dashboard?.tableStats.length ?? 0 }} TABLES · {{ fmt(tableRows) }} ROWS</small></header>
        <div class="table-matrix">
          <div v-for="row in dashboard?.tableStats ?? []" :key="row.table" class="matrix-row">
            <span>{{ row.label }}</span><i><b :style="{ width: `${Math.max(2, Math.pow(row.rows / maxTableRows, 0.55) * 100)}%` }" /></i><strong>{{ fmt(row.rows) }}</strong><em>{{ row.table }}</em>
          </div>
          <p v-if="!dashboard?.tableStats.length" class="matrix-empty">正在读取数据库表统计…</p>
        </div>
      </article>
      <article class="wall-panel panel-chart span-3">
        <header class="panel-head"><div><span>ASSET_RADAR</span><h2>数据资产覆盖</h2></div><small>COVERAGE</small></header>
        <div ref="radarEl" class="chart-body chart-wide" />
      </article>
    </section>

    <section class="insight-strip">
      <article class="wall-panel gap-panel">
        <header class="panel-head"><div><span>KNOWLEDGE_GAP</span><h2>高频未命中问题</h2></div><small>TOP {{ dashboard?.frequentQuestions.length ?? 0 }}</small></header>
        <ol class="gap-list">
          <li v-for="(question, index) in dashboard?.frequentQuestions ?? []" :key="`${question.name}-${index}`">
            <b>{{ String(index + 1).padStart(2, '0') }}</b><span>{{ question.name }}</span><em>{{ fmt(question.count) }}</em>
          </li>
          <li v-if="!dashboard?.frequentQuestions.length" class="empty-row">
            <b>--</b><span>{{ isSensitiveRole ? '暂无未命中问题，知识覆盖良好' : '当前角色无原始问题权限' }}</span><em>0</em>
          </li>
        </ol>
      </article>
      <article class="wall-panel audit-panel">
        <header class="panel-head"><div><span>AUDIT_STREAM</span><h2>最近操作轨迹</h2></div><small>IMMUTABLE LOG</small></header>
        <ol class="audit-list">
          <li v-for="(audit, index) in dashboard?.recentAudits ?? []" :key="`${audit.createdAt}-${index}`">
            <time>{{ audit.createdAt }}</time><strong>{{ auditAction(audit.action) }}</strong><span>{{ auditTarget(audit.targetType) }} · {{ audit.detail || '—' }}</span>
          </li>
          <li v-if="!dashboard?.recentAudits.length" class="audit-empty">
            <time>--</time><strong>{{ isSensitiveRole ? '暂无审计记录' : '当前角色无审计详情权限' }}</strong>
            <span>{{ isSensitiveRole ? '完成一次管理操作后，轨迹会实时出现在这里' : '聚合指标仍可正常访问，审计原文遵循最小权限原则' }}</span>
          </li>
        </ol>
      </article>
    </section>
  </section>
</template>

<style scoped>
.data-wall {
  --wall-panel: linear-gradient(155deg, rgb(8 30 66 / 92%), rgb(4 13 40 / 97%));
  --wall-panel-2: linear-gradient(180deg, rgb(7 25 57 / 94%), rgb(3 11 34 / 98%));
  --wall-line: rgb(84 156 205 / 22%);
  --wall-line-soft: rgb(84 156 205 / 11%);
  --wall-cyan: #00dcf6;
  --wall-blue: #4974eb;
  --wall-teal: #16b8d9;
  --wall-amber: #cc860f;
  --wall-rose: #b13452;
  --wall-text: #e8f6ff;
  --wall-muted: #7ba4c3;
  position: relative;
  isolation: isolate;
  display: grid;
  gap: 0.62rem;
  min-width: 0;
  color: var(--wall-text);
}

.data-wall::before {
  position: absolute;
  z-index: -1;
  inset: -1.2rem -0.6rem -1.1rem;
  background:
    radial-gradient(48rem 26rem at 50% -8%, rgb(0 220 246 / 13%), transparent 70%),
    radial-gradient(42rem 32rem at 2% 52%, rgb(73 116 235 / 10%), transparent 72%),
    radial-gradient(40rem 30rem at 100% 58%, rgb(22 184 217 / 8%), transparent 72%),
    radial-gradient(22rem 14rem at 50% 42%, rgb(0 150 220 / 6%), transparent 76%);
  content: '';
  pointer-events: none;
}

.data-wall::after {
  position: absolute;
  z-index: -1;
  inset: 0;
  background-image:
    radial-gradient(circle, rgb(116 219 255 / 22%) 0.6px, transparent 0.8px),
    linear-gradient(90deg, transparent, rgb(0 220 246 / 3%), transparent);
  background-position: 0 0, 0 0;
  background-size: 26px 26px, 100% 100%;
  content: '';
  opacity: 0.34;
  mask-image: linear-gradient(180deg, transparent, #000 12%, #000 88%, transparent);
  pointer-events: none;
}

/* ── Header ─────────────────────────────────────────────────────────────── */
.wall-header {
  position: relative;
  display: grid;
  grid-template-columns: minmax(10rem, 0.75fr) minmax(24rem, 1.8fr) minmax(13rem, 0.75fr);
  align-items: center;
  gap: 1rem;
  min-height: 4.85rem;
  border-bottom: 1px solid var(--wall-line);
  padding: 0.24rem 0.4rem 0.58rem;
  background: linear-gradient(180deg, rgb(8 30 66 / 34%), transparent 78%);
}

.wall-header::before,
.wall-header::after {
  position: absolute;
  bottom: -1px;
  width: 28%;
  height: 1px;
  content: '';
}

.wall-header::before {
  left: 0;
  background: linear-gradient(90deg, transparent, var(--wall-cyan));
}

.wall-header::after {
  right: 0;
  background: linear-gradient(90deg, var(--wall-cyan), transparent);
}

.header-wing {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.header-left { justify-items: start; }
.header-right { justify-items: end; gap: 0.55rem; }

.micro-label {
  color: #6695b6;
  font: 0.6rem/1 var(--font-mono);
  letter-spacing: 0.2em;
}

.header-left strong {
  color: #f0fdff;
  font: 600 1.78rem/1 var(--font-mono);
  letter-spacing: 0.06em;
  text-shadow: 0 0 22px rgb(0 220 246 / 42%);
}

.header-left small {
  color: #7fa0ba;
  font: 0.6rem/1.2 var(--font-mono);
  letter-spacing: 0.07em;
}

.header-pulse {
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
  margin-top: 0.22rem;
  color: #57cbdc;
  font: 0.5rem/1 var(--font-mono);
  letter-spacing: 0.12em;
}

.header-pulse i {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 9px #2dd4bf;
  animation: pulse 2.2s infinite;
}

.wall-title {
  position: relative;
  display: grid;
  justify-items: center;
  gap: 0.24rem;
  text-align: center;
}

.title-kicker {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid rgb(0 220 246 / 17%);
  padding: 0.27rem 0.72rem;
  color: #67a9c7;
  background: rgb(0 220 246 / 4%);
  font: 0.52rem/1 var(--font-mono);
  letter-spacing: 0.24em;
}

.title-kicker i {
  width: 0.34rem;
  height: 0.34rem;
  border-radius: 50%;
  background: var(--wall-cyan);
  box-shadow: 0 0 12px var(--wall-cyan);
  animation: pulse 2s infinite;
}

.wall-title h1 {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  margin: 0;
  font: 700 clamp(1.85rem, 2.55vw, 2.7rem)/1 var(--font-display);
  letter-spacing: 0.14em;
}

.wall-title h1 span {
  color: #e1f9ff;
  text-shadow: 0 0 30px rgb(0 220 246 / 24%);
}

.wall-title h1 b {
  color: var(--wall-cyan);
  font-weight: 700;
  text-shadow: 0 0 25px rgb(0 220 246 / 55%);
}

.wall-title p {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin: 0;
  color: #7fa0ba;
  font: 0.56rem/1.2 var(--font-mono);
  letter-spacing: 0.1em;
}

.wall-title p i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--wall-teal);
  opacity: 0.85;
}

.header-statuses {
  display: flex;
  align-items: center;
  gap: 0.95rem;
}

.online-state,
.sync-state {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font: 0.52rem/1.15 var(--font-mono);
  letter-spacing: 0.1em;
  white-space: nowrap;
}

.online-state { color: #62e6c4; }
.sync-state { color: #7fa7c5; }

.online-state i {
  width: 0.35rem;
  height: 0.35rem;
  border-radius: 50%;
  background: #2dd4bf;
  box-shadow: 0 0 10px #2dd4bf;
}

.header-right button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid rgb(0 220 246 / 32%);
  border-radius: 2px;
  padding: 0.42rem 0.75rem;
  color: #a9f2fb;
  background: linear-gradient(135deg, rgb(0 220 246 / 9%), rgb(73 116 235 / 8%));
  font-size: 0.58rem;
  letter-spacing: 0.06em;
  cursor: pointer;
}

.header-right button:hover {
  border-color: var(--wall-cyan);
  background: linear-gradient(135deg, rgb(0 220 246 / 16%), rgb(73 116 235 / 12%));
}

.header-right button:disabled { opacity: 0.5; cursor: wait; }
.header-right button b { color: var(--wall-cyan); font-size: 0.82rem; }

/* ── KPI cards ─────────────────────────────────────────────────────────── */
.kpi-ribbon {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.68rem;
}

.kpi-card {
  --kpi-accent: var(--wall-cyan);
  --kpi-soft: rgb(0 220 246 / 10%);
  position: relative;
  display: grid;
  grid-template-columns: 3.2rem minmax(0, 1fr) clamp(3rem, 5vw, 5.4rem);
  grid-template-rows: auto auto 2px;
  align-items: center;
  gap: 0.3rem 0.85rem;
  overflow: hidden;
  min-height: 6.35rem;
  border: 1px solid var(--wall-line);
  padding: 0.8rem 0.95rem 0.65rem 1rem;
  background:
    radial-gradient(16rem 8rem at 100% 0, var(--kpi-soft), transparent 72%),
    linear-gradient(118deg, rgb(9 33 72 / 96%), rgb(4 14 43 / 98%));
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 5%), 0 14px 36px rgb(0 4 16 / 22%);
  clip-path: polygon(0 0, calc(100% - 0.9rem) 0, 100% 0.9rem, 100% 100%, 0.9rem 100%, 0 calc(100% - 0.9rem));
}

.kpi-card::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: linear-gradient(180deg, transparent, var(--kpi-accent), transparent);
  content: '';
  opacity: 0.95;
}

.kpi-card::after {
  position: absolute;
  top: 0;
  right: 0;
  width: 6.2rem;
  height: 6.2rem;
  background: radial-gradient(circle at 100% 0, var(--kpi-soft), transparent 68%);
  content: '';
  pointer-events: none;
}

.tone-cyan { --kpi-accent: #00dcf6; --kpi-soft: rgb(0 220 246 / 11%); }
.tone-blue { --kpi-accent: #4974eb; --kpi-soft: rgb(73 116 235 / 15%); }
.tone-amber { --kpi-accent: #f0b64a; --kpi-soft: rgb(204 134 15 / 14%); }
.tone-mint { --kpi-accent: #2ce6c2; --kpi-soft: rgb(44 230 194 / 12%); }
.tone-violet { --kpi-accent: #7f9cff; --kpi-soft: rgb(73 116 235 / 16%); }
.tone-steel { --kpi-accent: #16b8d9; --kpi-soft: rgb(22 184 217 / 13%); }

.kpi-glyph {
  display: grid;
  width: 3rem;
  height: 3rem;
  grid-row: 1 / 3;
  place-items: center;
  border: 1px solid var(--kpi-soft);
  color: var(--kpi-accent);
  background: var(--kpi-soft);
  box-shadow: inset 0 0 22px var(--kpi-soft);
  font: 1.45rem/1 var(--font-mono);
  text-shadow: 0 0 15px var(--kpi-accent);
}

.kpi-copy {
  display: grid;
  min-width: 0;
  grid-row: 1 / 3;
  gap: 0.2rem;
}

.kpi-copy header {
  display: grid;
  align-content: center;
  gap: 0.1rem;
  min-width: 0;
}

.kpi-copy header span {
  overflow: hidden;
  color: #d9edfa;
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-copy header small {
  overflow: hidden;
  color: #6f93b2;
  font: 0.5rem/1 var(--font-mono);
  letter-spacing: 0.06em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.kpi-value {
  display: flex;
  align-items: baseline;
  gap: 0.3rem;
  min-width: 0;
}

.kpi-value strong {
  color: #ffffff;
  font: 700 clamp(2rem, 2.35vw, 2.75rem)/1 var(--font-mono);
  letter-spacing: -0.04em;
  text-shadow: 0 0 22px var(--kpi-soft);
}

.kpi-value em {
  color: var(--kpi-accent);
  font: 0.6rem/1 var(--font-mono);
  font-style: normal;
}

.kpi-copy p {
  display: -webkit-box;
  overflow: hidden;
  margin: 0;
  color: #8babc6;
  font-size: 0.66rem;
  line-height: 1.35;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.kpi-spark {
  width: 100%;
  height: 2.9rem;
  grid-row: 1 / 3;
  overflow: visible;
  opacity: 0.9;
}

.kpi-spark polygon {
  fill: var(--kpi-accent);
  opacity: 0.1;
}

.kpi-spark polyline {
  fill: none;
  stroke: var(--kpi-accent);
  stroke-width: 1.6;
  filter: drop-shadow(0 0 4px var(--kpi-accent));
  opacity: 0.9;
  vector-effect: non-scaling-stroke;
}

.kpi-meter {
  height: 2px;
  grid-column: 1 / -1;
  overflow: hidden;
  margin-top: 0.14rem;
  background: rgb(73 116 235 / 15%);
}

.kpi-meter b {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, transparent, var(--kpi-accent));
  box-shadow: 0 0 10px var(--kpi-accent);
}

/* ── Shared panels ─────────────────────────────────────────────────────── */
.command-grid {
  display: grid;
  height: clamp(25rem, 45.6vh, 30.75rem);
  grid-template-columns: minmax(15rem, 0.82fr) minmax(32rem, 1.95fr) minmax(15rem, 0.82fr);
  gap: 0.62rem;
  min-width: 0;
}

.side-column {
  display: grid;
  min-width: 0;
  height: 100%;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 0.62rem;
}

.wall-panel,
.map-panel {
  position: relative;
  min-width: 0;
  border: 1px solid var(--wall-line);
  background: var(--wall-panel);
  box-shadow: inset 0 0 34px rgb(17 86 133 / 7%), 0 12px 32px rgb(0 4 16 / 16%);
}

.wall-panel::before,
.map-panel::before {
  position: absolute;
  z-index: 2;
  top: -1px;
  left: -1px;
  width: 0.9rem;
  height: 0.9rem;
  border-top: 1px solid var(--wall-cyan);
  border-left: 1px solid var(--wall-cyan);
  content: '';
  pointer-events: none;
}

.wall-panel::after,
.map-panel::after {
  position: absolute;
  z-index: 2;
  right: -1px;
  bottom: -1px;
  width: 0.9rem;
  height: 0.9rem;
  border-right: 1px solid rgb(73 116 235 / 80%);
  border-bottom: 1px solid rgb(73 116 235 / 80%);
  content: '';
  pointer-events: none;
}

.panel-chart {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  min-height: 2.7rem;
  border-bottom: 1px solid var(--wall-line-soft);
  padding: 0.58rem 0.8rem 0.52rem 0.9rem;
  background: linear-gradient(90deg, rgb(10 40 83 / 44%), transparent 80%);
}

.panel-head div { display: grid; gap: 0.14rem; }

.panel-head span {
  color: #5d9fc4;
  font: 0.56rem/1 var(--font-mono);
  letter-spacing: 0.16em;
}

.panel-head h2 {
  margin: 0;
  color: #d6eefa;
  font-size: 0.9rem;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.panel-head small {
  color: #5f86a8;
  font: 0.54rem/1.2 var(--font-mono);
  text-align: right;
  white-space: nowrap;
}

.chart-body {
  width: 100%;
  height: 0;
  min-height: 0;
  flex: 1 1 0;
  padding: 0.12rem 0.2rem 0;
}

.chart-wide {
  height: 0;
  min-height: 0;
  flex: 1 1 0;
}

/* ── 3D map ────────────────────────────────────────────────────────────── */
.map-panel {
  display: flex;
  min-height: 0;
  height: 100%;
  flex-direction: column;
  overflow: hidden;
  background:
    radial-gradient(58% 68% at 50% 46%, rgb(0 125 185 / 18%), transparent 72%),
    radial-gradient(48% 52% at 50% 100%, rgb(73 116 235 / 9%), transparent 76%),
    var(--wall-panel-2);
}

.map-panel::before { width: 1.2rem; height: 1.2rem; }
.map-panel::after { width: 1.2rem; height: 1.2rem; }

.map-head {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  min-height: 3.05rem;
  border-bottom: 1px solid rgb(0 220 246 / 20%);
  padding: 0.6rem 0.9rem;
  background: linear-gradient(90deg, rgb(5 24 59 / 92%), rgb(4 14 43 / 34%));
}

.map-head > div:first-child { display: grid; gap: 0.12rem; }

.map-head span,
.map-head small {
  color: #5d9fc4;
  font: 0.56rem/1 var(--font-mono);
  letter-spacing: 0.14em;
}

.map-head h2 {
  margin: 0;
  color: #dff3fb;
  font-size: 0.98rem;
  font-weight: 600;
  letter-spacing: 0.05em;
}

.map-head-right {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.twin-chip {
  border: 1px solid rgb(73 116 235 / 26%);
  padding: 0.28rem 0.46rem;
  color: #8eb6d4 !important;
  background: rgb(73 116 235 / 7%);
  font-size: 0.46rem !important;
  letter-spacing: 0.08em !important;
}

.twin-state {
  display: inline-flex;
  align-items: center;
  gap: 0.38rem;
  border: 1px solid rgb(0 220 246 / 26%);
  padding: 0.28rem 0.46rem;
  color: #78e5f1 !important;
  background: rgb(0 220 246 / 6%);
  font-size: 0.46rem !important;
  letter-spacing: 0.08em !important;
}

.twin-state i {
  width: 0.32rem;
  height: 0.32rem;
  border-radius: 50%;
  background: var(--wall-cyan);
  box-shadow: 0 0 9px var(--wall-cyan);
}

.map-host {
  position: absolute;
  z-index: 1;
  inset: 3.05rem 0 0;
  cursor: crosshair;
}

.map-host :deep(canvas) { display: block; width: 100%; height: 100%; }

.map-scanline {
  position: absolute;
  z-index: 2;
  top: 3.05rem;
  right: 0;
  left: 0;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgb(0 220 246 / 50%), transparent);
  box-shadow: 0 0 20px rgb(0 220 246 / 22%);
  animation: scan-y 4.2s linear infinite;
  pointer-events: none;
}

.map-overlay { position: absolute; z-index: 3; pointer-events: none; }

.map-top-left {
  top: 3.5rem;
  left: 1rem;
  display: grid;
  gap: 0.2rem;
  border-left: 2px solid var(--wall-cyan);
  padding: 0.4rem 0.65rem;
  background: linear-gradient(90deg, rgb(3 15 42 / 72%), transparent);
}

.map-top-left span,
.map-top-right > span {
  color: #5d9fc4;
  font: 0.56rem/1 var(--font-mono);
  letter-spacing: 0.12em;
}

.map-top-left strong {
  color: #8fe3f0;
  font-size: 0.78rem;
  font-weight: 600;
}

.map-top-left small { color: #5f86a6; font: 0.52rem/1 var(--font-mono); }

.map-top-right {
  top: 3.5rem;
  right: 0.95rem;
  width: 12.2rem;
  padding: 0.4rem 0.6rem;
  background: linear-gradient(270deg, rgb(3 15 42 / 70%), transparent);
}

.map-top-right ol {
  display: grid;
  gap: 0.32rem;
  margin: 0.45rem 0 0;
  padding: 0;
  list-style: none;
}

.map-top-right li {
  display: grid;
  grid-template-columns: 1.4rem minmax(0, 1fr) 2.5rem;
  align-items: center;
  gap: 0.4rem;
  color: #9dbfd5;
  font-size: 0.64rem;
}

.map-top-right li b { color: #4f86a8; font: 0.52rem/1 var(--font-mono); }
.map-top-right li span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.map-top-right li em {
  color: #6ee5ef;
  font: 0.62rem/1 var(--font-mono);
  font-style: normal;
  text-align: right;
}

.map-legend {
  position: absolute;
  z-index: 3;
  bottom: 3.65rem;
  left: 50%;
  display: flex;
  max-width: 70%;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.28rem 0.7rem;
  transform: translateX(-50%);
  pointer-events: none;
}

.map-legend span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: #88aec7;
  font: 0.54rem/1 var(--font-mono);
  white-space: nowrap;
}

.map-legend i {
  width: 0.38rem;
  height: 0.38rem;
  background: var(--legend-color);
  box-shadow: 0 0 7px var(--legend-color);
}

.map-footer {
  position: absolute;
  z-index: 3;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  min-height: 3rem;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  border-top: 1px solid rgb(0 220 246 / 18%);
  background: linear-gradient(180deg, rgb(5 20 54 / 88%), rgb(3 12 37 / 96%));
  backdrop-filter: blur(12px);
}

.map-footer span {
  display: grid;
  gap: 0.14rem;
  border-right: 1px solid var(--wall-line-soft);
  padding: 0.46rem 0.5rem;
}

.map-footer span:last-child { border-right: 0; }

.map-footer small {
  color: #6288a6;
  font: 0.5rem/1 var(--font-mono);
  letter-spacing: 0.06em;
}

.map-footer b {
  color: #91e2f0;
  font: 0.66rem/1 var(--font-mono);
  font-weight: 500;
}

/* ── Lower data grid ───────────────────────────────────────────────────── */
.lower-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 0.62rem;
}

.span-3 { grid-column: span 3; }
.span-4 { grid-column: span 4; }
.span-5 { grid-column: span 5; }

.lower-grid .panel-chart,
.table-panel {
  display: flex;
  height: 13.6rem;
  min-height: 13.6rem;
  flex-direction: column;
  overflow: hidden;
}

.table-matrix {
  display: grid;
  max-height: none;
  flex: 1;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(7, minmax(0, 1fr));
  grid-auto-flow: column;
  align-content: stretch;
  gap: 0.1rem 0.75rem;
  overflow: auto;
  padding: 0.42rem 0.7rem 0.5rem;
}

.matrix-row {
  display: grid;
  grid-template-columns: 4.9rem minmax(2.4rem, 1fr) 2.8rem;
  align-items: center;
  gap: 0.45rem;
  border-bottom: 1px dashed var(--wall-line-soft);
  padding: 0.12rem 0.3rem;
}

.matrix-row:hover { background: rgb(0 220 246 / 4%); }
.matrix-row span { color: #b5cede; font-size: 0.64rem; }
.matrix-row i { height: 3px; overflow: hidden; background: rgb(73 116 235 / 14%); }

.matrix-row i b {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--wall-blue), var(--wall-cyan));
  box-shadow: 0 0 8px rgb(0 220 246 / 42%);
}

.matrix-row strong {
  color: #d4f0fa;
  font: 0.64rem/1 var(--font-mono);
  text-align: right;
}

.matrix-row em { display: none; }

.matrix-empty {
  margin: 1rem;
  color: var(--wall-muted);
  font-size: 0.6rem;
  text-align: center;
}

/* ── Insight strip ─────────────────────────────────────────────────────── */
.insight-strip {
  display: grid;
  min-width: 0;
  grid-template-columns: minmax(18rem, 0.75fr) minmax(32rem, 1.55fr);
  gap: 0.62rem;
  min-height: 5.2rem;
}

.gap-panel,
.audit-panel {
  min-height: 5.2rem;
  overflow: hidden;
}

.insight-strip .panel-head {
  min-height: 2.4rem;
  padding: 0.4rem 0.7rem 0.36rem 0.78rem;
}

.gap-list {
  display: grid;
  max-height: 2.5rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.05rem 0.8rem;
  margin: 0;
  overflow: auto;
  padding: 0.36rem 0.6rem 0.4rem;
  list-style: none;
}

.gap-list li {
  display: grid;
  grid-template-columns: 1.5rem minmax(0, 1fr) 2.2rem;
  align-items: center;
  gap: 0.38rem;
  border-bottom: 1px dashed var(--wall-line-soft);
  padding: 0.08rem 0.22rem;
}

.gap-list li b { color: #4d7c9e; font: 0.52rem/1 var(--font-mono); }

.gap-list li span {
  overflow: hidden;
  color: #a9c8dc;
  font-size: 0.63rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gap-list li em {
  color: #f0b64a;
  font: 0.6rem/1 var(--font-mono);
  font-style: normal;
  text-align: right;
}

.gap-list .empty-row span { color: #5f86a6; }

.audit-list {
  display: grid;
  max-height: 2.5rem;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.03rem 0.9rem;
  margin: 0;
  overflow: auto;
  padding: 0.34rem 0.7rem 0.4rem;
  list-style: none;
}

.audit-list li {
  display: grid;
  grid-template-columns: 6.6rem minmax(0, 1fr);
  gap: 0.06rem 0.45rem;
  border-bottom: 1px dashed var(--wall-line-soft);
  padding: 0.08rem 0;
}

.audit-list time {
  grid-row: span 2;
  color: #628aa8;
  font: 0.52rem/1.25 var(--font-mono);
}

.audit-list li strong {
  overflow: hidden;
  color: #9ecfe0;
  font-size: 0.62rem;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audit-list li span {
  overflow: hidden;
  color: #7fa2bc;
  font-size: 0.56rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.audit-empty { grid-column: 1 / -1; }

/* ── Motion ────────────────────────────────────────────────────────────── */
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgb(0 220 246 / 48%); }
  70% { box-shadow: 0 0 0 7px rgb(0 220 246 / 0%); }
  100% { box-shadow: 0 0 0 0 rgb(0 220 246 / 0%); }
}

@keyframes scan-y {
  0% { top: 3.05rem; opacity: 0; }
  12% { opacity: 0.65; }
  88% { opacity: 0.65; }
  100% { top: calc(100% - 3rem); opacity: 0; }
}

/* ── Responsive ────────────────────────────────────────────────────────── */
@media (max-width: 1500px) {
  .audit-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }

  .command-grid {
    grid-template-columns: minmax(13.2rem, 0.74fr) minmax(25rem, 1.65fr) minmax(13.2rem, 0.74fr);
  }

  .kpi-card {
    grid-template-columns: 2.8rem minmax(0, 1fr) minmax(2.8rem, 4.2rem);
    gap: 0.24rem 0.55rem;
  }

  .kpi-glyph { width: 2.65rem; height: 2.65rem; }
  .kpi-copy header small { display: none; }
}

@media (max-width: 1360px) {
  .kpi-ribbon { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .command-grid { height: auto; grid-template-columns: minmax(13rem, 1fr) minmax(13rem, 1fr); }
  .map-panel { height: 28rem; min-height: 28rem; grid-column: 1 / -1; grid-row: 1; }
  .side-column { height: 22rem; }
  .side-column:first-child { grid-column: 1; grid-row: 2; }
  .side-column:last-child { grid-column: 2; grid-row: 2; }
  .span-3, .span-4, .span-5 { grid-column: span 12; }
  .lower-grid .wall-panel, .lower-grid .table-panel { height: 12rem; min-height: 12rem; }
  .insight-strip { grid-template-columns: 1fr; }
  .gap-panel, .audit-panel { min-height: 6rem; }
  .gap-list, .audit-list { max-height: 3.2rem; }
}

@media (max-width: 1180px) {
  .wall-header { grid-template-columns: 1fr auto; }
  .wall-title { grid-column: 1 / -1; grid-row: 1; }
  .header-left { grid-row: 2; }
  .header-right { grid-row: 2; }
}

@media (max-width: 760px) {
  .kpi-ribbon { grid-template-columns: 1fr 1fr; }
  .wall-title h1 { font-size: 1.35rem; letter-spacing: 0.08em; }
  .wall-title h1 span, .wall-title h1 b { display: inline; }
  .wall-title p { flex-wrap: wrap; justify-content: center; }
  .header-left strong { font-size: 1.15rem; }
  .command-grid { display: flex; flex-direction: column; }
  .map-panel { order: -1; min-height: 25rem; }
  .map-top-right { display: none; }
  .map-legend { max-width: 84%; }
  .audit-list { grid-template-columns: 1fr; }
  .audit-empty { grid-column: 1; }
}

@media (max-width: 540px) {
  .wall-header { grid-template-columns: 1fr; }
  .header-left { display: none; }
  .header-right { justify-items: start; }
  .header-statuses { flex-wrap: wrap; }
  .kpi-ribbon { grid-template-columns: 1fr; }
  .kpi-card { grid-template-columns: 2.8rem minmax(0, 1fr) 3.6rem; }
  .map-footer { grid-template-columns: repeat(3, 1fr); }
  .map-footer span:nth-child(n + 4) { display: none; }
  .map-top-left { display: none; }
  .map-legend { bottom: 3.5rem; }
}

@media (max-height: 860px) and (min-width: 1361px) {
  .data-wall { gap: 0.38rem; }
  .wall-header { min-height: 3.5rem; padding-top: 0.05rem; padding-bottom: 0.25rem; }
  .wall-title h1 { font-size: 1.5rem; }
  .title-kicker, .wall-title p, .header-left .micro-label, .header-statuses { display: none; }
  .title-kicker { padding: 0.2rem 0.55rem; font-size: 0.46rem; }
  .header-left strong { font-size: 1.2rem; }
  .kpi-card { min-height: 5rem; padding: 0.48rem 0.65rem 0.4rem 0.72rem; }
  .kpi-value strong { font-size: 1.72rem; }
  .kpi-copy header span { font-size: 0.72rem; }
  .kpi-copy p { font-size: 0.58rem; }
  .kpi-spark { height: 1.95rem; }
  .kpi-glyph { width: 2.55rem; height: 2.55rem; font-size: 1.2rem; }
  .command-grid { height: 22.5rem; }
  .panel-head { min-height: 2.25rem; padding: 0.38rem 0.65rem 0.34rem 0.72rem; }
  .panel-head h2 { font-size: 0.78rem; }
  .panel-head span { font-size: 0.46rem; }
  .panel-head small { font-size: 0.46rem; }
  .map-head { min-height: 2.45rem; padding: 0.42rem 0.7rem; }
  .map-head h2 { font-size: 0.8rem; }
  .map-host { inset: 2.45rem 0 0; }
  .map-top-left { top: 2.9rem; }
  .map-top-right { top: 2.9rem; width: 10.4rem; }
  .map-top-right li { font-size: 0.55rem; }
  .lower-grid .panel-chart, .table-panel { height: 9.4rem; min-height: 9.4rem; }
  .gap-panel, .audit-panel { min-height: 3.9rem; }
  .insight-strip { min-height: 3.9rem; }
  .insight-strip .panel-head { min-height: 2.1rem; padding: 0.28rem 0.6rem 0.24rem 0.68rem; }
  .insight-strip .panel-head h2 { font-size: 0.68rem; }
  .gap-list, .audit-list { max-height: 1.55rem; }
}

@media (prefers-reduced-motion: reduce) {
  .title-kicker i,
  .header-pulse i,
  .map-scanline { animation: none; }
}

.data-wall::before { bottom: 0; }

/* Readability and interaction refinement */
.micro-label { font-size: 0.66rem; color: #79a4c2; }
.header-left small { color: #8aa9c2; font-size: 0.66rem; }
.header-pulse { color: #70d7e6; font-size: 0.57rem; }
.title-kicker { border-radius: 999px; padding: 0.32rem 0.8rem; font-size: 0.6rem; color: #80bdd4; }
.wall-title p { color: #8db6cd; font-size: 0.64rem; }
.online-state, .sync-state { font-size: 0.57rem; }
.header-right button {
  min-height: 2.35rem;
  border-radius: 0.64rem;
  padding: 0.4rem 0.7rem;
  font-size: 0.66rem;
}
.header-right button b { display: inline-flex; align-items: center; }
.header-right button b.spinning { animation: icon-spin 900ms linear infinite; }
.kpi-glyph { border-radius: 0.72rem; text-shadow: none; }
.kpi-copy header span { font-size: 0.86rem; }
.kpi-copy header small { color: #7fa3c0; font-size: 0.5rem; }
.kpi-value em { font-size: 0.66rem; }
.kpi-copy p { color: #9ab9cf; font-size: 0.7rem; }
.panel-head span, .map-head span, .map-top-left span, .map-top-right > span {
  color: #75acc8;
  font-size: 0.56rem;
}
.panel-head h2, .map-head h2 { font-size: 0.98rem; }
.panel-head small, .map-head small { color: #678eaa; font-size: 0.54rem; }
.map-top-left strong { font-size: 0.74rem; }
.map-top-left small { color: #79a0b9; font-size: 0.58rem; }
.map-top-right li { font-size: 0.66rem; }
.map-top-right li b { color: #6b9ab8; font-size: 0.56rem; }
.map-footer small { font-size: 0.5rem; }
.map-footer b { font-size: 0.66rem; }
.matrix-row span { font-size: 0.7rem; }
.matrix-row strong { font-size: 0.68rem; }
.gap-list li { font-size: 0.67rem; }
.gap-list li b { color: #6a9ab9; font-size: 0.56rem; }
.audit-list time { font-size: 0.54rem; }
.audit-list li strong { font-size: 0.68rem; }
.audit-list li span { font-size: 0.62rem; }
@keyframes icon-spin { to { transform: rotate(360deg); } }

/* ── Height-aware one-screen layout ───────────────────────────────────────
   The cockpit must fill exactly one viewport on every desktop height.
   Header, KPI ribbon, lower grid and insight strip keep their intrinsic
   sizes; the command grid absorbs the remaining height instead of the
   page growing a 70px scrollbar on 1440x900 laptops. */
@media (min-width: 1361px) {
  :global(.admin-shell.overview-mode .admin-main) {
    display: flex;
    height: 100vh;
    min-height: 0;
    flex-direction: column;
  }

  .data-wall {
    flex: 1;
    min-height: 0;
    grid-template-rows: auto minmax(16rem, 1fr) auto auto;
  }

  .command-grid {
    height: 100%;
    min-height: 0;
  }
}

</style>
