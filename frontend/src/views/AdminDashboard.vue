<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, authApi, ApiError } from '../api/client'
import type {
  CameraPreset,
  ChatLog,
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

const pointForm = reactive({
  id: 0,
  name: '',
  aliases: '',
  category: 'TEACHING',
  description: '',
  x: 0,
  z: 0,
  buildingHeight: 7,
  colorHex: '#6d8e7d',
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

const navItems: Array<{ key: TabKey; label: string; index: string }> = [
  { key: 'overview', label: '运行概览', index: '01' },
  { key: 'points', label: '校园点位', index: '02' },
  { key: 'knowledge', label: '知识库', index: '03' },
  { key: 'faq', label: '高频问答', index: '04' },
  { key: 'tours', label: '精品路线', index: '05' },
  { key: 'cameras', label: '预置机位', index: '06' },
  { key: 'logs', label: '对话日志', index: '07' },
  { key: 'configs', label: '系统参数', index: '08' },
]

const allowedTabs = computed(() => {
  const role = user.value?.role
  if (role === 'SYSTEM_ADMIN') return navItems
  if (role === 'DATA_ANALYST') return navItems.filter((item) => ['overview', 'logs'].includes(item.key))
  if (role === 'REVIEWER') return navItems.filter((item) => item.key !== 'configs')
  return navItems.filter((item) => item.key !== 'configs')
})

const canEditContent = computed(() => ['SYSTEM_ADMIN', 'CONTENT_ADMIN'].includes(user.value?.role ?? ''))

onMounted(async () => {
  if (!user.value) {
    await router.replace('/admin/login')
    return
  }
  await loadAll()
})

async function loadAll() {
  busy.value = true
  error.value = ''
  try {
    stats.value = await adminApi.stats()
    const logPage = await adminApi.logs()
    logs.value = logPage.records
    if (user.value?.role !== 'DATA_ANALYST') {
      ;[points.value, knowledge.value, faqs.value, tours.value, cameras.value] = await Promise.all([
        adminApi.points(),
        adminApi.knowledge(),
        adminApi.faqs(),
        adminApi.tours(),
        adminApi.cameras(),
      ])
    }
    if (user.value?.role === 'SYSTEM_ADMIN') {
      configs.value = await adminApi.configs()
    }
  } catch (exception) {
    handleError(exception)
  } finally {
    busy.value = false
  }
}

async function switchTab(next: TabKey) {
  tab.value = next
  banner.value = ''
  error.value = ''
  if (next === 'overview' || next === 'logs') {
    try {
      stats.value = await adminApi.stats()
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
      colorHex: point.colorHex ?? '#6d8e7d',
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
      colorHex: '#6d8e7d',
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

function logout() {
  authApi.logout()
  void router.replace('/admin/login')
}

function goToGuide() {
  void router.push('/')
}

function statusClass(value?: string) {
  return (value ?? '').toLowerCase().replaceAll('_', '-')
}

function handleError(exception: unknown) {
  error.value = exception instanceof ApiError ? `${exception.message}（${exception.code}）` : '操作失败，请稍后重试'
  if (exception instanceof ApiError && exception.status === 401) {
    void router.replace('/admin/login')
  }
}
</script>

<template>
  <main class="admin-shell">
    <aside class="admin-sidebar">
      <a href="/" class="admin-brand">
        <span>HBEUTC</span>
        <strong>校园导览后台</strong>
      </a>
      <div class="role-stamp">
        <span>{{ user?.role }}</span>
        <strong>{{ user?.displayName }}</strong>
        <small>{{ user?.username }}</small>
      </div>
      <nav aria-label="后台导航">
        <button
          v-for="item in allowedTabs"
          :key="item.key"
          type="button"
          :class="{ active: tab === item.key }"
          @click="switchTab(item.key)"
        >
          <span>{{ item.index }}</span>
          {{ item.label }}
        </button>
      </nav>
      <div class="sidebar-foot">
        <button type="button" class="guide-link" @click="goToGuide">
          <span class="guide-link-icon">3D</span>
          <span>
            <strong>进入三维导览</strong>
            <small>返回公众可视化界面</small>
          </span>
          <b>↗</b>
        </button>
        <button type="button" @click="logout">退出登录</button>
      </div>
    </aside>

    <section class="admin-main">
      <header class="admin-header">
        <div>
          <span class="eyebrow">CONTENT OPERATIONS / {{ tab.toUpperCase() }}</span>
          <h1>{{ navItems.find((item) => item.key === tab)?.label }}</h1>
        </div>
        <div class="header-meta">
          <button type="button" class="viewer-link" @click="goToGuide">
            <span class="viewer-icon">3D</span>
            <span>进入三维导览</span>
            <b>↗</b>
          </button>
          <span :class="{ online: !busy }">{{ busy ? '处理中' : '服务在线' }}</span>
          <button type="button" @click="loadAll">刷新数据</button>
        </div>
      </header>

      <p v-if="banner" class="banner success" role="status">{{ banner }}</p>
      <p v-if="error" class="banner error" role="alert">{{ error }}</p>

      <section v-if="tab === 'overview'" class="workspace overview-grid">
        <div class="metric hero-metric">
          <span>累计对话</span>
          <strong>{{ stats?.totalConversations ?? 0 }}</strong>
          <small>来自公众匿名会话</small>
        </div>
        <div class="metric">
          <span>待补知识问题</span>
          <strong>{{ stats?.unanswerableCount ?? 0 }}</strong>
          <small>无依据或超出服务范围</small>
        </div>
        <div class="metric">
          <span>平均处理时延</span>
          <strong>{{ stats?.averageLatencyMs ?? 0 }}<em>ms</em></strong>
          <small>后端完整响应时间</small>
        </div>
        <article class="data-panel">
          <header><span>意图分布</span><small>INTENT MAP</small></header>
          <div v-for="(count, intent) in stats?.intentCounts" :key="intent" class="bar-row">
            <span>{{ intent }}</span>
            <div><i :style="{ width: `${Math.min(100, count * 12)}%` }" /></div>
            <strong>{{ count }}</strong>
          </div>
          <p v-if="!Object.keys(stats?.intentCounts ?? {}).length" class="empty">暂无对话数据。</p>
        </article>
        <article class="data-panel">
          <header><span>热门点位</span><small>POINT SIGNAL</small></header>
          <ol>
            <li v-for="item in stats?.popularPoints" :key="item.name">
              <span>{{ item.name }}</span><strong>{{ item.count }}</strong>
            </li>
          </ol>
          <p v-if="!stats?.popularPoints.length" class="empty">暂无点位访问数据。</p>
        </article>
        <article class="data-panel wide">
          <header><span>高频未命中问题</span><small>FAQ BACKLOG</small></header>
          <ol>
            <li v-for="item in stats?.frequentQuestions" :key="item.name">
              <span>{{ item.name }}</span><strong>{{ item.count }}</strong>
            </li>
          </ol>
          <p v-if="!stats?.frequentQuestions.length" class="empty">当前没有未命中问题。</p>
        </article>
      </section>

      <section v-else-if="tab === 'points'" class="workspace split-workspace">
        <div class="table-panel">
          <div class="panel-tools">
            <p>点位坐标使用 Three.js 场景单位。只有状态为已发布且资料有效的点位才会出现在公众端。</p>
            <button v-if="canEditContent" type="button" @click="editPoint()">新建点位</button>
          </div>
          <table>
            <thead><tr><th>点位</th><th>类别</th><th>坐标</th><th>核验</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="point in points" :key="point.id">
                <td><strong>{{ point.name }}</strong><small>{{ point.aliases.join(' / ') }}</small></td>
                <td>{{ point.category }}</td>
                <td class="mono">{{ point.x }}, {{ point.z }}</td>
                <td><span class="status-chip" :class="statusClass(point.verificationStatus)">{{ point.verificationStatus }}</span></td>
                <td><span class="status-chip" :class="statusClass(point.status)">{{ point.status }}</span></td>
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
            <label>类别<select v-model="pointForm.category"><option>GATE</option><option>LIBRARY</option><option>TEACHING</option><option>LAB</option><option>DORM</option><option>CANTEEN</option><option>SPORTS</option><option>ADMIN</option><option>SERVICE</option></select></label>
            <label>道路节点<input v-model.number="pointForm.roadNodeId" type="number" min="1" /></label>
            <label>X 坐标<input v-model.number="pointForm.x" type="number" step="0.1" /></label>
            <label>Z 坐标<input v-model.number="pointForm.z" type="number" step="0.1" /></label>
            <label>建筑高度<input v-model.number="pointForm.buildingHeight" type="number" step="0.1" /></label>
            <label>色值<input v-model="pointForm.colorHex" /></label>
            <label>核验状态<select v-model="pointForm.verificationStatus"><option>PENDING</option><option>DEMO_PLACEHOLDER</option><option>VERIFIED_NAME</option><option>VERIFIED_FULL</option></select></label>
            <label>发布状态<select v-model="pointForm.status"><option>DRAFT</option><option>PUBLISHED</option><option>OFFLINE</option></select></label>
            <label class="wide">介绍<textarea v-model="pointForm.description" rows="5" required /></label>
            <label class="wide">来源标题<input v-model="pointForm.sourceTitle" /></label>
            <label class="wide">来源地址<input v-model="pointForm.sourceUrl" /></label>
          </div>
          <button type="submit" :disabled="busy">保存点位</button>
        </form>
      </section>

      <section v-else-if="tab === 'knowledge'" class="workspace">
        <div class="section-intro">
          <div><span class="eyebrow">PRIVATE RAG / DOCUMENT LIFECYCLE</span><h2>校园私有知识库</h2><p>文档上传后异步解析、清洗、分片和向量化。重新索引成功后才替换现有片段。</p></div>
        </div>
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
                <td><span class="status-chip" :class="statusClass(doc.parseStatus)">{{ doc.parseStatus }}</span><small v-if="doc.failureReason">{{ doc.failureReason }}</small></td>
                <td class="mono">{{ doc.chunkCount }}</td>
                <td>{{ doc.effectiveYear || '—' }}</td>
                <td><span class="status-chip" :class="statusClass(doc.status)">{{ doc.status }}</span></td>
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
            <p>FAQ 优先于一般向量检索，但未审核或过期内容不会命中。</p>
            <button v-if="canEditContent" type="button" @click="editFaq()">新建 FAQ</button>
          </div>
          <table>
            <thead><tr><th>问题</th><th>优先级</th><th>有效期</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="item in faqs" :key="item.id">
                <td><strong>{{ item.question }}</strong><small>{{ item.category }} · {{ item.sourceTitle }}</small></td>
                <td class="mono">{{ item.priority }}</td>
                <td>{{ item.effectiveFrom || '长期' }} → {{ item.effectiveTo || '长期' }}</td>
                <td><span class="status-chip" :class="statusClass(item.status)">{{ item.status }}</span></td>
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
            <p>路线按站点顺序驱动镜头、数字人讲解和站点切换。</p>
            <button v-if="canEditContent" type="button" @click="editTour()">新建路线</button>
          </div>
          <table>
            <thead><tr><th>路线</th><th>时长</th><th>状态</th><th>核验</th><th /></tr></thead>
            <tbody>
              <tr v-for="route in tours" :key="route.id">
                <td><strong>{{ route.name }}</strong><small>{{ route.routeKey }} · {{ route.audience }}</small></td>
                <td class="mono">{{ route.estimatedMinutes }} min</td>
                <td><span class="status-chip" :class="statusClass(route.status)">{{ route.status }}</span></td>
                <td><span class="status-chip" :class="statusClass(route.verificationStatus)">{{ route.verificationStatus }}</span></td>
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
            <label>状态<select v-model="routeForm.status"><option>DRAFT</option><option>PUBLISHED</option><option>OFFLINE</option></select></label>
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
            <p>机位坐标与镜头目标使用 Three.js 世界坐标。</p>
            <button v-if="canEditContent" type="button" @click="editCamera()">新建机位</button>
          </div>
          <table>
            <thead><tr><th>机位</th><th>相机坐标</th><th>目标</th><th>状态</th><th /></tr></thead>
            <tbody>
              <tr v-for="camera in cameras" :key="camera.id">
                <td><strong>{{ camera.name }}</strong><small>{{ camera.key }}</small></td>
                <td class="mono">{{ camera.x }}, {{ camera.y }}, {{ camera.z }}</td>
                <td class="mono">{{ camera.targetX }}, {{ camera.targetY }}, {{ camera.targetZ }}</td>
                <td><span class="status-chip" :class="statusClass(camera.status)">{{ camera.status }}</span></td>
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
            <label>状态<select v-model="cameraForm.status"><option>DRAFT</option><option>PUBLISHED</option><option>OFFLINE</option></select></label>
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
          <div class="panel-tools"><p>日志展示最近 30 条。原始录音不保存，敏感字段应在导出前脱敏。</p></div>
          <table>
            <thead><tr><th>时间</th><th>问题</th><th>意图</th><th>结果</th><th>耗时</th><th>会话</th></tr></thead>
            <tbody>
              <tr v-for="log in logs" :key="log.id">
                <td class="mono">{{ log.createdAt?.replace('T', ' ').slice(0, 19) }}</td>
                <td><strong>{{ log.message }}</strong><small>{{ log.answer }}</small></td>
                <td>{{ log.intent }}<small>{{ Math.round((log.confidence ?? 0) * 100) }}%</small></td>
                <td><span class="status-chip" :class="statusClass(log.answerStatus)">{{ log.answerStatus }}</span></td>
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
  display: grid;
  min-height: 100vh;
  grid-template-columns: 15.5rem minmax(0, 1fr);
  color: #17312f;
  background: #eae5d8;
}

.admin-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  height: 100vh;
  flex-direction: column;
  padding: 1.4rem 1.1rem;
  color: var(--ivory);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 3%), transparent),
    #0b2828;
}

.admin-brand {
  display: grid;
  gap: 0.35rem;
  color: var(--ivory);
  text-decoration: none;
}

.admin-brand span {
  color: var(--amber);
  font: 0.62rem/1 var(--font-mono);
  letter-spacing: 0.18em;
}

.admin-brand strong {
  font: 500 1.15rem/1.2 var(--font-display);
}

.role-stamp {
  display: grid;
  margin: 2rem 0 1.5rem;
  padding: 0.85rem 0 0.85rem 0.8rem;
  border-left: 1px solid var(--amber);
}

.role-stamp span,
.role-stamp small {
  color: var(--muted);
  font: 0.55rem/1.5 var(--font-mono);
  letter-spacing: 0.08em;
}

.role-stamp strong {
  margin: 0.2rem 0;
  font-size: 0.82rem;
}

nav {
  display: grid;
  gap: 0.15rem;
}

nav button {
  display: flex;
  gap: 0.85rem;
  border: 0;
  padding: 0.68rem 0.55rem;
  color: rgb(238 233 218 / 62%);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

nav button span {
  color: rgb(217 181 101 / 48%);
  font: 0.57rem/1.4 var(--font-mono);
}

nav button:hover,
nav button.active {
  color: var(--ivory);
  background: linear-gradient(90deg, rgb(217 181 101 / 12%), transparent);
}

nav button.active span {
  color: var(--amber);
}

.sidebar-foot {
  margin-top: auto;
  display: grid;
  gap: 0.65rem;
}

.sidebar-foot a,
.sidebar-foot button {
  border: 0;
  padding: 0;
  color: var(--muted);
  background: transparent;
  text-align: left;
  text-decoration: none;
  cursor: pointer;
  font-size: 0.68rem;
}

.sidebar-foot .guide-link {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 0.65rem;
  align-items: center;
  border: 1px solid rgb(217 181 101 / 48%);
  padding: 0.72rem 0.65rem;
  color: var(--ivory);
  background: rgb(217 181 101 / 9%);
}

.guide-link-icon {
  display: grid;
  width: 1.9rem;
  height: 1.9rem;
  place-items: center;
  border: 1px solid rgb(217 181 101 / 56%);
  color: var(--amber);
  font: 600 0.52rem/1 var(--font-mono);
}

.sidebar-foot .guide-link > span:nth-child(2) {
  display: grid;
  gap: 0.16rem;
  text-align: left;
}

.sidebar-foot .guide-link strong {
  font-size: 0.7rem;
  font-weight: 600;
}

.sidebar-foot .guide-link small {
  color: rgb(239 232 211 / 48%);
  font: 0.48rem/1.25 var(--font-mono);
}

.sidebar-foot .guide-link b {
  color: var(--amber);
  font-size: 0.8rem;
  font-weight: 500;
}

.admin-main {
  min-width: 0;
  padding: 1.6rem 2rem 3rem;
}

.admin-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 2rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid rgb(23 49 47 / 16%);
}

.eyebrow {
  color: #7c6b43;
  font: 0.58rem/1.2 var(--font-mono);
  letter-spacing: 0.14em;
}

.admin-header h1 {
  margin: 0.4rem 0 0;
  font: 500 2.1rem/1 var(--font-display);
}

.header-meta {
  display: flex;
  align-items: center;
  gap: 1rem;
  color: #68776f;
  font: 0.64rem/1 var(--font-mono);
}

.header-meta span::before {
  display: inline-block;
  width: 0.4rem;
  height: 0.4rem;
  margin-right: 0.4rem;
  border-radius: 50%;
  background: #c49340;
  content: '';
}

.header-meta span.online::before {
  background: #3f8a69;
}

.header-meta button,
.panel-tools button,
.editor-panel > button,
.upload-strip button,
.row-actions button,
.stop-head button,
.stop-row button,
.config-list button {
  border: 1px solid #213f3c;
  padding: 0.48rem 0.7rem;
  color: #17312f;
  background: transparent;
  cursor: pointer;
}

.header-meta .viewer-link {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  border-color: #17312f;
  padding: 0.48rem 0.72rem;
  color: #f5ebd4;
  background: #17312f;
  box-shadow: 0 8px 20px rgb(24 49 46 / 14%);
  font-size: 0.66rem;
  font-weight: 600;
}

.viewer-icon {
  display: grid;
  width: 1.65rem;
  height: 1.65rem;
  place-items: center;
  border: 1px solid rgb(217 181 101 / 54%);
  color: var(--amber);
  font: 600 0.48rem/1 var(--font-mono);
}

.viewer-link b {
  color: var(--amber);
  font-size: 0.8rem;
  font-weight: 500;
}

.header-meta .viewer-link:hover {
  border-color: #b8863d;
  background: #22453f;
}

.banner {
  margin: 1rem 0 0;
  padding: 0.7rem 0.9rem;
  font-size: 0.72rem;
}

.banner.success {
  background: #dce8dc;
}

.banner.error {
  color: #7b322b;
  background: #efd9d2;
}

.workspace {
  margin-top: 1.5rem;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.metric {
  min-height: 10rem;
  border-top: 1px solid #22433f;
  padding: 1.1rem 0.2rem;
}

.metric span,
.data-panel > header span,
.section-intro h2,
.config-list article span {
  font: 500 1rem/1.3 var(--font-display);
}

.metric strong {
  display: block;
  margin: 1rem 0 0.4rem;
  font: 400 clamp(2.4rem, 4vw, 4.2rem)/1 var(--font-display);
}

.metric strong em {
  margin-left: 0.2rem;
  color: #7c6b43;
  font: 0.7rem/1 var(--font-mono);
}

.metric small {
  color: #71807a;
  font-size: 0.64rem;
}

.hero-metric {
  border-top: 3px solid #b8863d;
}

.data-panel {
  grid-column: span 1;
  border: 1px solid rgb(23 49 47 / 14%);
  padding: 1rem;
  background: rgb(255 255 255 / 22%);
}

.data-panel.wide {
  grid-column: span 2;
}

.data-panel > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.data-panel > header small {
  color: #85775a;
  font: 0.55rem/1 var(--font-mono);
}

.bar-row {
  display: grid;
  grid-template-columns: 8rem 1fr 2rem;
  gap: 0.7rem;
  align-items: center;
  margin: 0.65rem 0;
  font: 0.64rem/1 var(--font-mono);
}

.bar-row div {
  height: 3px;
  background: rgb(23 49 47 / 10%);
}

.bar-row i {
  display: block;
  height: 100%;
  background: #aa7d34;
}

.data-panel ol {
  margin: 0;
  padding: 0;
  list-style: none;
}

.data-panel li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  border-top: 1px solid rgb(23 49 47 / 8%);
  padding: 0.6rem 0;
  font-size: 0.72rem;
}

.empty {
  color: #7d857f;
  font-size: 0.7rem;
}

.split-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(21rem, 0.85fr);
  gap: 1rem;
  align-items: start;
}

.table-panel {
  min-width: 0;
  overflow: auto;
  border-top: 1px solid #17312f;
}

.panel-tools {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  padding: 0.8rem 0;
}

.panel-tools p {
  margin: 0;
  color: #69766f;
  font-size: 0.68rem;
  line-height: 1.6;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.69rem;
}

th {
  border-bottom: 1px solid rgb(23 49 47 / 18%);
  padding: 0.6rem 0.5rem;
  color: #6b786f;
  font: 0.55rem/1 var(--font-mono);
  text-align: left;
  letter-spacing: 0.07em;
}

td {
  border-bottom: 1px solid rgb(23 49 47 / 8%);
  padding: 0.72rem 0.5rem;
  vertical-align: top;
}

td strong {
  display: block;
  font-weight: 600;
}

td small {
  display: block;
  margin-top: 0.25rem;
  color: #7a857f;
  font-size: 0.59rem;
  line-height: 1.45;
}

.mono {
  font-family: var(--font-mono);
  font-size: 0.62rem;
}

.row-actions {
  white-space: nowrap;
}

.row-actions button {
  border: 0;
  padding: 0.2rem;
  color: #6f5127;
  font-size: 0.62rem;
}

.status-chip {
  display: inline-block;
  border: 1px solid rgb(23 49 47 / 16%);
  padding: 0.18rem 0.35rem;
  font: 0.55rem/1 var(--font-mono);
}

.status-chip.published,
.status-chip.ready,
.status-chip.verified-full {
  color: #28604a;
  border-color: rgb(40 96 74 / 35%);
  background: #dbe7dd;
}

.status-chip.failed,
.status-chip.offline,
.status-chip.out-of-scope,
.status-chip.no-evidence {
  color: #813b31;
  border-color: rgb(129 59 49 / 25%);
  background: #efddd5;
}

.status-chip.demo-placeholder,
.status-chip.draft,
.status-chip.pending {
  color: #78612e;
  background: #eee5cd;
}

.editor-panel {
  position: sticky;
  top: 1rem;
  border-top: 3px solid #b8863d;
  padding: 1rem;
  background: #f4f0e5;
  box-shadow: 0 20px 50px rgb(26 48 44 / 10%);
}

.editor-panel header span {
  color: #8b7245;
  font: 0.55rem/1 var(--font-mono);
  letter-spacing: 0.12em;
}

.editor-panel header h2 {
  margin: 0.4rem 0 1rem;
  font: 500 1.3rem/1.1 var(--font-display);
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.7rem;
}

.form-grid label,
.upload-strip label {
  display: grid;
  gap: 0.28rem;
  color: #6d786f;
  font: 0.56rem/1.2 var(--font-mono);
}

.form-grid .wide,
.check-field {
  grid-column: 1 / -1;
}

.form-grid input,
.form-grid select,
.form-grid textarea,
.upload-strip input,
.stop-row input,
.stop-row select,
.config-list input {
  width: 100%;
  border: 1px solid rgb(23 49 47 / 18%);
  outline: 0;
  padding: 0.5rem 0.55rem;
  color: #17312f;
  background: rgb(255 255 255 / 45%);
  font: 0.7rem/1.35 var(--font-body);
}

.form-grid textarea {
  resize: vertical;
}

.form-grid input:focus,
.form-grid select:focus,
.form-grid textarea:focus,
.upload-strip input:focus,
.stop-row input:focus,
.config-list input:focus {
  border-color: #a47732;
}

.check-field {
  display: flex !important;
  align-items: center;
  grid-template-columns: auto 1fr;
}

.check-field input {
  width: auto;
}

.editor-panel > button {
  margin-top: 1rem;
  color: #f3ead4;
  background: #17312f;
}

.section-intro {
  display: flex;
  justify-content: space-between;
  margin-bottom: 1rem;
}

.section-intro h2 {
  margin: 0.35rem 0;
  font-size: 1.3rem;
}

.section-intro p {
  margin: 0;
  color: #6f7b74;
  font-size: 0.7rem;
}

.upload-strip {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr 1.4fr 0.5fr 0.7fr 0.7fr auto;
  gap: 0.55rem;
  align-items: end;
  margin-bottom: 1rem;
  padding: 0.9rem;
  background: #f4f0e5;
}

.upload-strip button {
  color: #f3ead4;
  background: #17312f;
}

.stop-editor {
  margin-top: 1rem;
  border-top: 1px solid rgb(23 49 47 / 14%);
  padding-top: 0.8rem;
}

.stop-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
  font-size: 0.72rem;
}

.stop-row {
  display: grid;
  grid-template-columns: 1.5rem 8rem 1fr auto;
  gap: 0.4rem;
  align-items: center;
  margin: 0.4rem 0;
}

.stop-row > span {
  color: #8b7245;
  font: 0.6rem/1 var(--font-mono);
}

.config-list {
  display: grid;
  gap: 0;
  max-width: 58rem;
  border-top: 1px solid #17312f;
}

.config-list article {
  display: grid;
  grid-template-columns: 1fr minmax(12rem, 18rem) auto;
  gap: 1rem;
  align-items: center;
  border-bottom: 1px solid rgb(23 49 47 / 12%);
  padding: 1rem 0;
}

.config-list p {
  margin: 0.25rem 0;
  color: #6f7a74;
  font-size: 0.68rem;
}

.config-list small {
  color: #8a938d;
  font: 0.55rem/1 var(--font-mono);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

@media (max-width: 1150px) {
  .admin-shell {
    grid-template-columns: 12rem minmax(0, 1fr);
  }

  .split-workspace {
    grid-template-columns: 1fr;
  }

  .editor-panel {
    position: static;
  }

  .upload-strip {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

@media (max-width: 780px) {
  .admin-shell {
    display: block;
  }

  .admin-sidebar {
    position: static;
    height: auto;
  }

  .role-stamp {
    margin: 1rem 0;
  }

  nav {
    display: flex;
    overflow-x: auto;
  }

  nav button {
    flex: 0 0 auto;
  }

  .sidebar-foot {
    display: none;
  }

  .admin-main {
    padding: 1rem;
  }

  .admin-header h1 {
    font-size: 1.55rem;
  }

  .admin-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-meta {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-meta .viewer-link {
    margin-right: auto;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .data-panel.wide {
    grid-column: auto;
  }

  .upload-strip {
    grid-template-columns: 1fr;
  }

  .config-list article {
    grid-template-columns: 1fr;
  }
}
</style>
