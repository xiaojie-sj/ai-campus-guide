export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
  timestamp: string
}

export interface PointView {
  id: number
  name: string
  aliases: string[]
  category: string
  description: string
  x: number
  z: number
  buildingHeight: number
  colorHex: string
  modelKey: string
  roadNodeId: number
  status: string
  verificationStatus: string
  sourceTitle?: string
  sourceUrl?: string
  sortOrder: number
}

export interface CampusBuildingView {
  id: number
  pointId?: number
  name: string
  category: string
  height: number
  colorHex?: string
  footprint: [number, number][]
}

export interface RoadNodeView {
  id: number
  name: string
  x: number
  z: number
}

export interface RoadEdgeView {
  id: number
  fromNodeId: number
  toNodeId: number
  distance: number
}

export interface CameraView {
  id: number
  key: string
  name: string
  pointId?: number
  x: number
  y: number
  z: number
  targetX: number
  targetY: number
  targetZ: number
  sortOrder: number
}

export interface TourStopView {
  id: number
  pointId: number
  pointName: string
  x: number
  z: number
  sortOrder: number
  narration: string
  transitionType: string
}

export interface TourView {
  id: number
  key: string
  name: string
  summary: string
  audience: string
  estimatedMinutes: number
  verificationStatus: string
  stops: TourStopView[]
}

export interface PublicConfig {
  avatarSpeechRate: number
  avatarVolume: number
  roamSpeed: number
  cameraFlightSeconds: number
  highlightMs: number
  autoSpeak: boolean
  mode: string
  notice: string
}

export interface BootstrapResponse {
  points: PointView[]
  buildings: CampusBuildingView[]
  roadNodes: RoadNodeView[]
  roadEdges: RoadEdgeView[]
  cameras: CameraView[]
  tours: TourView[]
  config: PublicConfig
  dataVersion: string
}

export interface SourceRef {
  title: string
  url?: string
  excerpt?: string
  effectiveYear?: string
}

export interface MediaRef {
  id: number
  title: string
  url: string
  altText?: string
  sourceTitle?: string
  sourceUrl?: string
}

export interface NavigationStep {
  index: number
  fromNodeId: number
  toNodeId: number
  instruction: string
  distance: number
}

export interface RoutePlan {
  startPointId: number
  startName: string
  endPointId: number
  endName: string
  distance: number
  estimatedSeconds: number
  path: [number, number][]
  steps: NavigationStep[]
  sources: SourceRef[]
}

export interface SceneCommand {
  type: string
  pointId?: number
  cameraKey?: string
  route?: RoutePlan
  action?: string
}

export interface ChatResponse {
  logId: number
  sessionId: string
  intent: string
  confidence: number
  answer: string
  answerStatus: string
  sources: SourceRef[]
  media: MediaRef[]
  command: SceneCommand
}

export interface KnowledgeDocumentView {
  id: number
  title: string
  originalFilename: string
  sourceTitle?: string
  sourceUrl?: string
  effectiveYear?: string
  effectiveFrom?: string
  effectiveTo?: string
  category?: string
  status: string
  parseStatus: string
  chunkCount: number
  indexVersion?: string
  reviewer?: string
  failureReason?: string
  updatedAt: string
}

export interface FaqItem {
  id: number
  question: string
  aliases?: string
  answer: string
  category?: string
  sourceTitle?: string
  sourceUrl?: string
  priority: number
  status: string
  effectiveFrom?: string
  effectiveTo?: string
  updatedAt: string
}

export interface TourRoute {
  id: number
  routeKey: string
  name: string
  summary?: string
  audience?: string
  estimatedMinutes: number
  status: string
  verificationStatus: string
  effectiveFrom?: string
  effectiveTo?: string
  sortOrder: number
}

export interface TourStop {
  id: number
  routeId: number
  pointId: number
  sortOrder: number
  narration?: string
  transitionType?: string
}

export interface CameraPreset extends CameraView {
  publicVisible: boolean
  status: string
}

export interface SystemConfig {
  id: number
  configKey: string
  configValue: string
  description: string
  valueType: string
  minValue?: number
  maxValue?: number
  updatedAt: string
}

export interface StatsResponse {
  totalConversations: number
  unanswerableCount: number
  averageLatencyMs: number
  intentCounts: Record<string, number>
  popularPoints: Array<{ name: string; count: number }>
  frequentQuestions: Array<{ name: string; count: number }>
}

export interface ChatLog {
  id: number
  sessionId: string
  message: string
  inputType: string
  intent: string
  confidence: number
  pointId?: number
  answerStatus: string
  answer: string
  latencyMs: number
  feedback?: string
  createdAt: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

export interface AdminUser {
  token: string
  userId: number
  username: string
  displayName: string
  role: string
}
