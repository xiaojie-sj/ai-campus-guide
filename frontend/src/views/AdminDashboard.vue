<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, authApi, ApiError } from '../api/client'
import AdminOverview from '../components/admin/AdminOverview.vue'
import AppIcon from '../components/AppIcon.vue'
import type {
  CameraPreset,
  ChatLog,
  DashboardResponse,
  FaqItem,
  KnowledgeDocumentView,
  PointView,
  StatsResponse,
  SystemConfig,
  TourRoute,
} from '../types'

type TabKey =
  | 'overview'
  | 'points'
  | 'knowledge'
  | 'faq'
  | 'tours'
  | 'cameras'
  | 'logs'
  | 'configs'

const router = useRouter()
const user = ref(authApi.session())
const tab = ref<TabKey>('overview')
const banner = ref('')
const error = ref('')
const busy = ref(false)

const points = ref<PointView[]>([])
const knowledge = ref<KnowledgeDocumentView[]>([])
const faqs = ref<FaqItem[]>([])
const tours = ref<TourRoute[]>([])
const cameras = ref<CameraPreset[]>([])
const configs = ref<SystemConfig[]>([])
const logs = ref<ChatLog[]>([])
const stats = ref<StatsResponse>()
const dashboard = ref<DashboardResponse>()

const pointForm = reactive({
  id: 0,
  name: '',
  aliases: '',
  category: 'TEACHING',
  description: '',
  x: 0,
  z: 0,
  buildingHeight: 7,
  colorHex: '#16b8d9',
  modelKey: 'BLOCK',
  roadNodeId: 1,
  status: 'DRAFT',
  verificationStatus: 'PENDING',
  sourceTitle: '',
  sourceUrl: '',
  sortOrder: 100,
})

const faqForm = reactive({
  id: 0,
  question: '',
  aliases: '',
  answer: '',
  category: '校园信息',
  sourceTitle: '',
  sourceUrl: '',
  priority: 50,
  status: 'DRAFT',
  effectiveFrom: '',
  effectiveTo: '',
})

const cameraForm = reactive({
  id: 0,
  key: '',
  name: '',
  pointId: 0,
  x: 0,
  y: 12,
  z: 20,
  targetX: 0,
  targetY: 3,
  targetZ: 0,
  publicVisible: true,
  status: 'DRAFT',
  sortOrder: 100,
})

const routeForm = reactive({
  id: 0,
  key: '',
  name: '',
  summary: '',
  audience: '',
  estimatedMinutes: 10,
  status: 'DRAFT',
  verificationStatus: 'PENDING',
  effectiveFrom: '',
  effectiveTo: '',
  sortOrder: 100,
  stops: [] as Array<{ pointId: number; narration: string; transitionType: string }>,
})

const uploadForm = reactive({
  title: '',
  sourceTitle: '',
  sourceUrl: '',
  effectiveYear: '2026',
  effectiveFrom: '',
  effectiveTo: '',
  category: '校园资料',
})
const uploadFile = ref<File>()

const navItems: Array<{ key: TabKey; label: string; index: string; icon: string }> = [
  { key: 'overview', label: '运行概览', index: '01', icon: 'dashboard' },
  { key: 'points', label: '校园点位', index: '02', icon: 'pin' },
  { key: 'knowledge', label: '知识库', index: '03', icon: 'book' },
  { key: 'faq', label: '高频问答', index: '04', icon: 'chat' },
  { key: 'tours', label: '精品路线', index: '05', icon: 'route' },
  { key: 'cameras', label: '预置机位', index: '06', icon: 'camera' },
  { key: 'logs', label: '对话日志', index: '07', icon: 'list' },
  { key: 'configs', label: '系统参数', index: '08', icon: 'settings' },
]

const tabMeta: Record<TabKey, { code: string; desc: string }> = {
  overview: { code: 'COMMAND CENTER', desc: '三维数据驾驶舱 · 对话、知识、资产与审计实时联动' },
  points: { code: 'SPATIAL CATALOG', desc: '维护点位坐标、分类、核验与发布状态 · 仅已发布且资料有效的点位会进入公众端' },
  knowledge: { code: 'PRIVATE RAG', desc: '文档上传后异步解析、清洗、分片与向量化 · 重建成功后才替换现有片段' },
  faq: { code: 'ANSWER LAYER', desc: 'FAQ 优先于一般向量检索命中 · 未审核或已过期内容不会进入回答链路' },
  tours: { code: 'GUIDED JOURNEY', desc: '按站点顺序编排镜头、数字人讲解与过渡方式' },
  cameras: { code: 'CAMERA RIG', desc: '维护三维场景预置机位、目标点与公众可见性' },
  logs: { code: 'CONVERSATION LOG', desc: '最近 30 条问答意图、置信度、回答状态与链路时延 · 原始录音不保存' },
  configs: { code: 'SYSTEM TUNING', desc: '数字人语音、漫游速度、镜头飞行与高亮参数 · 修改会写入审计日志' },
}

const allowedTabs = computed(() => {
  const role = user.value?.role
  if (role === 'SYSTEM_ADMIN') return navItems
  if (role === 'DATA_ANALYST') return navItems.filter((item) => ['overview', 'logs'].includes(item.key))
  // Raw chat logs and system configs are least-privilege areas. Reviewers and
  // content editors only get the aggregated overview plus content tabs.
  return navItems.filter((item) => !['logs', 'configs'].includes(item.key))
})

const DISPLAY_LABELS: Record<string, string> = {
  DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下线', ARCHIVED: '已归档',
  PENDING: '待核验', DEMO_PLACEHOLDER: '演示占位', MAP_2_DIGITIZED: '地图数字化', VERIFIED: '已核验', VERIFIED_NAME: '名称已核验', VERIFIED_FULL: '完整核验',
  UPLOADED: '待解析', PROCESSING: '解析中', READY: '待发布', FAILED: '处理失败',
  GATE: '校门', LIBRARY: '图书馆', TEACHING: '教学楼', LAB: '实验楼', DORM: '宿舍', CANTEEN: '食堂', SPORTS: '运动场', ADMIN: '行政楼', SERVICE: '服务设施', OTHER: '其他', PLAZA: '广场',
  ANSWERED: '已回答', NO_EVIDENCE: '无依据', OUT_OF_SCOPE: '超出范围', NEED_CLARIFY: '需澄清', UNKNOWN: '未知', ERROR: '异常',
  BUILDING_INTRO: '建筑讲解', ROUTE: '路线导航', INFO: '信息咨询', SCENE_CONTROL: '场景控制', CLARIFY: '意图澄清',
  SYSTEM_ADMIN: '系统管理员', CONTENT_ADMIN: '内容管理员', REVIEWER: '内容审核员', DATA_ANALYST: '数据分析师',
}

function displayLabel(value?: string | null) {
  if (!value) return '—'
  return DISPLAY_LABELS[value] ?? value
}

const canEditContent = computed(() => ['SYSTEM_ADMIN', 'CONTENT_ADMIN'].includes(user.value?.role ?? ''))

const pageStats = computed<Array<{ label: string; value: string }>>(() => {
  const countByStatus = (list: Array<{ status?: string }>, status: string) =>
    list.filter((item) => item.status === status).length

  switch (tab.value) {
    case 'overview':
      return [
        { label: '业务数据表', value: String(dashboard.value?.tableStats.length ?? 0) },
        { label: '三维点位', value: String(dashboard.value?.assets.points ?? 0) },
        { label: '路网节点', value: String(dashboard.value?.assets.roadNodes ?? 0) },
      ]
    case 'points':
      return [
        { label: '全部点位', value: String(points.value.length) },
        { label: '公众可见', value: String(countByStatus(points.value, 'PUBLISHED')) },
        { label: '完整核验', value: String(countByStatus(points.value, 'VERIFIED_FULL')) },
      ]
    case 'knowledge': {
      const chunks = knowledge.value.reduce((sum, doc) => sum + (doc.chunkCount ?? 0), 0)
      return [
        { label: '知识文档', value: String(knowledge.value.length) },
        { label: '已发布', value: String(countByStatus(knowledge.value, 'PUBLISHED')) },
        { label: '向量片段', value: chunks.toLocaleString('zh-CN') },
      ]
    }
    case 'faq':
      return [
        { label: '问答条目', value: String(faqs.value.length) },
        { label: '已发布', value: String(countByStatus(faqs.value, 'PUBLISHED')) },
        { label: '草稿', value: String(countByStatus(faqs.value, 'DRAFT')) },
      ]
    case 'tours':
      return [
        { label: '精品路线', value: String(tours.value.length) },
        { label: '已发布', value: String(countByStatus(tours.value, 'PUBLISHED')) },
        { label: '总时长', value: `${tours.value.reduce((sum, route) => sum + (route.estimatedMinutes ?? 0), 0)} min` },
      ]
    case 'cameras':
      return [
        { label: '预置机位', value: String(cameras.value.length) },
        { label: '公众可见', value: String(cameras.value.filter((item) => item.publicVisible).length) },
        { label: '已发布', value: String(countByStatus(cameras.value, 'PUBLISHED')) },
      ]
    case 'logs': {
      const answered = logs.value.filter((item) => item.answerStatus === 'ANSWERED').length
      const average = logs.value.length
        ? Math.round(logs.value.reduce((sum, item) => sum + (item.latencyMs ?? 0), 0) / logs.value.length)
        : 0
      return [
        { label: '本次加载', value: String(logs.value.length) },
        { label: '已回答', value: String(answered) },
        { label: '平均时延', value: `${average} ms` },
      ]
    }
    case 'configs':
      return [
        { label: '参数项', value: String(configs.value.length) },
        { label: '数值型', value: String(configs.value.filter((item) => item.valueType === 'NUMBER').length) },
        { label: '权限域', value: 'SYSTEM' },
      ]
    default:
      return []
  }
})

watch(allowedTabs, (tabs) => {
  if (!tabs.some((item) => item.key === tab.value)) {
    tab.value = tabs[0]?.key ?? 'overview'
  }
}, { immediate: true })

onMounted(async () => {
  if (!user.value) {
    await router.replace('/admin/login')
    return
  }
  await loadAll()
})

async function loadAll() {
  if (!user.value) return
  busy.value = true
  error.value = ''
  const role = user.value?.role
  try {
    const [statsData, dashboardData] = await Promise.all([
      adminApi.stats(),
      adminApi.dashboard(),
    ])
    stats.value = statsData
    dashboard.value = dashboardData
    if (role === 'DATA_ANALYST' || role === 'SYSTEM_ADMIN') {
      logs.value = (await adminApi.logs()).records
    }
    if (role !== 'DATA_ANALYST') {
      ;[points.value, knowledge.value, faqs.value, tours.value, cameras.value] = await Promise.all([
        adminApi.points(),
        adminApi.knowledge(),
        adminApi.faqs(),
        adminApi.tours(),
        adminApi.cameras(),
      ])
    }
    if (role === 'SYSTEM_ADMIN') {
      configs.value = await adminApi.configs()
    }
  } catch (exception) {
    handleError(exception)
  } finally {
    busy.value = false
  }
}

async function switchTab(next: TabKey) {
  if (!allowedTabs.value.some((item) => item.key === next)) {
    return
  }
  tab.value = next
  banner.value = ''
  error.value = ''
  if (next === 'overview') {
    try {
      stats.value = await adminApi.stats()
    } catch (exception) {
      handleError(exception)
    }
  }
  if (next === 'logs') {
    try {
      logs.value = (await adminApi.logs()).records
    } catch (exception) {
      handleError(exception)
    }
  }
}

function editPoint(point?: PointView) {
  if (point) {
    Object.assign(pointForm, {
      ...point,
      aliases: point.aliases.join('，'),
      buildingHeight: point.buildingHeight ?? 7,
      colorHex: point.colorHex ?? '#16b8d9',
    })
  } else {
    Object.assign(pointForm, {
      id: 0,
      name: '',
      aliases: '',
      category: 'TEACHING',
      description: '',
      x: 0,
      z: 0,
      buildingHeight: 7,
      colorHex: '#16b8d9',
      modelKey: 'BLOCK',
      roadNodeId: 1,
      status: 'DRAFT',
      verificationStatus: 'PENDING',
      sourceTitle: '',
      sourceUrl: '',
      sortOrder: 100,
    })
  }
}

async function savePoint() {
  const payload = { ...pointForm, aliases: pointForm.aliases }
  try {
    busy.value = true
    if (pointForm.id) await adminApi.updatePoint(pointForm.id, payload as never)
    else await adminApi.createPoint(payload as never)
    points.value = await adminApi.points()
    banner.value = '点位已保存。公众端只会读取已发布点位。'
  } catch (exception) {
    handleError(exception)
  } finally {
    busy.value = false
  }
}

async function removePoint(point: PointView) {
  if (!window.confirm(`确定删除点位“${point.name}”？被路线引用的点位会被阻止删除。`)) return
  try {
    await adminApi.deletePoint(point.id)
    points.value = await adminApi.points()
    banner.value = '点位已删除。'
  } catch (exception) {
    handleError(exception)
  }
}

function editFaq(item?: FaqItem) {
  if (item) {
    Object.assign(faqForm, {
      ...item,
      aliases: item.aliases ?? '',
      effectiveFrom: item.effectiveFrom ?? '',
      effectiveTo: item.effectiveTo ?? '',
    })
  } else {
    Object.assign(faqForm, {
      id: 0,
      question: '',
      aliases: '',
      answer: '',
      category: '校园信息',
      sourceTitle: '',
      sourceUrl: '',
      priority: 50,
      status: 'DRAFT',
      effectiveFrom: '',
      effectiveTo: '',
    })
  }
}

async function saveFaq() {
  try {
    const payload = {
      ...faqForm,
      effectiveFrom: faqForm.effectiveFrom || null,
      effectiveTo: faqForm.effectiveTo || null,
    }
    if (faqForm.id) await adminApi.updateFaq(faqForm.id, payload as never)
    else await adminApi.createFaq(payload as never)
    faqs.value = await adminApi.faqs()
    banner.value = 'FAQ 已保存。只有已发布且未过期的问答会参与优先匹配。'
  } catch (exception) {
    handleError(exception)
  }
}

async function removeFaq(item: FaqItem) {
  try {
    await adminApi.deleteFaq(item.id)
    faqs.value = await adminApi.faqs()
    banner.value = 'FAQ 已删除。'
  } catch (exception) {
    handleError(exception)
  }
}

async function uploadKnowledge() {
  if (!uploadFile.value) {
    error.value = '请选择 PDF、DOC、DOCX 或 TXT 文件。'
    return
  }
  const form = new FormData()
  form.append('file', uploadFile.value)
  form.append('title', uploadForm.title || uploadFile.value.name)
  for (const key of ['sourceTitle', 'sourceUrl', 'effectiveYear', 'category'] as const) {
    if (uploadForm[key]) form.append(key, uploadForm[key])
  }
  if (uploadForm.effectiveFrom) form.append('effectiveFrom', uploadForm.effectiveFrom)
  if (uploadForm.effectiveTo) form.append('effectiveTo', uploadForm.effectiveTo)
  try {
    busy.value = true
    await adminApi.uploadKnowledge(form)
    knowledge.value = await adminApi.knowledge()
    uploadFile.value = undefined
    uploadForm.title = ''
    banner.value = '文档已上传并开始异步解析、分片和向量化。处理完成前不会参与公众回答。'
  } catch (exception) {
    handleError(exception)
  } finally {
    busy.value = false
  }
}

async function toggleKnowledge(doc: KnowledgeDocumentView) {
  try {
    await adminApi.publishKnowledge(doc.id, doc.status !== 'PUBLISHED')
    knowledge.value = await adminApi.knowledge()
    banner.value = doc.status === 'PUBLISHED' ? '文档已撤回发布。' : '文档已发布到私有 RAG。'
  } catch (exception) {
    handleError(exception)
  }
}

async function reindexKnowledge(doc: KnowledgeDocumentView) {
  try {
    await adminApi.reindexKnowledge(doc.id)
    knowledge.value = await adminApi.knowledge()
    banner.value = '重新向量化任务已排队。处理失败时不会替换已有有效索引。'
  } catch (exception) {
    handleError(exception)
  }
}

async function removeKnowledge(doc: KnowledgeDocumentView) {
  if (!window.confirm(`归档文档“${doc.title}”？`)) return
  try {
    await adminApi.deleteKnowledge(doc.id)
    knowledge.value = await adminApi.knowledge()
    banner.value = '文档已归档，将不再参与新的回答。'
  } catch (exception) {
    handleError(exception)
  }
}

function editTour(item?: TourRoute) {
  if (item) {
    Object.assign(routeForm, {
      ...item,
      summary: item.summary ?? '',
      audience: item.audience ?? '',
      effectiveFrom: item.effectiveFrom ?? '',
      effectiveTo: item.effectiveTo ?? '',
      stops: [],
    })
    void adminApi.stops(item.id).then((items) => {
      routeForm.stops = items.map((stop) => ({
        pointId: stop.pointId,
        narration: stop.narration ?? '',
        transitionType: stop.transitionType ?? 'FLY',
      }))
    })
  } else {
    Object.assign(routeForm, {
      id: 0,
      key: '',
      name: '',
      summary: '',
      audience: '',
      estimatedMinutes: 10,
      status: 'DRAFT',
      verificationStatus: 'PENDING',
      effectiveFrom: '',
      effectiveTo: '',
      sortOrder: 100,
      stops: points.value.slice(0, 2).map((point) => ({
        pointId: point.id,
        narration: `这里是${point.name}的演示讲解。`,
        transitionType: 'FLY',
      })),
    })
  }
}

function addRouteStop() {
  const point = points.value[routeForm.stops.length % Math.max(1, points.value.length)]
  if (!point) return
  routeForm.stops.push({ pointId: point.id, narration: `这里是${point.name}。`, transitionType: 'FLY' })
}

function removeRouteStop(index: number) {
  routeForm.stops.splice(index, 1)
}

async function saveTour() {
  if (!routeForm.stops.length) {
    error.value = '精品路线至少需要一个站点。'
    return
  }
  try {
    const payload = {
      ...routeForm,
      effectiveFrom: routeForm.effectiveFrom || null,
      effectiveTo: routeForm.effectiveTo || null,
    }
    if (routeForm.id) await adminApi.updateTour(routeForm.id, payload)
    else await adminApi.createTour(payload)
    tours.value = await adminApi.tours()
    banner.value = '精品路线已保存。'
  } catch (exception) {
    handleError(exception)
  }
}

function editCamera(item?: CameraPreset) {
  if (item) {
    Object.assign(cameraForm, {
      ...item,
      key: item.key,
      pointId: item.pointId ?? 0,
    })
  } else {
    Object.assign(cameraForm, {
      id: 0,
      key: '',
      name: '',
      pointId: 0,
      x: 0,
      y: 12,
      z: 20,
      targetX: 0,
      targetY: 3,
      targetZ: 0,
      publicVisible: true,
      status: 'DRAFT',
      sortOrder: 100,
    })
  }
}

async function saveCamera() {
  try {
    if (cameraForm.id) await adminApi.updateCamera(cameraForm.id, cameraForm)
    else await adminApi.createCamera(cameraForm)
    cameras.value = await adminApi.cameras()
    banner.value = '预置机位已保存。'
  } catch (exception) {
    handleError(exception)
  }
}

async function saveConfig(config: SystemConfig) {
  try {
    const updated = await adminApi.updateConfig(config.configKey, config.configValue)
    Object.assign(config, updated)
    banner.value = '参数已更新，新请求将采用新配置。'
  } catch (exception) {
    handleError(exception)
  }
}

async function logout() {
  busy.value = true
  try {
    await authApi.logout()
    user.value = null
  } finally {
    busy.value = false
    // authApi.logout queued a skip so this navigation does not re-check the
    // cookie it just invalidated.
    await router.replace('/admin/login')
  }
}

function goToGuide() {
  void router.push('/')
}

function statusClass(value?: string) {
  return (value ?? '').toLowerCase().replaceAll('_', '-')
}

function handleError(exception: unknown) {
  if (exception instanceof ApiError && exception.status === 401) {
    // The API client already reported the expiry once and the router listener
    // performs a single redirect. Navigating from every failed request here
    // was what caused the 401 request storm.
    error.value = '登录已失效，正在返回登录页…'
    return
  }
  error.value = exception instanceof ApiError ? `${exception.message}（${exception.code}）` : '操作失败，请稍后重试'
}
</script>

<template>
  <main class="admin-shell" :class="{ 'overview-mode': tab === 'overview' }">
    <aside class="admin-sidebar">
      <a href="/" class="admin-brand" title="返回校园数字导览首页">
        <span class="brand-mark"><AppIcon name="spark" :size="20" /></span>
        <span class="brand-copy">
          <strong>HBEUTC</strong>
          <small>校园数字运营中枢</small>
        </span>
      </a>
      <div class="role-stamp">
        <span class="role-avatar">{{ (user?.displayName || 'A').slice(0, 1) }}</span>
        <div class="role-copy">
          <span>{{ user?.role }}</span>
          <strong>{{ user?.displayName }}</strong>
          <small>{{ user?.username }}</small>
        </div>
      </div>
      <nav aria-label="后台导航">
        <span class="nav-label">OPERATIONS</span>
        <button
          v-for="item in allowedTabs"
          :key="item.key"
          type="button"
          :title="item.label"
          :class="{ active: tab === item.key }"
          :aria-current="tab === item.key ? 'page' : undefined"
          @click="switchTab(item.key)"
        >
          <i><AppIcon :name="item.icon" :size="18" /></i>
          <span class="nav-index">{{ item.index }}</span>
          <span class="nav-text">{{ item.label }}</span>
        </button>
      </nav>
      <div class="sidebar-foot">
        <button type="button" class="guide-link" title="进入三维导览" @click="goToGuide">
          <span class="guide-link-icon">3D</span>
          <span>
            <strong>进入三维导览</strong>
            <small>返回公众可视化界面</small>
          </span>
          <AppIcon name="arrow" :size="16" />
        </button>
        <button type="button" class="logout-btn" aria-label="退出登录" title="退出登录" @click="logout">
          <AppIcon name="logout" :size="17" />
          <span>退出登录</span>
        </button>
      </div>
    </aside>

    <section class="admin-main">
      <header class="admin-header">
        <div class="header-title">
          <span class="eyebrow">{{ tabMeta[tab].code }} <em>/ {{ user?.role }}</em></span>
          <h1>{{ navItems.find((item) => item.key === tab)?.label }}</h1>
          <p>{{ tabMeta[tab].desc }}</p>
        </div>
        <div class="header-meta">
          <div v-if="pageStats.length" class="header-kpis" aria-label="当前页面统计">
            <div v-for="stat in pageStats" :key="stat.label" class="header-kpi">
              <strong>{{ stat.value }}</strong>
              <span>{{ stat.label }}</span>
            </div>
          </div>
          <div class="header-status">
            <span class="status-dot" :class="{ online: !busy }" />
            <div>
              <strong>{{ busy ? '数据同步中' : '全部系统正常' }}</strong>
              <small>API · DATABASE · RAG</small>
            </div>
          </div>
          <button type="button" class="ghost-btn" @click="loadAll">刷新数据</button>
        </div>
      </header>

      <p v-if="banner" class="banner success" role="status">{{ banner }}</p>
      <p v-if="error" class="banner error" role="alert">{{ error }}</p>

      <AdminOverview
        v-if="tab === 'overview'"
        :dashboard="dashboard"
        :stats="stats"
        :role="user?.role"
      />

      <section v-else-if="tab === 'points'" class="workspace split-workspace">
        <div class="table-panel">
          <div class="panel-tools">
            <span class="panel-tools-label">点位清单 <small>{{ points.length }} ITEMS</small></span>
            <button v-if="canEditContent" type="button" @click="editPoint()">新建点位</button>
          </div>
          <table>
            <thead><tr><th>点位</th><th>类别</th><th>坐标</th><th>核验</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="point in points" :key="point.id">
                <td><strong>{{ point.name }}</strong><small>{{ point.aliases.join(' / ') }}</small></td>
                <td>{{ displayLabel(point.category) }}</td>
                <td class="mono">{{ point.x }}, {{ point.z }}</td>
                <td><span class="status-chip" :class="statusClass(point.verificationStatus)">{{ displayLabel(point.verificationStatus) }}</span></td>
                <td><span class="status-chip" :class="statusClass(point.status)">{{ displayLabel(point.status) }}</span></td>
                <td class="row-actions">
                  <button v-if="canEditContent" type="button" @click="editPoint(point)">编辑</button>
                  <button v-if="canEditContent" type="button" @click="removePoint(point)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <form v-if="canEditContent" class="editor-panel" @submit.prevent="savePoint">
          <header>
            <span>{{ pointForm.id ? 'EDIT POINT' : 'NEW POINT' }}</span>
            <h2>{{ pointForm.id ? '编辑点位' : '新建点位' }}</h2>
          </header>
          <div class="form-grid">
            <label class="wide">名称<input v-model="pointForm.name" required /></label>
            <label class="wide">别名<input v-model="pointForm.aliases" placeholder="用逗号分隔" /></label>
            <label>类别<select v-model="pointForm.category"><option value="GATE">校门</option><option value="LIBRARY">图书馆</option><option value="TEACHING">教学楼</option><option value="LAB">实验楼</option><option value="DORM">宿舍</option><option value="CANTEEN">食堂</option><option value="SPORTS">运动场</option><option value="ADMIN">行政楼</option><option value="SERVICE">服务设施</option><option value="OTHER">其他</option></select></label>
            <label>道路节点<input v-model.number="pointForm.roadNodeId" type="number" min="1" /></label>
            <label>X 坐标<input v-model.number="pointForm.x" type="number" step="0.1" /></label>
            <label>Z 坐标<input v-model.number="pointForm.z" type="number" step="0.1" /></label>
            <label>建筑高度<input v-model.number="pointForm.buildingHeight" type="number" step="0.1" /></label>
            <label>色值<input v-model="pointForm.colorHex" /></label>
            <label>核验状态<select v-model="pointForm.verificationStatus"><option value="PENDING">待核验</option><option value="DEMO_PLACEHOLDER">演示占位</option><option value="VERIFIED_NAME">名称已核验</option><option value="VERIFIED_FULL">完整核验</option></select></label>
            <label>发布状态<select v-model="pointForm.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="OFFLINE">已下线</option></select></label>
            <label class="wide">介绍<textarea v-model="pointForm.description" rows="5" required /></label>
            <label class="wide">来源标题<input v-model="pointForm.sourceTitle" /></label>
            <label class="wide">来源地址<input v-model="pointForm.sourceUrl" /></label>
          </div>
          <button type="submit" :disabled="busy">保存点位</button>
        </form>
      </section>

      <section v-else-if="tab === 'knowledge'" class="workspace">
        <form v-if="canEditContent" class="upload-strip" @submit.prevent="uploadKnowledge">
          <label class="file-field"><span>选择文件</span><input type="file" accept=".pdf,.doc,.docx,.txt" @change="uploadFile = ($event.target as HTMLInputElement).files?.[0]" /></label>
          <label>标题<input v-model="uploadForm.title" placeholder="默认使用文件名" /></label>
          <label>来源<input v-model="uploadForm.sourceTitle" placeholder="官网栏目或正式文件" /></label>
          <label>来源 URL<input v-model="uploadForm.sourceUrl" /></label>
          <label>年度<input v-model="uploadForm.effectiveYear" /></label>
          <label>生效日期<input v-model="uploadForm.effectiveFrom" type="date" /></label>
          <label>失效日期<input v-model="uploadForm.effectiveTo" type="date" /></label>
          <button type="submit" :disabled="busy">上传并向量化</button>
        </form>
        <div class="table-panel">
          <table>
            <thead><tr><th>文档</th><th>处理</th><th>片段</th><th>年度</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="doc in knowledge" :key="doc.id">
                <td><strong>{{ doc.title }}</strong><small>{{ doc.originalFilename }} · {{ doc.reviewer }}</small></td>
                <td><span class="status-chip" :class="statusClass(doc.parseStatus)">{{ displayLabel(doc.parseStatus) }}</span><small v-if="doc.failureReason">{{ doc.failureReason }}</small></td>
                <td class="mono">{{ doc.chunkCount }}</td>
                <td>{{ doc.effectiveYear || '—' }}</td>
                <td><span class="status-chip" :class="statusClass(doc.status)">{{ displayLabel(doc.status) }}</span></td>
                <td class="row-actions">
                  <button type="button" @click="toggleKnowledge(doc)">{{ doc.status === 'PUBLISHED' ? '撤回' : '发布' }}</button>
                  <button v-if="canEditContent" type="button" @click="reindexKnowledge(doc)">重建</button>
                  <button v-if="canEditContent" type="button" @click="removeKnowledge(doc)">归档</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="tab === 'faq'" class="workspace split-workspace">
        <div class="table-panel">
          <div class="panel-tools">
            <span class="panel-tools-label">问答索引 <small>{{ faqs.length }} ITEMS</small></span>
            <button v-if="canEditContent" type="button" @click="editFaq()">新建 FAQ</button>
          </div>
          <table>
            <thead><tr><th>问题</th><th>优先级</th><th>有效期</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="item in faqs" :key="item.id">
                <td><strong>{{ item.question }}</strong><small>{{ item.category }} · {{ item.sourceTitle }}</small></td>
                <td class="mono">{{ item.priority }}</td>
                <td>{{ item.effectiveFrom || '长期' }} → {{ item.effectiveTo || '长期' }}</td>
                <td><span class="status-chip" :class="statusClass(item.status)">{{ displayLabel(item.status) }}</span></td>
                <td class="row-actions">
                  <button v-if="canEditContent" type="button" @click="editFaq(item)">编辑</button>
                  <button v-if="canEditContent" type="button" @click="removeFaq(item)">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <form v-if="canEditContent" class="editor-panel" @submit.prevent="saveFaq">
          <header><span>{{ faqForm.id ? 'EDIT FAQ' : 'NEW FAQ' }}</span><h2>{{ faqForm.id ? '编辑问答' : '新建问答' }}</h2></header>
          <div class="form-grid">
            <label class="wide">标准问题<input v-model="faqForm.question" required /></label>
            <label class="wide">相似问法<input v-model="faqForm.aliases" placeholder="用逗号分隔" /></label>
            <label class="wide">标准答案<textarea v-model="faqForm.answer" rows="8" required /></label>
            <label>优先级<input v-model.number="faqForm.priority" type="number" /></label>
            <label>状态<select v-model="faqForm.status"><option>DRAFT</option><option>PUBLISHED</option><option>OFFLINE</option></select></label>
            <label>生效日期<input v-model="faqForm.effectiveFrom" type="date" /></label>
            <label>失效日期<input v-model="faqForm.effectiveTo" type="date" /></label>
            <label class="wide">来源标题<input v-model="faqForm.sourceTitle" /></label>
            <label class="wide">来源 URL<input v-model="faqForm.sourceUrl" /></label>
          </div>
          <button type="submit">保存 FAQ</button>
        </form>
      </section>

      <section v-else-if="tab === 'tours'" class="workspace split-workspace">
        <div class="table-panel">
          <div class="panel-tools">
            <span class="panel-tools-label">路线清单 <small>{{ tours.length }} ROUTES</small></span>
            <button v-if="canEditContent" type="button" @click="editTour()">新建路线</button>
          </div>
          <table>
            <thead><tr><th>路线</th><th>时长</th><th>状态</th><th>核验</th><th /></tr></thead>
            <tbody>
              <tr v-for="route in tours" :key="route.id">
                <td><strong>{{ route.name }}</strong><small>{{ route.routeKey }} · {{ route.audience }}</small></td>
                <td class="mono">{{ route.estimatedMinutes }} min</td>
                <td><span class="status-chip" :class="statusClass(route.status)">{{ displayLabel(route.status) }}</span></td>
                <td><span class="status-chip" :class="statusClass(route.verificationStatus)">{{ displayLabel(route.verificationStatus) }}</span></td>
                <td><button v-if="canEditContent" type="button" @click="editTour(route)">编辑</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <form v-if="canEditContent" class="editor-panel" @submit.prevent="saveTour">
          <header><span>{{ routeForm.id ? 'EDIT TOUR' : 'NEW TOUR' }}</span><h2>{{ routeForm.id ? '编辑路线' : '新建路线' }}</h2></header>
          <div class="form-grid">
            <label>路线标识<input v-model="routeForm.key" required /></label>
            <label>名称<input v-model="routeForm.name" required /></label>
            <label class="wide">简介<textarea v-model="routeForm.summary" rows="3" /></label>
            <label>适用对象<input v-model="routeForm.audience" /></label>
            <label>预计分钟<input v-model.number="routeForm.estimatedMinutes" type="number" /></label>
            <label>状态<select v-model="routeForm.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="OFFLINE">已下线</option></select></label>
            <label>核验状态<select v-model="routeForm.verificationStatus"><option>PENDING</option><option>DEMO_PLACEHOLDER</option><option>VERIFIED_FULL</option></select></label>
          </div>
          <div class="stop-editor">
            <div class="stop-head"><strong>站点顺序</strong><button type="button" @click="addRouteStop">添加站点</button></div>
            <div v-for="(stop, index) in routeForm.stops" :key="index" class="stop-row">
              <span>{{ index + 1 }}</span>
              <select v-model.number="stop.pointId">
                <option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option>
              </select>
              <input v-model="stop.narration" placeholder="本站讲稿" />
              <button type="button" @click="removeRouteStop(index)">移除</button>
            </div>
          </div>
          <button type="submit">保存路线</button>
        </form>
      </section>

      <section v-else-if="tab === 'cameras'" class="workspace split-workspace">
        <div class="table-panel">
          <div class="panel-tools">
            <span class="panel-tools-label">机位清单 <small>{{ cameras.length }} RIGS</small></span>
            <button v-if="canEditContent" type="button" @click="editCamera()">新建机位</button>
          </div>
          <table>
            <thead><tr><th>机位</th><th>相机坐标</th><th>目标</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="camera in cameras" :key="camera.id">
                <td><strong>{{ camera.name }}</strong><small>{{ camera.key }}</small></td>
                <td class="mono">{{ camera.x }}, {{ camera.y }}, {{ camera.z }}</td>
                <td class="mono">{{ camera.targetX }}, {{ camera.targetY }}, {{ camera.targetZ }}</td>
                <td><span class="status-chip" :class="statusClass(camera.status)">{{ displayLabel(camera.status) }}</span></td>
                <td><button v-if="canEditContent" type="button" @click="editCamera(camera)">编辑</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <form v-if="canEditContent" class="editor-panel" @submit.prevent="saveCamera">
          <header><span>{{ cameraForm.id ? 'EDIT CAMERA' : 'NEW CAMERA' }}</span><h2>{{ cameraForm.id ? '编辑机位' : '新建机位' }}</h2></header>
          <div class="form-grid">
            <label>标识<input v-model="cameraForm.key" required /></label>
            <label>名称<input v-model="cameraForm.name" required /></label>
            <label>关联点位<select v-model.number="cameraForm.pointId"><option :value="0">不关联</option><option v-for="point in points" :key="point.id" :value="point.id">{{ point.name }}</option></select></label>
            <label>状态<select v-model="cameraForm.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option><option value="OFFLINE">已下线</option></select></label>
            <label>相机 X<input v-model.number="cameraForm.x" type="number" step="0.1" /></label>
            <label>相机 Y<input v-model.number="cameraForm.y" type="number" step="0.1" /></label>
            <label>相机 Z<input v-model.number="cameraForm.z" type="number" step="0.1" /></label>
            <label>目标 X<input v-model.number="cameraForm.targetX" type="number" step="0.1" /></label>
            <label>目标 Y<input v-model.number="cameraForm.targetY" type="number" step="0.1" /></label>
            <label>目标 Z<input v-model.number="cameraForm.targetZ" type="number" step="0.1" /></label>
            <label class="check-field"><input v-model="cameraForm.publicVisible" type="checkbox" /> 公众可见</label>
          </div>
          <button type="submit">保存机位</button>
        </form>
      </section>

      <section v-else-if="tab === 'logs'" class="workspace">
        <div class="table-panel">
          <div class="panel-tools"><span class="panel-tools-label">最近 30 条会话 <small>READ ONLY</small></span></div>
          <table>
            <thead><tr><th>时间</th><th>问题</th><th>意图</th><th>结果</th><th>耗时</th><th>会话</th></tr></thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id">
                <td class="mono">{{ log.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
                <td><strong>{{ log.message }}</strong><small>{{ log.answer }}</small></td>
                <td>{{ displayLabel(log.intent) }}<small>{{ Math.round((log.confidence ?? 0) * 100) }}%</small></td>
                <td><span class="status-chip" :class="statusClass(log.answerStatus)">{{ displayLabel(log.answerStatus) }}</span></td>
                <td class="mono">{{ log.latencyMs }} ms</td>
                <td class="mono">{{ log.sessionId.slice(0, 8) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <section v-else-if="tab === 'configs'" class="workspace">
        <div class="config-list">
          <article v-for="config in configs" :key="config.configKey">
            <div>
              <span>{{ config.configKey }}</span>
              <p>{{ config.description }}</p>
              <small>{{ config.valueType }}<template v-if="config.minValue != null"> · {{ config.minValue }} → {{ config.maxValue }}</template></small>
            </div>
            <input v-model="config.configValue" :type="config.valueType === 'NUMBER' ? 'number' : 'text'" />
            <button type="button" @click="saveConfig(config)">保存</button>
          </article>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.admin-shell {
  --admin-bg: #040d29;
  --admin-panel: rgb(7 26 59 / 88%);
  --admin-panel-strong: rgb(4 15 43 / 96%);
  --admin-line: rgb(125 150 190 / 15%);
  --admin-line-strong: rgb(0 220 246 / 28%);
  --admin-text: #e6f1ff;
  --admin-muted: #7f93b5;
  --admin-cyan: #00dcf6;
  --admin-violet: #4974eb;
  --admin-mint: #34d399;
  --admin-amber: #fbbf24;
  position: relative;
  display: grid;
  min-height: 100vh;
  grid-template-columns: 16.5rem minmax(0, 1fr);
  color: var(--admin-text);
  background:
    radial-gradient(62rem 38rem at 4% -8%, rgb(0 220 246 / 10%), transparent 62%),
    radial-gradient(58rem 42rem at 102% -2%, rgb(73 116 235 / 12%), transparent 58%),
    linear-gradient(145deg, #040d29 0%, #051436 54%, #030a1c 100%);
}

.admin-shell::before {
  position: fixed;
  z-index: 0;
  inset: 0;
  background-image:
    linear-gradient(rgb(125 150 190 / 4.5%) 1px, transparent 1px),
    linear-gradient(90deg, rgb(125 150 190 / 4.5%) 1px, transparent 1px);
  background-size: 64px 64px;
  content: '';
  pointer-events: none;
  mask-image: radial-gradient(ellipse at 55% 22%, #000 18%, transparent 82%);
}

.admin-sidebar {
  position: sticky;
  z-index: 3;
  top: 0;
  display: flex;
  height: 100vh;
  flex-direction: column;
  overflow: auto;
  border-right: 1px solid var(--admin-line);
  padding: 1.25rem 1rem 1rem;
  background:
    linear-gradient(180deg, rgb(5 20 54 / 94%), rgb(4 13 41 / 92%)),
    radial-gradient(18rem 22rem at 0 0, rgb(0 220 246 / 12%), transparent 70%);
  backdrop-filter: blur(24px);
}

.admin-sidebar::after {
  position: absolute;
  top: 0;
  right: -1px;
  width: 1px;
  height: 12rem;
  background: linear-gradient(180deg, transparent, #00dcf6, transparent);
  content: '';
  opacity: 0.55;
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 1px solid rgb(125 150 190 / 10%);
  border-radius: 0.9rem;
  padding: 0.55rem;
  text-decoration: none;
  transition: border-color 180ms ease, background 180ms ease;
}

.admin-brand:hover {
  border-color: rgb(0 220 246 / 28%);
  background: rgb(0 220 246 / 4%);
}

.brand-mark {
  display: grid;
  width: 2.6rem;
  height: 2.6rem;
  place-items: center;
  border: 1px solid rgb(0 220 246 / 40%);
  border-radius: 0.72rem;
  color: #07111f;
  background: linear-gradient(135deg, #6ff5ff, #4974eb 64%, #2c8fc0);
  box-shadow: 0 0 28px rgb(0 220 246 / 28%), inset 0 0 14px rgb(255 255 255 / 34%);
  font-size: 1.15rem;
}

.brand-copy {
  display: grid;
  gap: 0.1rem;
}

.brand-copy strong {
  color: #f0f9ff;
  font: 700 1rem/1 var(--font-mono);
  letter-spacing: 0.08em;
}

.brand-copy small {
  color: #6f86a8;
  font-size: 0.58rem;
  letter-spacing: 0.06em;
}

.role-stamp {
  display: flex;
  align-items: center;
  gap: 0.72rem;
  margin: 1rem 0 1.15rem;
  border: 1px solid var(--admin-line);
  border-radius: 0.9rem;
  padding: 0.78rem;
  background: linear-gradient(135deg, rgb(16 33 60 / 82%), rgb(8 18 36 / 70%));
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 3%);
}

.role-avatar {
  display: grid;
  width: 2.5rem;
  height: 2.5rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgb(73 116 235 / 36%);
  border-radius: 0.72rem;
  color: #e0e7ff;
  background: linear-gradient(135deg, rgb(73 116 235 / 34%), rgb(14 165 233 / 22%));
  box-shadow: 0 0 20px rgb(73 116 235 / 14%);
  font-size: 0.94rem;
  font-weight: 700;
}

.role-copy {
  display: grid;
  min-width: 0;
  gap: 0.08rem;
}

.role-copy > span {
  overflow: hidden;
  color: #6ff5ff;
  font: 0.48rem/1.2 var(--font-mono);
  letter-spacing: 0.1em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-copy strong {
  overflow: hidden;
  color: #e6f1ff;
  font-size: 0.76rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-copy small {
  overflow: hidden;
  color: #657a9b;
  font: 0.55rem/1.3 var(--font-mono);
  text-overflow: ellipsis;
  white-space: nowrap;
}

nav {
  display: grid;
  gap: 0.28rem;
}

.nav-label {
  margin: 0.15rem 0 0.45rem 0.55rem;
  color: #526887;
  font: 0.5rem/1 var(--font-mono);
  letter-spacing: 0.18em;
}

nav button {
  position: relative;
  display: grid;
  width: 100%;
  grid-template-columns: 1.8rem 1.3rem minmax(0, 1fr);
  align-items: center;
  gap: 0.36rem;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 0.68rem;
  padding: 0.64rem 0.55rem;
  color: #879bb9;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: color 180ms ease, border-color 180ms ease, background 180ms ease, transform 180ms ease;
}

nav button::before {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgb(0 220 246 / 8%), rgb(73 116 235 / 4%) 60%, transparent);
  content: '';
  opacity: 0;
  transition: opacity 180ms ease;
}

nav button:hover {
  border-color: rgb(125 150 190 / 12%);
  color: #c7d8ef;
  background: rgb(125 150 190 / 4%);
  transform: translateX(2px);
}

nav button.active {
  border-color: rgb(0 220 246 / 26%);
  color: #e8fbff;
  background: linear-gradient(100deg, rgb(8 145 178 / 17%), rgb(73 116 235 / 9%));
  box-shadow: inset 0 0 22px rgb(0 220 246 / 5%);
}

nav button.active::before {
  opacity: 1;
}

nav button.active::after {
  position: absolute;
  top: 24%;
  bottom: 24%;
  left: 0;
  width: 2px;
  border-radius: 99px;
  background: #00dcf6;
  box-shadow: 0 0 12px #00dcf6;
  content: '';
}

nav button i,
nav button span {
  position: relative;
  z-index: 1;
}

nav button i {
  display: grid;
  width: 1.65rem;
  height: 1.65rem;
  place-items: center;
  border: 1px solid rgb(125 150 190 / 12%);
  border-radius: 0.48rem;
  color: #6f86a8;
  background: rgb(4 11 23 / 42%);
  font-size: 0.7rem;
  font-style: normal;
}

nav button.active i {
  border-color: rgb(0 220 246 / 30%);
  color: #6ff5ff;
  background: rgb(0 220 246 / 8%);
  box-shadow: 0 0 16px rgb(0 220 246 / 10%);
}

.nav-index {
  color: #4c6282;
  font: 0.49rem/1 var(--font-mono);
  letter-spacing: 0.04em;
}

nav button.active .nav-index {
  color: #8edfff;
}

.nav-text {
  overflow: hidden;
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-foot {
  display: grid;
  gap: 0.55rem;
  margin-top: auto;
  padding-top: 1rem;
  border-top: 1px solid var(--admin-line);
}

.sidebar-foot button {
  border: 0;
  border-radius: 0.65rem;
  padding: 0.65rem 0.72rem;
  color: #7589a8;
  background: transparent;
  font-size: 0.64rem;
  text-align: left;
  cursor: pointer;
}

.sidebar-foot button:hover {
  color: #c7d8ef;
  background: rgb(125 150 190 / 6%);
}

.sidebar-foot .guide-link {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  border: 1px solid rgb(0 220 246 / 18%);
  padding: 0.7rem;
  background: linear-gradient(120deg, rgb(8 145 178 / 10%), rgb(73 116 235 / 6%));
}

.guide-link-icon {
  display: grid;
  width: 2rem;
  height: 2rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid rgb(0 220 246 / 28%);
  border-radius: 0.48rem;
  color: #6ff5ff;
  background: rgb(0 220 246 / 7%);
  font: 700 0.55rem/1 var(--font-mono);
}

.sidebar-foot .guide-link > span:nth-child(2) {
  display: grid;
  min-width: 0;
  gap: 0.14rem;
}

.sidebar-foot .guide-link strong {
  color: #cfeff9;
  font-size: 0.64rem;
  font-weight: 600;
}

.sidebar-foot .guide-link small {
  color: #627795;
  font-size: 0.52rem;
}

.sidebar-foot .guide-link b {
  margin-left: auto;
  color: #6ff5ff;
  font-size: 0.78rem;
}

.admin-main {
  position: relative;
  z-index: 1;
  min-width: 0;
  padding: 1.35rem clamp(1rem, 2.1vw, 2.4rem) 2.5rem;
}

.admin-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem;
  margin-bottom: 1.1rem;
  border-bottom: 1px solid var(--admin-line);
  padding: 0.35rem 0 1.15rem;
}

.header-title {
  display: grid;
  min-width: 0;
  gap: 0.34rem;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  color: #4dd6ee;
  font: 0.54rem/1.2 var(--font-mono);
  letter-spacing: 0.16em;
}

.eyebrow em {
  color: #6b7f9f;
  font-style: normal;
  letter-spacing: 0.06em;
}

.admin-header h1 {
  margin: 0;
  color: #eff9ff;
  font: 600 clamp(1.55rem, 2.1vw, 2.15rem)/1.08 var(--font-body);
  letter-spacing: -0.035em;
  text-shadow: 0 0 28px rgb(0 220 246 / 10%);
}

.header-title p {
  max-width: 54rem;
  margin: 0;
  color: #7489a9;
  font-size: 0.67rem;
  line-height: 1.6;
}

.header-meta {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 0.58rem;
}

.header-kpis {
  display: flex;
  flex: 0 0 auto;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid rgb(138 169 214 / 16%);
  border-radius: 0.85rem;
  background: linear-gradient(158deg, rgb(14 38 70 / 78%), rgb(5 16 36 / 92%));
  box-shadow: inset 0 1px 0 rgb(255 255 255 / 4%), 0 14px 34px rgb(0 0 0 / 18%);
}

.header-kpi {
  display: grid;
  min-width: 6.2rem;
  align-content: center;
  justify-items: start;
  gap: 0.18rem;
  padding: 0.52rem 0.92rem 0.48rem;
}

.header-kpi + .header-kpi {
  border-left: 1px solid rgb(138 169 214 / 12%);
}

.header-kpi strong {
  color: #f2fbff;
  font: 600 1.32rem/1 var(--font-display);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
  text-shadow: 0 0 22px rgb(53 215 255 / 24%);
}

.header-kpi span {
  color: #6f88a9;
  font: 0.52rem/1.2 var(--font-mono);
  letter-spacing: 0.1em;
  white-space: nowrap;
}

.header-status {
  display: flex;
  align-items: center;
  gap: 0.58rem;
  border: 1px solid rgb(52 211 153 / 18%);
  border-radius: 0.7rem;
  padding: 0.56rem 0.72rem;
  background: rgb(16 185 129 / 5%);
}

.status-dot {
  width: 0.48rem;
  height: 0.48rem;
  border-radius: 50%;
  background: #fbbf24;
  box-shadow: 0 0 12px rgb(251 191 36 / 70%);
}

.status-dot.online {
  background: #34d399;
  box-shadow: 0 0 12px rgb(52 211 153 / 72%);
}

.header-status div {
  display: grid;
  gap: 0.08rem;
}

.header-status strong {
  color: #c9f7e9;
  font-size: 0.61rem;
  font-weight: 600;
}

.header-status small {
  color: #56778a;
  font: 0.45rem/1.2 var(--font-mono);
  letter-spacing: 0.08em;
}

.ghost-btn,
.primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 2.35rem;
  border-radius: 0.68rem;
  padding: 0.58rem 0.85rem;
  font-size: 0.64rem;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 180ms ease, box-shadow 180ms ease, transform 180ms ease;
}

.ghost-btn {
  border: 1px solid var(--admin-line);
  color: #9db1cf;
  background: rgb(125 150 190 / 5%);
}

.ghost-btn:hover {
  border-color: rgb(0 220 246 / 32%);
  color: #d9f7ff;
}

.primary-btn {
  border: 1px solid rgb(0 220 246 / 42%);
  color: #05121d;
  background: linear-gradient(120deg, #6ff5ff, #4974eb 58%, #4974eb);
  box-shadow: 0 0 26px rgb(0 220 246 / 18%);
}

.primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 0 32px rgb(0 220 246 / 28%);
}

.primary-btn b {
  font-size: 0.84rem;
}

.banner {
  margin: 0 0 1rem;
  border: 1px solid var(--admin-line);
  border-radius: 0.7rem;
  padding: 0.72rem 0.9rem;
  font-size: 0.66rem;
  line-height: 1.55;
}

.banner.success {
  border-color: rgb(52 211 153 / 26%);
  color: #9ef3cd;
  background: rgb(16 185 129 / 8%);
}

.banner.error {
  border-color: rgb(251 113 133 / 28%);
  color: #fecdd3;
  background: rgb(244 63 94 / 8%);
}

.workspace {
  display: grid;
  gap: 1rem;
  min-width: 0;
  animation: workspace-in 380ms cubic-bezier(0.2, 0.7, 0.2, 1) both;
}

.split-workspace {
  grid-template-columns: minmax(0, 1.45fr) minmax(24rem, 0.85fr);
  align-items: start;
}

.table-panel,
.editor-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--admin-line);
  border-radius: 0.95rem;
  background:
    linear-gradient(155deg, rgb(13 28 52 / 84%), rgb(6 15 30 / 92%));
  box-shadow: 0 22px 60px rgb(0 0 0 / 22%), inset 0 1px 0 rgb(255 255 255 / 2%);
}

.table-panel {
  overflow-x: auto;
}

.panel-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid var(--admin-line);
  padding: 0.78rem 0.9rem;
  background: rgb(12 26 48 / 52%);
}

.panel-tools-label {
  display: inline-flex;
  align-items: baseline;
  gap: 0.5rem;
  color: #a9c4dd;
  font: 500 0.62rem/1.4 var(--font-mono);
  letter-spacing: 0.08em;
}

.panel-tools-label small {
  color: #5d7697;
  font-size: 0.5rem;
  letter-spacing: 0.12em;
}

.panel-tools button,
.table-panel td button,
.stop-head button,
.stop-row button,
.config-list button {
  border: 1px solid rgb(0 220 246 / 22%);
  border-radius: 0.48rem;
  padding: 0.36rem 0.58rem;
  color: #a5edf8;
  background: rgb(0 220 246 / 7%);
  font-size: 0.56rem;
  white-space: nowrap;
  cursor: pointer;
}

.panel-tools button:hover,
.table-panel td button:hover,
.stop-head button:hover,
.stop-row button:hover,
.config-list button:hover {
  border-color: rgb(0 220 246 / 46%);
  color: #e5fcff;
  background: rgb(0 220 246 / 12%);
}

table {
  width: 100%;
  min-width: 44rem;
  border-collapse: collapse;
}

thead {
  background: rgb(8 18 35 / 92%);
}

th {
  padding: 0.72rem 0.85rem;
  color: #526b8b;
  font: 500 0.5rem/1.2 var(--font-mono);
  letter-spacing: 0.1em;
  text-align: left;
  white-space: nowrap;
}

td {
  border-top: 1px solid rgb(125 150 190 / 8%);
  padding: 0.76rem 0.85rem;
  color: #93a8c6;
  font-size: 0.62rem;
  line-height: 1.5;
  vertical-align: top;
}

tbody tr {
  transition: background 140ms ease;
}

tbody tr:hover {
  background: rgb(0 220 246 / 4%);
}

td strong {
  display: block;
  max-width: 28rem;
  overflow: hidden;
  color: #dcecff;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

td small {
  display: block;
  max-width: 30rem;
  margin-top: 0.2rem;
  overflow: hidden;
  color: #667d9d;
  font-size: 0.54rem;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mono {
  color: #6fa6bf;
  font-family: var(--font-mono);
  font-size: 0.56rem;
  white-space: nowrap;
}

.row-actions {
  white-space: nowrap;
}

.row-actions button {
  border: 0;
  padding: 0.16rem 0.28rem;
  color: #68c9dc;
  background: transparent;
}

.row-actions button:hover {
  color: #c8faff;
}

.row-actions button + button {
  margin-left: 0.2rem;
  color: #e59aab;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  border: 1px solid rgb(125 150 190 / 18%);
  border-radius: 999px;
  padding: 0.2rem 0.45rem;
  color: #8fa4c2;
  background: rgb(125 150 190 / 7%);
  font: 0.5rem/1 var(--font-mono);
  letter-spacing: 0.03em;
  white-space: nowrap;
}

.status-chip.published,
.status-chip.ready,
.status-chip.verified-full,
.status-chip.answered {
  border-color: rgb(52 211 153 / 28%);
  color: #6ee7b7;
  background: rgb(16 185 129 / 10%);
}

.status-chip.failed,
.status-chip.offline,
.status-chip.out-of-scope,
.status-chip.no-evidence {
  border-color: rgb(251 113 133 / 28%);
  color: #fda4af;
  background: rgb(244 63 94 / 9%);
}

.status-chip.demo-placeholder,
.status-chip.draft,
.status-chip.pending,
.status-chip.processing {
  border-color: rgb(251 191 36 / 25%);
  color: #fcd34d;
  background: rgb(245 158 11 / 9%);
}

.editor-panel {
  padding: 1rem;
  container-type: inline-size;
}

.editor-panel header {
  display: grid;
  gap: 0.26rem;
  margin-bottom: 0.9rem;
  border-bottom: 1px solid var(--admin-line);
  padding-bottom: 0.72rem;
}

.editor-panel header span {
  color: #4ecbdf;
  font: 0.49rem/1.2 var(--font-mono);
  letter-spacing: 0.14em;
}

.editor-panel header h2 {
  margin: 0;
  color: #e1f2ff;
  font-size: 1rem;
  font-weight: 600;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.64rem;
}

@container (max-width: 20rem) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

.form-grid label,
.upload-strip label {
  display: grid;
  min-width: 0;
  gap: 0.28rem;
  color: #7287a7;
  font: 0.53rem/1.2 var(--font-mono);
}

.form-grid .wide,
.check-field {
  grid-column: 1 / -1;
}

.form-grid input,
.form-grid select,
.form-grid textarea,
.upload-strip input,
.upload-strip select,
.stop-row input,
.stop-row select,
.config-list input {
  width: 100%;
  min-width: 0;
  border: 1px solid rgb(125 150 190 / 16%);
  border-radius: 0.52rem;
  outline: 0;
  padding: 0.54rem 0.58rem;
  color: #dbeafe;
  background: rgb(4 12 25 / 54%);
  font: 0.62rem/1.35 var(--font-body);
  transition: border-color 160ms ease, box-shadow 160ms ease, background 160ms ease;
}

.form-grid input:hover,
.form-grid select:hover,
.form-grid textarea:hover,
.upload-strip input:hover,
.upload-strip select:hover,
.stop-row input:hover,
.stop-row select:hover,
.config-list input:hover {
  border-color: rgb(125 150 190 / 26%);
}

.form-grid input:focus,
.form-grid select:focus,
.form-grid textarea:focus,
.upload-strip input:focus,
.upload-strip select:focus,
.stop-row input:focus,
.stop-row select:focus,
.config-list input:focus {
  border-color: rgb(0 220 246 / 52%);
  background: rgb(5 16 32 / 86%);
  box-shadow: 0 0 0 3px rgb(0 220 246 / 7%);
}

.form-grid select option,
.upload-strip select option,
.stop-row select option {
  color: #dbeafe;
  background: #051436;
}

.form-grid textarea {
  resize: vertical;
}

.check-field {
  display: flex !important;
  align-items: center;
  gap: 0.45rem;
}

.check-field input {
  width: auto;
  accent-color: #00dcf6;
}

.editor-panel > button[type='submit'] {
  width: 100%;
  margin-top: 0.9rem;
  border: 1px solid rgb(0 220 246 / 36%);
  border-radius: 0.58rem;
  padding: 0.64rem 0.9rem;
  color: #04131e;
  background: linear-gradient(110deg, #6ff5ff, #4974eb 62%, #4974eb);
  font-size: 0.64rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 0 24px rgb(0 220 246 / 12%);
}

.upload-strip {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 1.15fr 0.66fr 0.82fr 0.82fr auto;
  gap: 0.55rem;
  align-items: end;
  border: 1px solid var(--admin-line);
  border-radius: 0.9rem;
  padding: 0.9rem;
  background: linear-gradient(145deg, rgb(13 28 52 / 80%), rgb(6 15 30 / 84%));
}

.upload-strip .file-field input {
  padding: 0.42rem;
  color: #7f93b5;
}

.file-field input::file-selector-button {
  margin-right: 0.5rem;
  border: 1px solid rgb(0 220 246 / 22%);
  border-radius: 0.38rem;
  padding: 0.32rem 0.5rem;
  color: #a5edf8;
  background: rgb(0 220 246 / 8%);
  font-size: 0.54rem;
  cursor: pointer;
}

.upload-strip button {
  min-height: 2rem;
  border: 1px solid rgb(0 220 246 / 30%);
  border-radius: 0.52rem;
  padding: 0.54rem 0.72rem;
  color: #05131e;
  background: linear-gradient(120deg, #6ff5ff, #4974eb);
  font-size: 0.58rem;
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}

.stop-editor {
  margin-top: 0.9rem;
  border-top: 1px solid var(--admin-line);
  padding-top: 0.8rem;
}

.stop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.55rem;
}

.stop-head strong {
  color: #a9bedb;
  font-size: 0.64rem;
}

.stop-row {
  display: grid;
  grid-template-columns: 1.25rem minmax(6rem, 8rem) minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.38rem;
  margin: 0.38rem 0;
}

.stop-row > span {
  color: #5b7394;
  font: 0.54rem/1 var(--font-mono);
  text-align: center;
}

.config-list {
  display: grid;
  max-width: 68rem;
  gap: 0.55rem;
}

.config-list article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(10rem, 17rem) auto;
  align-items: center;
  gap: 1rem;
  border: 1px solid var(--admin-line);
  border-radius: 0.78rem;
  padding: 0.85rem 0.95rem;
  background: linear-gradient(145deg, rgb(13 28 52 / 72%), rgb(6 15 30 / 82%));
  transition: border-color 160ms ease, transform 160ms ease;
}

.config-list article:hover {
  border-color: rgb(0 220 246 / 20%);
  transform: translateY(-1px);
}

.config-list article span {
  color: #8edfff;
  font: 0.56rem/1.2 var(--font-mono);
  letter-spacing: 0.06em;
}

.config-list p {
  margin: 0.24rem 0;
  color: #758aa9;
  font-size: 0.61rem;
  line-height: 1.5;
}

.config-list small {
  color: #506787;
  font: 0.49rem/1.2 var(--font-mono);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@keyframes workspace-in {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 1500px) {
  .header-status {
    display: none;
  }

  .split-workspace {
    grid-template-columns: minmax(0, 1fr);
  }

  .upload-strip {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .upload-strip .file-field {
    grid-column: span 2;
  }

  .upload-strip > button {
    grid-column: span 2;
  }
}

@media (max-width: 1180px) {
  .admin-header {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .header-meta {
    width: 100%;
    justify-content: flex-end;
  }

  .upload-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .upload-strip .file-field,
  .upload-strip > button {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1240px) {
  .admin-shell {
    grid-template-columns: 13rem minmax(0, 1fr);
  }

  .split-workspace {
    grid-template-columns: 1fr;
  }

  .editor-panel {
    position: static;
  }

  .upload-strip {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .header-status {
    display: none;
  }
}

@media (max-width: 900px) {
  .admin-shell {
    display: block;
  }

  .admin-sidebar {
    position: relative;
    height: auto;
    overflow: visible;
    border-right: 0;
    border-bottom: 1px solid var(--admin-line);
    padding: 0.9rem 1rem;
  }

  .admin-sidebar::after {
    display: none;
  }

  .role-stamp {
    margin: 0.7rem 0;
  }

  nav {
    display: flex;
    gap: 0.38rem;
    overflow-x: auto;
    padding-bottom: 0.2rem;
  }

  .nav-label {
    display: none;
  }

  nav button {
    width: auto;
    flex: 0 0 auto;
    grid-template-columns: 1.6rem minmax(0, 1fr);
  }

  .nav-index {
    display: none;
  }

  .sidebar-foot {
    display: flex;
    align-items: center;
    margin-top: 0.7rem;
    padding-top: 0.65rem;
  }

  .sidebar-foot .guide-link {
    flex: 1;
  }

  .sidebar-foot > button:last-child {
    flex: 0 0 auto;
    border: 1px solid var(--admin-line);
  }

  .admin-main {
    padding: 1rem;
  }

  .admin-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-meta {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-meta .ghost-btn,
  .header-meta .primary-btn {
    flex: 1;
  }

  .header-kpis {
    width: 100%;
  }

  .header-kpi {
    flex: 1;
    min-width: 0;
    justify-items: center;
  }

  .upload-strip {
    grid-template-columns: 1fr 1fr;
  }

  .config-list article {
    grid-template-columns: 1fr;
  }

  .stop-row {
    grid-template-columns: 1.2rem minmax(0, 1fr) auto;
  }

  .stop-row input {
    grid-column: 1 / -1;
  }
}

@media (max-width: 560px) {
  .brand-copy small,
  .role-copy > span,
  .role-copy small {
    display: none;
  }

  .role-stamp {
    padding: 0.55rem;
  }

  .form-grid,
  .upload-strip {
    grid-template-columns: 1fr;
  }

  .panel-tools {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-title p {
    font-size: 0.62rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .workspace {
    animation: none;
  }

  nav button,
  .primary-btn,
  .config-list article {
    transition: none;
  }
}

/* ---------------------------------------------------------------
   UI/UX Pro Max refinement layer
   Cohesive operations-console language, readable type scale,
   accessible focus states and consistent surface elevation.
---------------------------------------------------------------- */
.admin-shell {
  --admin-bg: #020817;
  --admin-panel: rgb(7 22 44 / 88%);
  --admin-panel-strong: rgb(5 17 36 / 96%);
  --admin-line: rgb(138 169 214 / 13%);
  --admin-line-strong: rgb(53 215 255 / 32%);
  --admin-text: #f3f7ff;
  --admin-muted: #8295b4;
  --admin-cyan: #35d7ff;
  --admin-violet: #5c7cfa;
  --admin-mint: #34d399;
  --admin-amber: #ffb454;
  background:
    radial-gradient(62rem 38rem at 4% -8%, rgb(53 215 255 / 9%), transparent 62%),
    radial-gradient(58rem 42rem at 102% -2%, rgb(92 124 250 / 11%), transparent 58%),
    linear-gradient(145deg, #020817 0%, #061226 54%, #020817 100%);
}

.admin-sidebar {
  border-right-color: rgb(138 169 214 / 12%);
  padding: 1.15rem 0.9rem 1rem;
  background:
    linear-gradient(180deg, rgb(5 18 38 / 96%), rgb(2 10 24 / 96%)),
    radial-gradient(20rem 24rem at 0 0, rgb(53 215 255 / 10%), transparent 70%);
}

.admin-brand {
  min-height: 4.15rem;
  border-color: rgb(138 169 214 / 11%);
  border-radius: var(--radius-md);
  padding: 0.62rem;
}

.brand-mark {
  width: 2.75rem;
  height: 2.75rem;
  border-color: rgb(92 217 255 / 45%);
  border-radius: 0.8rem;
  color: #03101f;
  background: linear-gradient(145deg, #8bf4ff, #35d7ff 46%, #5c7cfa);
  box-shadow: 0 8px 26px rgb(53 215 255 / 20%);
}

.brand-copy strong {
  font-size: 0.93rem;
  letter-spacing: 0.06em;
}

.brand-copy small {
  color: #7e94b3;
  font-size: 0.66rem;
  letter-spacing: 0.02em;
}

.role-stamp {
  border-color: rgb(138 169 214 / 12%);
  border-radius: var(--radius-md);
  padding: 0.72rem;
  background: linear-gradient(130deg, rgb(53 215 255 / 6%), rgb(92 124 250 / 4%));
}

.role-avatar {
  width: 2.35rem;
  height: 2.35rem;
  border-color: rgb(53 215 255 / 40%);
  color: #9af5ff;
  background: linear-gradient(145deg, rgb(53 215 255 / 16%), rgb(92 124 250 / 12%));
  font-size: 0.9rem;
}

.role-copy > span {
  color: #5f7899;
  font-size: 0.62rem;
  letter-spacing: 0.08em;
}

.role-copy strong {
  color: #e5efff;
  font-size: 0.8rem;
}

.role-copy small {
  color: #7186a6;
  font-size: 0.66rem;
}

nav {
  gap: 0.32rem;
  margin-top: 0.82rem;
}

.nav-label {
  padding: 0.18rem 0.68rem 0.5rem;
  color: #4e6382;
  font-size: 0.62rem;
  letter-spacing: 0.16em;
}

nav button {
  min-height: 2.95rem;
  gap: 0.56rem;
  border-radius: 0.78rem;
  padding: 0.48rem 0.58rem;
  color: #93a5c2;
}

nav button:hover {
  border-color: rgb(138 169 214 / 14%);
  color: #e0ecfb;
  background: rgb(111 147 194 / 6%);
}

nav button.active {
  border-color: rgb(53 215 255 / 28%);
  color: #f2fdff;
  background: linear-gradient(100deg, rgb(53 215 255 / 14%), rgb(92 124 250 / 8%));
  box-shadow: inset 0 0 26px rgb(53 215 255 / 5%), 0 8px 24px rgb(0 0 0 / 12%);
}

nav button i {
  width: 2rem;
  height: 2rem;
  border-color: rgb(138 169 214 / 12%);
  border-radius: 0.58rem;
  color: #8499b9;
  background: rgb(5 16 34 / 50%);
}

nav button.active i {
  border-color: rgb(53 215 255 / 34%);
  color: #8cf5ff;
  background: rgb(53 215 255 / 9%);
}

.nav-index {
  color: #4d6688;
  font-size: 0.6rem;
}

.nav-text {
  font-size: 0.81rem;
  font-weight: 500;
  letter-spacing: 0.01em;
}

.sidebar-foot {
  gap: 0.5rem;
}

.sidebar-foot button {
  display: flex;
  min-height: 2.7rem;
  align-items: center;
  gap: 0.55rem;
  border-radius: 0.72rem;
  padding: 0.65rem 0.72rem;
  color: #8499b9;
  font-size: 0.78rem;
}

.sidebar-foot button:hover {
  color: #e0ecfb;
  background: rgb(111 147 194 / 7%);
}

.sidebar-foot .guide-link {
  min-height: 3.45rem;
  border-color: rgb(53 215 255 / 18%);
  padding: 0.7rem;
  background: linear-gradient(120deg, rgb(53 215 255 / 8%), rgb(92 124 250 / 5%));
}

.guide-link-icon {
  width: 2.15rem;
  height: 2.15rem;
  border-color: rgb(53 215 255 / 30%);
  border-radius: 0.6rem;
  color: #86f3ff;
  background: rgb(53 215 255 / 8%);
}

.sidebar-foot .guide-link strong {
  font-size: 0.77rem;
}

.sidebar-foot .guide-link small {
  color: #6f84a3;
  font-size: 0.65rem;
}

.logout-btn span {
  white-space: nowrap;
}

.admin-main {
  padding: 1.5rem clamp(1.1rem, 2.2vw, 2.6rem) 2.75rem;
}

.admin-header {
  margin-bottom: 1.3rem;
  border-bottom-color: rgb(138 169 214 / 12%);
  padding: 0.45rem 0 1.3rem;
}

.eyebrow {
  color: #65e7ff;
  font-size: 0.67rem;
  letter-spacing: 0.13em;
}

.eyebrow em {
  color: #7287a7;
}

.admin-header h1 {
  font-size: clamp(1.75rem, 2.2vw, 2.35rem);
  letter-spacing: -0.04em;
}

.header-title p {
  color: #8b9db9;
  font-size: 0.82rem;
  line-height: 1.65;
}

.header-status {
  border-color: rgb(52 211 153 / 20%);
  border-radius: 0.82rem;
  padding: 0.6rem 0.78rem;
  background: rgb(16 185 129 / 6%);
}

.header-status strong {
  font-size: 0.76rem;
}

.header-status small {
  color: #64809a;
  font-size: 0.61rem;
}

.ghost-btn,
.primary-btn {
  min-height: 2.75rem;
  border-radius: 0.78rem;
  padding: 0.62rem 0.95rem;
  font-size: 0.8rem;
  font-weight: 600;
}

.primary-btn {
  border-color: rgb(53 215 255 / 44%);
  background: linear-gradient(120deg, #8bf4ff, #35d7ff 42%, #5c7cfa 100%);
  box-shadow: 0 10px 30px rgb(53 215 255 / 18%);
}

.banner {
  border-radius: 0.8rem;
  padding: 0.8rem 1rem;
  font-size: 0.8rem;
}

.workspace {
  gap: 1.2rem;
}

.table-panel,
.editor-panel {
  border-color: rgb(138 169 214 / 13%);
  border-radius: 1.15rem;
  background: linear-gradient(155deg, rgb(9 29 57 / 88%), rgb(4 14 32 / 94%));
  box-shadow: var(--shadow-panel), inset 0 1px 0 rgb(255 255 255 / 3%);
}

.panel-tools {
  padding: 0.9rem 1rem;
  background: rgb(11 33 63 / 55%);
}

.panel-tools p {
  color: #7d92b0;
  font-size: 0.76rem;
}

.panel-tools button,
.table-panel td button,
.stop-head button,
.stop-row button,
.config-list button {
  min-height: 2.2rem;
  border-color: rgb(53 215 255 / 24%);
  border-radius: 0.6rem;
  padding: 0.4rem 0.68rem;
  color: #b9f5ff;
  background: rgb(53 215 255 / 7%);
  min-height: 2.65rem;
  font-size: 0.76rem;
  font-weight: 600;
}

th {
  padding: 0.82rem 0.92rem;
  color: #7b92b1;
  font-size: 0.68rem;
  letter-spacing: 0.09em;
}

td {
  padding: 0.9rem 0.92rem;
  color: #b2c4db;
  font-size: 0.82rem;
}

td strong {
  color: #e8f1ff;
  font-size: 0.83rem;
}

td small {
  color: #7186a5;
  font-size: 0.69rem;
}

.status-chip {
  border-radius: 999px;
  padding: 0.22rem 0.5rem;
  font-size: 0.67rem;
  font-weight: 600;
}

.editor-panel header span {
  font-size: 0.62rem;
}

.editor-panel header h2 {
  font-size: 1.15rem;
}

.form-grid label,
.upload-strip label {
  gap: 0.35rem;
  color: #96aac7;
  font-size: 0.76rem;
  font-weight: 500;
}

.form-grid input,
.form-grid select,
.form-grid textarea,
.upload-strip input,
.upload-strip select,
.stop-row input,
.stop-row select,
.config-list input {
  min-height: 2.65rem;
  border-color: rgb(138 169 214 / 17%);
  border-radius: 0.7rem;
  padding: 0.58rem 0.68rem;
  color: #e6efff;
  background: rgb(3 12 27 / 56%);
  font-size: 0.84rem;
}

.form-grid textarea {
  min-height: 7.5rem;
}

.editor-panel > button[type='submit'],
.upload-strip button {
  min-height: 2.8rem;
  border-radius: 0.72rem;
  font-size: 0.8rem;
}

.upload-strip {
  border-color: rgb(138 169 214 / 13%);
  border-radius: 1.1rem;
  padding: 1rem;
  background: linear-gradient(145deg, rgb(9 29 57 / 86%), rgb(4 14 32 / 90%));
  box-shadow: var(--shadow-panel);
}

.stop-head strong {
  font-size: 0.79rem;
}

.config-list {
  gap: 0.7rem;
}

.config-list article {
  border-color: rgb(138 169 214 / 13%);
  border-radius: 0.95rem;
  padding: 1rem 1.05rem;
  background: linear-gradient(145deg, rgb(9 29 57 / 78%), rgb(4 14 32 / 88%));
  box-shadow: 0 12px 34px rgb(0 0 0 / 16%);
}

.config-list article span {
  font-size: 0.7rem;
}

.config-list p {
  color: #8b9db9;
  font-size: 0.78rem;
}

.config-list small {
  color: #657c9c;
  font-size: 0.64rem;
}

@media (max-width: 900px) {
  .admin-sidebar {
    padding: 0.85rem 1rem;
    background: rgb(4 14 32 / 96%);
  }

  nav button {
    min-height: 2.8rem;
  }

  .sidebar-foot button {
    min-height: 2.65rem;
  }

  .admin-main {
    padding: 1.15rem;
  }
}

@media (max-width: 560px) {
  .header-title p {
    font-size: 0.78rem;
  }

  .ghost-btn,
  .primary-btn {
    font-size: 0.76rem;
  }

  .table-panel,
  .editor-panel,
  .upload-strip {
    border-radius: 0.95rem;
  }
}

</style>
