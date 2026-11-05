<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type {
  CampusBuildingView,
  CameraView,
  PointView,
  PublicConfig,
  RoadEdgeView,
  RoadNodeView,
  RoutePlan,
} from '../types'

const props = defineProps<{
  points: PointView[]
  buildings: CampusBuildingView[]
  roadNodes: RoadNodeView[]
  roadEdges: RoadEdgeView[]
  cameras: CameraView[]
  config: PublicConfig
}>()

const emit = defineEmits<{
  pointSelected: [point: PointView]
  ready: []
}>()

type PointDimensions = {
  w: number
  d: number
  h: number
}

type LabelEntry = {
  pointId: number
  sprite: THREE.Sprite
  order: number
}

const host = ref<HTMLDivElement>()
let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let frameId = 0
let resizeObserver: ResizeObserver | null = null
let lastLabelUpdate = 0
const animationStartedAt = performance.now()
const pointGroups = new Map<number, THREE.Group>()
const pointLabels: LabelEntry[] = []
const proceduralBuildingParts: THREE.Object3D[] = []
const proceduralSceneParts: THREE.Object3D[] = []
const textureCache = new Map<string, THREE.CanvasTexture>()
const pointOrder = new Map<number, number>()
let selectedPointId: number | null = null
let highlight: {
  group: THREE.Group
  ring: THREE.Mesh
  cornerMarks: THREE.Mesh[]
  until: number
} | null = null
let routeGroup: THREE.Group | null = null
let routeMaterials: THREE.MeshStandardMaterial[] = []
let cameraFlight:
  | {
      start: THREE.Vector3
      end: THREE.Vector3
      startTarget: THREE.Vector3
      endTarget: THREE.Vector3
      startedAt: number
      durationMs: number
    }
  | null = null
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
let pointerDown = { x: 0, y: 0 }

const shared = {
  road: new THREE.MeshStandardMaterial({
    color: '#e5e8e3',
    roughness: 0.86,
    metalness: 0.05,
    transparent: true,
    opacity: 0.48,
  }),
  roadEdge: new THREE.MeshStandardMaterial({
    color: '#f4f5ef',
    roughness: 0.9,
    metalness: 0.02,
    transparent: true,
    opacity: 0.38,
  }),
  roadMark: new THREE.MeshStandardMaterial({
    color: '#e8dfc6',
    roughness: 0.72,
    emissive: '#5b4927',
    emissiveIntensity: 0.08,
  }),
  darkMetal: new THREE.MeshStandardMaterial({
    color: '#163b3d',
    roughness: 0.44,
    metalness: 0.36,
  }),
  warmMetal: new THREE.MeshStandardMaterial({
    color: '#c79d52',
    roughness: 0.38,
    metalness: 0.42,
  }),
  glass: new THREE.MeshPhysicalMaterial({
    color: '#6aa3a6',
    roughness: 0.18,
    metalness: 0.18,
    transparent: true,
    opacity: 0.7,
    transmission: 0.08,
  }),
}

function isDark(color: THREE.Color) {
  return color.r + color.g + color.b < 1.8
}

function seededRandom(seed: number) {
  let value = seed >>> 0
  return () => {
    value += 0x6d2b79f5
    let result = value
    result = Math.imul(result ^ (result >>> 15), result | 1)
    result ^= result + Math.imul(result ^ (result >>> 7), result | 61)
    return ((result ^ (result >>> 14)) >>> 0) / 4294967296
  }
}

function init() {
  if (!host.value) return
  scene = new THREE.Scene()
  scene.background = new THREE.Color('#081d20')
  scene.fog = new THREE.FogExp2('#0a2425', 0.0085)
  props.points.forEach((point, index) => pointOrder.set(point.id, index + 1))

  camera = new THREE.PerspectiveCamera(40, host.value.clientWidth / host.value.clientHeight, 0.1, 340)
  camera.position.set(2, 49, 58)

  renderer = new THREE.WebGLRenderer({
    antialias: true,
    powerPreference: 'high-performance',
    alpha: false,
  })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 1.8))
  renderer.setSize(host.value.clientWidth, host.value.clientHeight)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.18
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFShadowMap
  host.value.appendChild(renderer.domElement)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.065
  controls.target.set(0, 2, 0)
  controls.minDistance = 9
  controls.maxDistance = 105
  controls.maxPolarAngle = Math.PI * 0.47
  controls.minPolarAngle = Math.PI * 0.075
  controls.screenSpacePanning = false

  setupLighting()
  buildAtmosphere()
  buildGround()
  buildTreeGrove()
  buildRoads()
  buildPoints()
  loadBlenderBuildings()
  renderer.domElement.addEventListener('pointerdown', onPointerDown)
  renderer.domElement.addEventListener('pointerup', onPointerUp)
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(host.value)
  animate()
  emit('ready')
}

function setupLighting() {
  if (!scene) return
  const hemisphere = new THREE.HemisphereLight('#c4f3ea', '#071511', 1.65)
  scene.add(hemisphere)

  const sun = new THREE.DirectionalLight('#ffd9a3', 3.25)
  sun.position.set(-22, 38, 20)
  sun.castShadow = true
  sun.shadow.mapSize.set(2048, 2048)
  sun.shadow.camera.left = -46
  sun.shadow.camera.right = 46
  sun.shadow.camera.top = 46
  sun.shadow.camera.bottom = -46
  sun.shadow.camera.near = 1
  sun.shadow.camera.far = 100
  sun.shadow.bias = -0.00025
  sun.shadow.normalBias = 0.04
  scene.add(sun)

  const fill = new THREE.DirectionalLight('#7cc8bc', 1.15)
  fill.position.set(26, 14, -18)
  scene.add(fill)

  const rim = new THREE.DirectionalLight('#f2b65b', 0.9)
  rim.position.set(8, 12, 30)
  scene.add(rim)
}

function buildAtmosphere() {
  if (!scene) return
  const sky = new THREE.Mesh(
    new THREE.SphereGeometry(170, 32, 18),
    new THREE.ShaderMaterial({
      uniforms: {
        topColor: { value: new THREE.Color('#102f34') },
        horizonColor: { value: new THREE.Color('#0a2226') },
        bottomColor: { value: new THREE.Color('#061316') },
      },
      vertexShader: `
        varying vec3 vWorldPosition;
        void main() {
          vec4 worldPosition = modelMatrix * vec4(position, 1.0);
          vWorldPosition = worldPosition.xyz;
          gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
        }
      `,
      fragmentShader: `
        uniform vec3 topColor;
        uniform vec3 horizonColor;
        uniform vec3 bottomColor;
        varying vec3 vWorldPosition;
        void main() {
          float h = normalize(vWorldPosition).y;
          vec3 lower = mix(bottomColor, horizonColor, smoothstep(-0.12, 0.18, h));
          vec3 color = mix(lower, topColor, smoothstep(0.12, 0.82, h));
          gl_FragColor = vec4(color, 1.0);
        }
      `,
      side: THREE.BackSide,
      depthWrite: false,
    }),
  )
  scene.add(sky)

  const haze = new THREE.Mesh(
    new THREE.CircleGeometry(68, 64),
    new THREE.MeshBasicMaterial({
      color: '#2e6962',
      transparent: true,
      opacity: 0.08,
      depthWrite: false,
    }),
  )
  haze.rotation.x = -Math.PI / 2
  haze.position.y = -0.09
  scene.add(haze)
}

function getGroundTexture() {
  const cached = textureCache.get('ground')
  if (cached) return cached
  const canvas = document.createElement('canvas')
  canvas.width = 1024
  canvas.height = 1024
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#173c33'
  ctx.fillRect(0, 0, 1024, 1024)
  const random = seededRandom(421)
  for (let i = 0; i < 18000; i++) {
    const shade = 38 + Math.floor(random() * 34)
    const alpha = 0.018 + random() * 0.06
    ctx.fillStyle = `rgba(${shade - 10}, ${shade + 30}, ${shade + 10}, ${alpha})`
    const size = 0.5 + random() * 2.1
    ctx.fillRect(random() * 1024, random() * 1024, size, size)
  }
  for (let i = 0; i < 80; i++) {
    ctx.strokeStyle = `rgba(104, 145, 114, ${0.02 + random() * 0.03})`
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.arc(random() * 1024, random() * 1024, 30 + random() * 110, 0, Math.PI * 2)
    ctx.stroke()
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(10, 10)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = renderer?.capabilities.getMaxAnisotropy() ?? 1
  textureCache.set('ground', texture)
  return texture
}

function getPavingTexture() {
  const cached = textureCache.get('paving')
  if (cached) return cached
  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 512
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = '#b8b19e'
  ctx.fillRect(0, 0, 512, 512)
  ctx.strokeStyle = 'rgba(61, 78, 69, 0.18)'
  ctx.lineWidth = 2
  for (let x = 0; x <= 512; x += 64) {
    ctx.beginPath()
    ctx.moveTo(x, 0)
    ctx.lineTo(x, 512)
    ctx.stroke()
  }
  for (let y = 0; y <= 512; y += 64) {
    ctx.beginPath()
    ctx.moveTo(0, y)
    ctx.lineTo(512, y)
    ctx.stroke()
  }
  const random = seededRandom(81)
  for (let i = 0; i < 1800; i++) {
    ctx.fillStyle = `rgba(49, 59, 52, ${random() * 0.04})`
    ctx.fillRect(random() * 512, random() * 512, 1 + random() * 3, 1 + random() * 3)
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(8, 8)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = renderer?.capabilities.getMaxAnisotropy() ?? 1
  textureCache.set('paving', texture)
  return texture
}

function buildGround() {
  if (!scene) return
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(140, 140),
    new THREE.MeshStandardMaterial({
      map: getGroundTexture(),
      color: '#b2c6aa',
      roughness: 0.98,
      metalness: 0,
    }),
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.14
  ground.receiveShadow = true
  scene.add(ground)

  for (let i = 0; i < 18; i++) {
    const random = seededRandom(i + 17)
    const radius = 10 + random() * 17
    const patch = new THREE.Mesh(
      new THREE.CircleGeometry(radius, 44),
      new THREE.MeshStandardMaterial({
        color: i % 3 === 0 ? '#1a4337' : '#1e493c',
        roughness: 1,
        transparent: true,
        opacity: 0.36,
      }),
    )
    patch.rotation.x = -Math.PI / 2
    patch.scale.set(1 + random() * 0.5, 1, 0.65 + random() * 0.4)
    patch.position.set((random() - 0.5) * 90, -0.1, (random() - 0.5) * 90)
    scene.add(patch)
  }

  const plaza = new THREE.Mesh(
    new THREE.CircleGeometry(8.2, 72),
    new THREE.MeshStandardMaterial({
      map: getPavingTexture(),
      color: '#d2cbb9',
      roughness: 0.92,
    }),
  )
  plaza.rotation.x = -Math.PI / 2
  plaza.position.set(0, -0.015, 2)
  plaza.receiveShadow = true
  scene.add(plaza)


  const plazaInset = new THREE.Mesh(
    new THREE.RingGeometry(5.6, 5.72, 72),
    new THREE.MeshBasicMaterial({
      color: '#e5d9bd',
      transparent: true,
      opacity: 0.36,
      side: THREE.DoubleSide,
    }),
  )
  plazaInset.rotation.x = -Math.PI / 2
  plazaInset.position.set(0, 0.012, 2)
  scene.add(plazaInset)
}

type LandscapePlacement = {
  x: number
  z: number
  scale: number
}

function distanceToSegment(
  x: number,
  z: number,
  fromX: number,
  fromZ: number,
  toX: number,
  toZ: number,
) {
  const dx = toX - fromX
  const dz = toZ - fromZ
  const lengthSquared = dx * dx + dz * dz
  if (lengthSquared === 0) return Math.hypot(x - fromX, z - fromZ)
  const t = THREE.MathUtils.clamp(
    ((x - fromX) * dx + (z - fromZ) * dz) / lengthSquared,
    0,
    1,
  )
  return Math.hypot(x - (fromX + dx * t), z - (fromZ + dz * t))
}

function pointInsidePolygon(x: number, z: number, polygon: [number, number][]) {
  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, zi] = polygon[i]
    const [xj, zj] = polygon[j]
    const intersects =
      zi > z !== zj > z &&
      x < ((xj - xi) * (z - zi)) / (zj - zi || Number.EPSILON) + xi
    if (intersects) inside = !inside
  }
  return inside
}

function distanceToPolygon(x: number, z: number, polygon: [number, number][]) {
  let minimum = Number.POSITIVE_INFINITY
  for (let i = 0; i < polygon.length; i++) {
    const from = polygon[i]
    const to = polygon[(i + 1) % polygon.length]
    minimum = Math.min(minimum, distanceToSegment(x, z, from[0], from[1], to[0], to[1]))
  }
  return pointInsidePolygon(x, z, polygon) ? 0 : minimum
}

function isLandscapeClear(
  x: number,
  z: number,
  crownRadius: number,
  roadClearance: number,
) {
  if (Math.abs(x) > 61 || Math.abs(z) > 61) return false
  if (Math.hypot(x, z - 2) < 9.8 + crownRadius) return false
  if (props.buildings.some(
    (building) =>
      building.footprint.length >= 3 &&
      distanceToPolygon(x, z, building.footprint) < crownRadius + 0.9,
  )) {
    return false
  }

  for (const point of props.points) {
    const display = visualPosition(point)
    const dx = x - display.x
    const dz = z - display.z
    if (point.category === 'SPORTS') {
      const fieldX = 14.1 + crownRadius
      const fieldZ = 11.4 + crownRadius
      if ((dx * dx) / (fieldX * fieldX) + (dz * dz) / (fieldZ * fieldZ) < 1) {
        return false
      }
      continue
    }
    if (point.category === 'GATE') {
      if (Math.abs(dx) < 7.2 + crownRadius && Math.abs(dz) < 4 + crownRadius) {
        return false
      }
      continue
    }

    const dims = pointDimensions(point)
    const apronWidth = Math.max(5.8, dims.w * 0.78)
    const apronDepth = Math.max(4.4, dims.d * 0.76)
    const halfWidth = Math.max(dims.w / 2, apronWidth / 2) + crownRadius + 0.45
    const minDepth = -dims.d / 2 - crownRadius - 0.45
    const maxDepth = dims.d * 0.42 + apronDepth / 2 + crownRadius + 0.45
    if (Math.abs(dx) < halfWidth && dz > minDepth && dz < maxDepth) {
      return false
    }
  }

  const nodes = new Map(props.roadNodes.map((node) => [node.id, node]))
  for (const edge of props.roadEdges) {
    const from = nodes.get(edge.fromNodeId)
    const to = nodes.get(edge.toNodeId)
    if (!from || !to) continue
    if (distanceToSegment(x, z, from.x, from.z, to.x, to.z) < roadClearance) {
      return false
    }
  }
  return true
}

function roadsideCandidates(
  seed: number,
  offsetMin: number,
  offsetMax: number,
  spacing: number,
  crownRadius: number,
  roadClearance: number,
) {
  const random = seededRandom(seed)
  const nodes = new Map(props.roadNodes.map((node) => [node.id, node]))
  const candidates: LandscapePlacement[] = []
  for (const edge of props.roadEdges) {
    const from = nodes.get(edge.fromNodeId)
    const to = nodes.get(edge.toNodeId)
    if (!from || !to) continue
    const dx = to.x - from.x
    const dz = to.z - from.z
    const length = Math.hypot(dx, dz)
    if (length < 3.2) continue
    const tangentX = dx / length
    const tangentZ = dz / length
    const normalX = -tangentZ
    const normalZ = tangentX
    const margin = Math.min(2.1, length * 0.2)
    const usableLength = Math.max(0, length - margin * 2)
    const slots = Math.max(1, Math.floor(usableLength / spacing) + 1)

    for (let slot = 0; slot < slots; slot++) {
      const along =
        slots === 1
          ? length / 2
          : margin + (usableLength * slot) / Math.max(1, slots - 1)
      for (const side of [-1, 1]) {
        const offset = offsetMin + random() * (offsetMax - offsetMin)
        const scale = 0.72 + random() * 0.52
        const x = from.x + tangentX * along + normalX * side * offset
        const z = from.z + tangentZ * along + normalZ * side * offset
        if (!isLandscapeClear(x, z, crownRadius * scale, roadClearance)) continue
        candidates.push({ x, z, scale })
      }
    }
  }
  return candidates
}

function greenbeltCandidates(
  seed: number,
  count: number,
  crownRadius: number,
) {
  const random = seededRandom(seed)
  const candidates: LandscapePlacement[] = []
  for (let attempt = 0; attempt < count * 8 && candidates.length < count; attempt++) {
    const angle = random() * Math.PI * 2
    const radius = 35 + random() * 22
    const scale = 0.7 + random() * 0.55
    const x = Math.cos(angle) * radius
    const z = Math.sin(angle) * radius
    if (!isLandscapeClear(x, z, crownRadius * scale, 3.2)) continue
    candidates.push({ x, z, scale })
  }
  return candidates
}

function collectLandscapePlacements(
  seed: number,
  target: number,
  crownRadius: number,
  offsetMin: number,
  offsetMax: number,
  spacing: number,
  roadClearance: number,
) {
  const roadside = roadsideCandidates(
    seed,
    offsetMin,
    offsetMax,
    spacing,
    crownRadius,
    roadClearance,
  )
  const outer = greenbeltCandidates(seed + 7, target, crownRadius)
  const accepted: LandscapePlacement[] = []

  const tryAdd = (candidate: LandscapePlacement) => {
    const minSpacing = 2.2 + (candidate.scale + 0.85) * 0.72
    const overlaps = accepted.some(
      (placement) =>
        Math.hypot(candidate.x - placement.x, candidate.z - placement.z) <
        minSpacing + placement.scale * 0.28,
    )
    if (overlaps) return
    accepted.push(candidate)
  }

  roadside.slice(0, Math.round(target * 0.78)).forEach(tryAdd)
  outer.forEach((candidate) => {
    if (accepted.length < target) tryAdd(candidate)
  })
  roadside.forEach((candidate) => {
    if (accepted.length < target) tryAdd(candidate)
  })
  return accepted
}

function buildTreeGrove() {
  if (!scene) return
  const placements = collectLandscapePlacements(984, 88, 1.28, 4.25, 5.15, 4.7, 3.15)
  const count = placements.length
  const random = seededRandom(984)
  const trunk = new THREE.InstancedMesh(
    new THREE.CylinderGeometry(0.12, 0.22, 1.8, 7),
    new THREE.MeshStandardMaterial({ color: '#674b37', roughness: 1 }),
    count,
  )
  const crown = new THREE.InstancedMesh(
    new THREE.IcosahedronGeometry(1.28, 1),
    new THREE.MeshStandardMaterial({
      color: '#ffffff',
      roughness: 0.96,
      vertexColors: true,
    }),
    count,
  )
  const dummy = new THREE.Object3D()
  const color = new THREE.Color()

  for (let i = 0; i < count; i++) {
    const { x, z, scale } = placements[i]

    dummy.position.set(x, 0.78 * scale, z)
    dummy.rotation.set(0, random() * Math.PI, 0)
    dummy.scale.setScalar(scale)
    dummy.updateMatrix()
    trunk.setMatrixAt(i, dummy.matrix)

    dummy.position.set(x, 2.08 * scale, z)
    dummy.rotation.set(random() * 0.25, random() * Math.PI, random() * 0.18)
    dummy.scale.set(scale * (0.84 + random() * 0.24), scale * (0.9 + random() * 0.3), scale)
    dummy.updateMatrix()
    crown.setMatrixAt(i, dummy.matrix)
    color.setHSL(
      0.36 + random() * 0.055,
      0.3 + random() * 0.16,
      0.41 + random() * 0.12,
    )
    crown.setColorAt(i, color)
  }
  trunk.instanceMatrix.needsUpdate = true
  crown.instanceMatrix.needsUpdate = true
  crown.instanceColor!.needsUpdate = true
  trunk.castShadow = true
  trunk.receiveShadow = true
  crown.castShadow = true
  crown.receiveShadow = true
  scene.add(trunk, crown)
  proceduralSceneParts.push(trunk, crown)

  const shrubPlacements = collectLandscapePlacements(
    386,
    48,
    0.82,
    2.8,
    3.45,
    3.8,
    2.28,
  )
  const shrubCount = shrubPlacements.length
  const shrubs = new THREE.InstancedMesh(
    new THREE.IcosahedronGeometry(0.82, 0),
    new THREE.MeshStandardMaterial({ color: '#3d7159', roughness: 1 }),
    shrubCount,
  )
  for (let i = 0; i < shrubCount; i++) {
    const { x, z, scale } = shrubPlacements[i]
    dummy.position.set(x, 0.42 * scale, z)
    dummy.rotation.set(0, random() * Math.PI, 0)
    dummy.scale.set(scale * 1.05, scale * (0.5 + random() * 0.2), scale)
    dummy.updateMatrix()
    shrubs.setMatrixAt(i, dummy.matrix)
  }
  shrubs.instanceMatrix.needsUpdate = true
  shrubs.receiveShadow = true
  scene.add(shrubs)
  proceduralSceneParts.push(shrubs)
}

function buildRoads() {
  if (!scene) return
  const nodes = new Map(props.roadNodes.map((node) => [node.id, node]))
  const intersectionMaterial = shared.road.clone()
  intersectionMaterial.color.set('#98a39e')

  for (const edge of props.roadEdges) {
    const from = nodes.get(edge.fromNodeId)
    const to = nodes.get(edge.toNodeId)
    if (!from || !to) continue
    const dx = to.x - from.x
    const dz = to.z - from.z
    const length = Math.hypot(dx, dz)
    if (length < 0.01) continue

    const segment = new THREE.Group()
    segment.position.set((from.x + to.x) / 2, 0, (from.z + to.z) / 2)
    segment.rotation.y = -Math.atan2(dz, dx)

    const sidewalk = new THREE.Mesh(
      new THREE.BoxGeometry(length + 0.15, 0.1, 2.8),
      shared.roadEdge,
    )
    sidewalk.position.y = 0.02
    sidewalk.receiveShadow = true

    const road = new THREE.Mesh(new THREE.BoxGeometry(length + 0.12, 0.08, 2.1), shared.road)
    road.position.y = 0.08
    road.receiveShadow = true
    segment.add(sidewalk, road)

    const dashCount = Math.max(1, Math.floor(length / 3.2))
    for (let i = 0; i < dashCount; i++) {
      const dash = new THREE.Mesh(
        new THREE.BoxGeometry(Math.min(1.1, length / dashCount / 2), 0.025, 0.1),
        shared.roadMark,
      )
      dash.position.set(
        -length / 2 + (i + 0.5) * (length / dashCount),
        0.132,
        0,
      )
      segment.add(dash)
    }
    scene.add(segment)
    proceduralSceneParts.push(segment)
  }

  const degrees = new Map<number, number>()
  for (const edge of props.roadEdges) {
    degrees.set(edge.fromNodeId, (degrees.get(edge.fromNodeId) ?? 0) + 1)
    degrees.set(edge.toNodeId, (degrees.get(edge.toNodeId) ?? 0) + 1)
  }
  for (const node of props.roadNodes) {
    const degree = degrees.get(node.id) ?? 0
    if (degree < 2) continue
    const intersection = new THREE.Mesh(
      new THREE.CircleGeometry(1.35, 32),
      intersectionMaterial,
    )
    intersection.rotation.x = -Math.PI / 2
    intersection.position.set(node.x, 0.055, node.z)
    intersection.receiveShadow = true
    scene.add(intersection)
    proceduralSceneParts.push(intersection)
  }
}

function buildPoints() {
  if (!scene) return
  const linkedPointIds = new Set(
    props.buildings.map((building) => building.pointId).filter((id): id is number => id != null),
  )
  for (const building of props.buildings) {
    if (building.pointId == null || !linkedPointIds.has(building.pointId)) {
      const group = createFootprintBuilding(building)
      scene.add(group)
    }
  }

  for (const point of props.points) {
    const footprint = props.buildings.find((building) => building.pointId === point.id)
    const group =
      footprint && point.category !== 'GATE' && point.category !== 'SPORTS'
        ? createFootprintBuilding(footprint, point)
        : createPointModel(point)
    if (!footprint || point.category === 'GATE' || point.category === 'SPORTS') {
      const display = visualPosition(point)
      group.position.set(display.x, 0, display.z)
    }
    group.userData.pointId = point.id
    group.userData.pointName = point.name
    pointGroups.set(point.id, group)
    scene.add(group)
  }
}

function createFootprintBuilding(building: CampusBuildingView, point?: PointView) {
  const footprint = building.footprint
  const centerX = footprint.reduce((sum, value) => sum + value[0], 0) / footprint.length
  const centerZ = footprint.reduce((sum, value) => sum + value[1], 0) / footprint.length
  const group = new THREE.Group()
  group.position.set(centerX, 0, centerZ)

  const shape = new THREE.Shape()
  footprint.forEach(([x, z], index) => {
    const localX = x - centerX
    const localZ = -(z - centerZ)
    if (index === 0) shape.moveTo(localX, localZ)
    else shape.lineTo(localX, localZ)
  })
  shape.closePath()

  const height = Math.max(3.5, building.height || 6)
  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: height,
    bevelEnabled: false,
  })
  geometry.rotateX(-Math.PI / 2)
  geometry.computeVertexNormals()

  const material = new THREE.MeshStandardMaterial({
    color: building.colorHex || '#6D8E7D',
    roughness: 0.82,
    metalness: 0.08,
  })
  const mesh = new THREE.Mesh(geometry, material)
  mesh.castShadow = true
  mesh.receiveShadow = true
  group.add(mesh)

  const outline = new THREE.LineSegments(
    new THREE.EdgesGeometry(geometry, 28),
    new THREE.LineBasicMaterial({
      color: '#ead6a1',
      transparent: true,
      opacity: 0.22,
    }),
  )
  outline.position.y = 0.025
  group.add(outline)
  proceduralBuildingParts.push(mesh, outline)

  if (point) {
    const label = createLabel(point)
    label.position.set(0, height + 2.45, 0)
    group.add(label)
    pointLabels.push({
      pointId: point.id,
      sprite: label,
      order: pointOrder.get(point.id) ?? 99,
    })
    group.traverse((object) => {
      object.userData.pointId = point.id
    })
  }
  return group
}

function clearProceduralScene() {
  for (const part of proceduralSceneParts) {
    part.removeFromParent()
    disposeObject(part)
  }
  proceduralSceneParts.length = 0
}

function removeProceduralPointMeshes() {
  for (const group of pointGroups.values()) {
    for (const child of [...group.children]) {
      if (!(child as THREE.Sprite).isSprite) {
        child.removeFromParent()
        disposeObject(child)
      }
    }
  }
}

function fitPointLabels(root: THREE.Object3D) {
  root.updateWorldMatrix(true, true)
  const boxes: THREE.Box3[] = []
  root.traverse((object) => {
    const mesh = object as THREE.Mesh
    const role = String(object.userData.campusRole ?? '')
    if (!mesh.isMesh || !mesh.geometry || (role !== 'building' && role !== 'gate')) return
    mesh.geometry.computeBoundingBox()
    if (!mesh.geometry.boundingBox) return
    boxes.push(mesh.geometry.boundingBox.clone().applyMatrix4(mesh.matrixWorld))
  })
  for (const group of pointGroups.values()) {
    const px = group.position.x
    const pz = group.position.z
    let nearest: THREE.Box3 | null = null
    let nearestDistance = 6.5
    for (const box of boxes) {
      const cx = (box.min.x + box.max.x) / 2
      const cz = (box.min.z + box.max.z) / 2
      const distance = Math.hypot(cx - px, cz - pz)
      if (distance < nearestDistance) {
        nearestDistance = distance
        nearest = box
      }
    }
    const labelY = nearest ? nearest.max.y + 1.05 : 2.2
    for (const child of group.children) {
      if ((child as THREE.Sprite).isSprite) child.position.y = labelY
    }
  }
}

function loadBlenderBuildings() {
  if (!scene) return
  new GLTFLoader().load(
    '/models/campus-buildings.glb',
    (gltf) => {
      for (const part of proceduralBuildingParts) {
        part.removeFromParent()
        disposeObject(part)
      }
      proceduralBuildingParts.length = 0
      clearProceduralScene()
      removeProceduralPointMeshes()
      gltf.scene.traverse((object) => {
        const pointId = Number(object.userData.pointId)
        if (pointId > 0) object.userData.pointId = pointId
        object.userData.proceduralBuilding = undefined
        const drawable = object as THREE.Mesh
        if (drawable.isMesh) {
          drawable.castShadow = true
          drawable.receiveShadow = true
        }
      })
      gltf.scene.name = 'BlenderCampusBuildings'
      scene?.add(gltf.scene)
      fitPointLabels(gltf.scene)
    },
    undefined,
    () => {
      // The procedural campus remains available if the optional GLB is missing.
    },
  )
}

function visualPosition(point: PointView) {
  const offsets: Record<number, [number, number]> = {
    4: [-1.7, -1.1],
    9: [1.2, 1.1],
  }
  const offset = offsets[point.id] ?? [0, 0]
  return { x: point.x + offset[0], z: point.z + offset[1] }
}

function pointDimensions(point: PointView): PointDimensions {
  const h = point.buildingHeight || 7
  if (point.category === 'LIBRARY') return { w: 10.4, d: 7, h: Math.max(h, 8.5) }
  if (point.category === 'CANTEEN') return { w: 9, d: 6.2, h: Math.max(h, 5.5) }
  if (point.category === 'DORM') return { w: 5.8, d: 5, h: Math.max(h, 7) }
  if (point.category === 'SERVICE') return { w: 7.2, d: 5.8, h: Math.max(h, 6) }
  if (point.category === 'ADMIN') return { w: 8.4, d: 6, h: Math.max(h, 8) }
  return { w: 7.4, d: 5.8, h: Math.max(h, 7) }
}

function createPointModel(point: PointView) {
  const group = new THREE.Group()
  const dims = pointDimensions(point)

  if (point.category === 'SPORTS') {
    createSportsField(group, point)
  } else if (point.category === 'GATE') {
    createGate(group, point)
  } else {
    createBuilding(group, point, dims)
  }

  createPointGround(group, point, dims)
  const label = createLabel(point)
  const labelY =
    point.category === 'SPORTS'
      ? 2.7
      : point.category === 'GATE'
        ? 7.4
        : dims.h + 2.55
  label.position.set(0, labelY, 0)
  group.add(label)
  pointLabels.push({
    pointId: point.id,
    sprite: label,
    order: pointOrder.get(point.id) ?? 99,
  })

  group.traverse((object) => {
    object.userData.pointId = point.id
  })
  return group
}

function createPointGround(group: THREE.Group, point: PointView, dims: PointDimensions) {
  if (point.category === 'SPORTS') {
    const apron = new THREE.Mesh(
      new THREE.CircleGeometry(11, 64),
      new THREE.MeshStandardMaterial({
        color: '#8f9b91',
        roughness: 0.96,
        transparent: true,
        opacity: 0.66,
      }),
    )
    apron.rotation.x = -Math.PI / 2
    apron.scale.set(1.25, 0.72, 1)
    apron.position.y = -0.01
    apron.receiveShadow = true
    group.add(apron)
    return
  }
  const apronWidth =
    point.category === 'GATE' ? 10.5 : Math.max(5.8, dims.w * 0.78)
  const apronDepth = point.category === 'GATE' ? 4.8 : Math.max(4.4, dims.d * 0.76)
  const apron = new THREE.Mesh(
    new THREE.BoxGeometry(apronWidth, 0.1, apronDepth),
    new THREE.MeshStandardMaterial({
      map: getPavingTexture(),
      color: '#c6c0ae',
      roughness: 0.94,
    }),
  )
  apron.position.set(0, 0.01, dims.d * 0.42)
  apron.receiveShadow = true
  group.add(apron)
}

function createFacadeTexture(point: PointView) {
  const cacheKey = `facade-${point.id}-${point.colorHex}`
  const cached = textureCache.get(cacheKey)
  if (cached) return cached

  const canvas = document.createElement('canvas')
  canvas.width = 512
  canvas.height = 512
  const ctx = canvas.getContext('2d')!
  const base = new THREE.Color(point.colorHex || '#668477')
  const random = seededRandom(point.id * 117 + 7)
  ctx.fillStyle = `#${base.getHexString()}`
  ctx.fillRect(0, 0, 512, 512)

  for (let y = 0; y < 512; y += 8) {
    ctx.fillStyle = `rgba(255, 255, 255, ${random() * 0.022})`
    ctx.fillRect(0, y, 512, 1)
  }
  ctx.strokeStyle = 'rgba(16, 38, 37, 0.2)'
  ctx.lineWidth = 3
  for (let x = 0; x <= 512; x += 128) {
    ctx.beginPath()
    ctx.moveTo(x, 0)
    ctx.lineTo(x, 512)
    ctx.stroke()
  }
  for (let y = 0; y <= 512; y += 102) {
    ctx.beginPath()
    ctx.moveTo(0, y)
    ctx.lineTo(512, y)
    ctx.stroke()
  }

  for (let row = 0; row < 5; row++) {
    for (let col = 0; col < 4; col++) {
      const x = 25 + col * 128
      const y = 19 + row * 102
      const glow = random() > 0.77
      const gradient = ctx.createLinearGradient(x, y, x + 80, y + 58)
      if (glow) {
        gradient.addColorStop(0, 'rgba(246, 199, 112, 0.72)')
        gradient.addColorStop(1, 'rgba(102, 104, 76, 0.6)')
      } else {
        gradient.addColorStop(0, 'rgba(94, 153, 155, 0.78)')
        gradient.addColorStop(0.55, 'rgba(31, 73, 78, 0.9)')
        gradient.addColorStop(1, 'rgba(11, 36, 40, 0.94)')
      }
      ctx.fillStyle = gradient
      ctx.fillRect(x, y, 78, 58)
      ctx.fillStyle = 'rgba(219, 228, 209, 0.18)'
      ctx.fillRect(x + 3, y + 3, 2, 52)
    }
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = THREE.RepeatWrapping
  texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(1.25, 1)
  texture.anisotropy = renderer?.capabilities.getMaxAnisotropy() ?? 1
  textureCache.set(cacheKey, texture)
  return texture
}

function createBuilding(group: THREE.Group, point: PointView, dims: PointDimensions) {
  const baseColor = new THREE.Color(point.colorHex || '#668477')
  const roofColor = isDark(baseColor) ? '#163b3b' : '#6b675b'
  const trimColor = isDark(baseColor) ? '#d8c9a6' : '#e3d8bd'
  const facadeMaterial = new THREE.MeshStandardMaterial({
    map: createFacadeTexture(point),
    roughness: 0.7,
    metalness: 0.06,
  })
  const sideMaterial = new THREE.MeshStandardMaterial({
    color: baseColor.clone().multiplyScalar(0.84),
    roughness: 0.76,
    metalness: 0.04,
  })
  const roofMaterial = new THREE.MeshStandardMaterial({
    color: roofColor,
    roughness: 0.62,
    metalness: 0.14,
  })
  const trimMaterial = new THREE.MeshStandardMaterial({
    color: trimColor,
    roughness: 0.62,
    metalness: 0.08,
  })

  const base = new THREE.Mesh(
    new THREE.BoxGeometry(dims.w + 0.72, 0.46, dims.d + 0.72),
    new THREE.MeshStandardMaterial({
      color: '#77776d',
      roughness: 0.84,
      metalness: 0.08,
    }),
  )
  base.position.y = 0.23
  base.castShadow = true
  base.receiveShadow = true

  const body = new THREE.Mesh(
    new THREE.BoxGeometry(dims.w, dims.h, dims.d),
    [sideMaterial, sideMaterial, roofMaterial, sideMaterial, facadeMaterial, sideMaterial],
  )
  body.position.y = dims.h / 2 + 0.44
  body.castShadow = true
  body.receiveShadow = true
  group.add(base, body)

  const parapet = new THREE.Mesh(
    new THREE.BoxGeometry(dims.w + 0.48, 0.38, dims.d + 0.48),
    roofMaterial,
  )
  parapet.position.y = dims.h + 0.55
  parapet.castShadow = true
  group.add(parapet)

  const trimBand = new THREE.Mesh(
    new THREE.BoxGeometry(dims.w + 0.18, 0.16, dims.d + 0.18),
    trimMaterial,
  )
  trimBand.position.y = dims.h + 0.36
  group.add(trimBand)

  createWindowGrid(group, dims)
  createEntrance(group, point, dims)
  addFacadeDetails(group, point, dims, roofMaterial, trimMaterial)
  addRoofDetails(group, point, dims, roofMaterial)
}

function createWindowGrid(group: THREE.Group, dims: PointDimensions) {
  const frontColumns = Math.max(2, Math.floor((dims.w - 1.4) / 1.3))
  const rows = Math.max(2, Math.floor((dims.h - 1.7) / 1.55))
  const sideColumns = Math.max(1, Math.floor((dims.d - 1.2) / 1.4))
  const total = frontColumns * rows * 2 + sideColumns * rows * 2
  const windows = new THREE.InstancedMesh(
    new THREE.BoxGeometry(0.78, 0.66, 0.09),
    new THREE.MeshStandardMaterial({
      color: '#ffffff',
      roughness: 0.2,
      metalness: 0.32,
      emissive: '#0b2529',
      emissiveIntensity: 0.12,
      vertexColors: true,
    }),
    total,
  )
  const dummy = new THREE.Object3D()
  const color = new THREE.Color()
  const random = seededRandom(Math.round(dims.w * 1000 + dims.h * 79))
  let index = 0

  for (const z of [dims.d / 2 + 0.065, -dims.d / 2 - 0.065]) {
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < frontColumns; col++) {
        dummy.position.set(
          -dims.w / 2 + ((col + 1) * dims.w) / (frontColumns + 1),
          1.42 + row * ((dims.h - 2.2) / Math.max(1, rows - 1)),
          z,
        )
        dummy.rotation.set(0, 0, 0)
        dummy.scale.set(1, 1, 1)
        dummy.updateMatrix()
        windows.setMatrixAt(index, dummy.matrix)
        color.set(random() > 0.84 ? '#e6b86a' : '#4f8a8c')
        windows.setColorAt(index, color)
        index++
      }
    }
  }

  for (const x of [dims.w / 2 + 0.065, -dims.w / 2 - 0.065]) {
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < sideColumns; col++) {
        dummy.position.set(
          x,
          1.42 + row * ((dims.h - 2.2) / Math.max(1, rows - 1)),
          -dims.d / 2 + ((col + 1) * dims.d) / (sideColumns + 1),
        )
        dummy.rotation.set(0, Math.PI / 2, 0)
        dummy.scale.set(1, 1, 1)
        dummy.updateMatrix()
        windows.setMatrixAt(index, dummy.matrix)
        color.set(random() > 0.88 ? '#d9a85e' : '#467d80')
        windows.setColorAt(index, color)
        index++
      }
    }
  }
  windows.instanceMatrix.needsUpdate = true
  windows.instanceColor!.needsUpdate = true
  windows.receiveShadow = true
  group.add(windows)
}

function createEntrance(
  group: THREE.Group,
  point: PointView,
  dims: PointDimensions,
) {
  const front = dims.d / 2 + 0.06
  const entranceWidth = Math.min(3.2, dims.w * 0.38)
  const entranceHeight = Math.min(2.2, dims.h * 0.34)
  const door = new THREE.Mesh(
    new THREE.BoxGeometry(entranceWidth, entranceHeight, 0.13),
    new THREE.MeshPhysicalMaterial({
      color: '#1c5557',
      roughness: 0.18,
      metalness: 0.18,
      transparent: true,
      opacity: 0.86,
    }),
  )
  door.position.set(0, entranceHeight / 2 + 0.48, front)
  door.castShadow = true
  group.add(door)

  const lintel = new THREE.Mesh(
    new THREE.BoxGeometry(entranceWidth + 1.8, 0.18, 1.5),
    shared.darkMetal,
  )
  lintel.position.set(0, entranceHeight + 0.65, front + 0.43)
  lintel.castShadow = true
  group.add(lintel)
  for (const x of [-entranceWidth / 2 - 0.5, entranceWidth / 2 + 0.5]) {
    const post = new THREE.Mesh(
      new THREE.BoxGeometry(0.12, entranceHeight + 0.15, 0.12),
      shared.warmMetal,
    )
    post.position.set(x, entranceHeight / 2 + 0.48, front + 1.05)
    group.add(post)
  }

  for (let i = 0; i < 3; i++) {
    const step = new THREE.Mesh(
      new THREE.BoxGeometry(entranceWidth + 1.8 - i * 0.38, 0.08, 0.5 + i * 0.35),
      shared.roadEdge,
    )
    step.position.set(0, 0.48 - i * 0.08, front + 0.65 + i * 0.22)
    step.receiveShadow = true
    group.add(step)
  }

  if (point.category === 'LIBRARY' || point.category === 'ADMIN') {
    const glassBay = new THREE.Mesh(
      new THREE.BoxGeometry(dims.w * 0.34, dims.h * 0.62, 0.16),
      shared.glass,
    )
    glassBay.position.set(0, dims.h * 0.53, front + 0.09)
    group.add(glassBay)
    for (let i = -2; i <= 2; i++) {
      const fin = new THREE.Mesh(
        new THREE.BoxGeometry(0.08, dims.h * 0.62, 0.25),
        shared.warmMetal,
      )
      fin.position.set((i * dims.w * 0.05), dims.h * 0.53, front + 0.18)
      group.add(fin)
    }
  }
}

function addFacadeDetails(
  group: THREE.Group,
  point: PointView,
  dims: PointDimensions,
  roofMaterial: THREE.Material,
  trimMaterial: THREE.Material,
) {
  const front = dims.d / 2 + 0.08
  const side = dims.w / 2 + 0.08

  if (point.category === 'DORM') {
    const floors = Math.max(2, Math.floor(dims.h / 1.9))
    for (let floor = 1; floor < floors; floor++) {
      const balcony = new THREE.Mesh(
        new THREE.BoxGeometry(dims.w * 0.72, 0.11, 0.76),
        shared.roadEdge,
      )
      balcony.position.set(0, 0.8 + floor * 1.82, front + 0.35)
      balcony.castShadow = true
      balcony.receiveShadow = true
      group.add(balcony)
    }
    for (const x of [-dims.w * 0.42, dims.w * 0.42]) {
      const vertical = new THREE.Mesh(
        new THREE.BoxGeometry(0.17, dims.h + 0.8, 0.25),
        trimMaterial,
      )
      vertical.position.set(x, dims.h / 2 + 0.45, front + 0.12)
      group.add(vertical)
    }
  } else if (point.category === 'CANTEEN') {
    const canopy = new THREE.Mesh(
      new THREE.BoxGeometry(dims.w * 0.86, 0.16, 2.1),
      new THREE.MeshStandardMaterial({
        color: '#e3d7bd',
        roughness: 0.65,
        metalness: 0.12,
      }),
    )
    canopy.position.set(0, dims.h * 0.42, front + 0.7)
    canopy.castShadow = true
    group.add(canopy)
    for (let i = -2; i <= 2; i++) {
      const panel = new THREE.Mesh(
        new THREE.BoxGeometry(dims.w * 0.11, dims.h * 0.28, 0.1),
        new THREE.MeshStandardMaterial({
          color: i % 2 === 0 ? '#c5894d' : '#284f4b',
          roughness: 0.55,
          metalness: 0.12,
        }),
      )
      panel.position.set((i * dims.w) / 5, dims.h * 0.71, front + 0.04)
      group.add(panel)
    }
  } else if (point.category === 'LAB') {
    for (const x of [-1.2, 1.2]) {
      const duct = new THREE.Mesh(
        new THREE.CylinderGeometry(0.3, 0.38, 2.4, 12),
        shared.darkMetal,
      )
      duct.position.set(x, dims.h + 1.25, -dims.d * 0.18)
      duct.castShadow = true
      group.add(duct)
    }
  } else if (point.category === 'TEACHING') {
    for (let i = -2; i <= 2; i++) {
      const fin = new THREE.Mesh(
        new THREE.BoxGeometry(0.09, dims.h * 0.84, 0.42),
        trimMaterial,
      )
      fin.position.set(i * dims.w * 0.11, dims.h * 0.52, front + 0.16)
      fin.castShadow = true
      group.add(fin)
    }
  }

  if (point.category === 'LIBRARY') {
    const drum = new THREE.Mesh(
      new THREE.CylinderGeometry(dims.w * 0.21, dims.w * 0.21, dims.h * 0.7, 28, 1, true),
      shared.glass,
    )
    drum.position.set(0, dims.h * 0.52, front - 0.45)
    drum.scale.z = 0.42
    group.add(drum)
    const crown = new THREE.Mesh(
      new THREE.CylinderGeometry(dims.w * 0.25, dims.w * 0.25, 0.22, 28),
      roofMaterial,
    )
    crown.position.set(0, dims.h + 0.82, front - 0.45)
    crown.scale.z = 0.45
    group.add(crown)
  }

  for (const x of [-side, side]) {
    const sideSeam = new THREE.Mesh(
      new THREE.BoxGeometry(0.12, dims.h * 0.78, dims.d * 0.92),
      shared.darkMetal,
    )
    sideSeam.position.set(x, dims.h * 0.5 + 0.45, 0)
    group.add(sideSeam)
  }
}

function addRoofDetails(
  group: THREE.Group,
  point: PointView,
  dims: PointDimensions,
  roofMaterial: THREE.Material,
) {
  const roofY = dims.h + 0.63
  const box = new THREE.Mesh(
    new THREE.BoxGeometry(Math.min(2.1, dims.w * 0.28), 0.7, Math.min(1.5, dims.d * 0.24)),
    roofMaterial,
  )
  box.position.set(dims.w * 0.18, roofY + 0.35, -dims.d * 0.13)
  box.castShadow = true
  group.add(box)

  const rim = new THREE.Mesh(
    new THREE.BoxGeometry(dims.w * 0.36, 0.07, dims.d * 0.3),
    shared.warmMetal,
  )
  rim.position.set(-dims.w * 0.15, roofY + 0.03, dims.d * 0.1)
  group.add(rim)

  if (point.category === 'SERVICE' || point.category === 'ADMIN') {
    const solar = new THREE.Mesh(
      new THREE.BoxGeometry(dims.w * 0.46, 0.06, dims.d * 0.3),
      new THREE.MeshStandardMaterial({
        color: '#234a51',
        roughness: 0.3,
        metalness: 0.35,
      }),
    )
    solar.position.set(-dims.w * 0.12, roofY + 0.11, 0)
    solar.rotation.x = -0.1
    group.add(solar)
  }
}

function createSportsField(group: THREE.Group, point: PointView) {
  const field = new THREE.Mesh(
    new THREE.CircleGeometry(6.3, 64),
    new THREE.MeshStandardMaterial({ color: '#3f8059', roughness: 1 }),
  )
  field.rotation.x = -Math.PI / 2
  field.scale.set(1.6, 1, 1)
  field.position.y = 0.055
  field.receiveShadow = true

  const track = new THREE.Mesh(
    new THREE.RingGeometry(6.4, 8.1, 64),
    new THREE.MeshStandardMaterial({ color: '#b06e4c', roughness: 0.9 }),
  )
  track.rotation.x = -Math.PI / 2
  track.scale.set(1.62, 1, 1.02)
  track.position.y = 0.04
  track.receiveShadow = true

  const lineMaterial = new THREE.MeshBasicMaterial({
    color: '#e9e4cf',
    transparent: true,
    opacity: 0.72,
  })
  for (const scale of [0.77, 0.93]) {
    const lane = new THREE.Mesh(
      new THREE.RingGeometry(6.3 * scale, 6.38 * scale, 64),
      lineMaterial,
    )
    lane.rotation.x = -Math.PI / 2
    lane.scale.set(1.62, 1, 1.02)
    lane.position.y = 0.067
    group.add(lane)
  }
  const middle = new THREE.Mesh(new THREE.PlaneGeometry(8.3, 0.08), lineMaterial)
  middle.rotation.x = -Math.PI / 2
  middle.position.y = 0.069
  const middleTwo = middle.clone()
  middleTwo.rotation.z = Math.PI / 2
  group.add(field, track, middle, middleTwo)

  const stand = new THREE.Mesh(
    new THREE.BoxGeometry(6.2, 0.72, 1.15),
    new THREE.MeshStandardMaterial({ color: '#6e806f', roughness: 0.82 }),
  )
  stand.position.set(-8.3, 0.36, 1.4)
  stand.rotation.y = 0.24
  stand.castShadow = true
  group.add(stand)

  const flagPole = new THREE.Mesh(
    new THREE.CylinderGeometry(0.04, 0.05, 3.4, 8),
    shared.darkMetal,
  )
  flagPole.position.set(8.3, 1.75, -2.7)
  const flag = new THREE.Mesh(
    new THREE.PlaneGeometry(1.1, 0.66),
    new THREE.MeshStandardMaterial({
      color: point.colorHex || '#d9b465',
      side: THREE.DoubleSide,
      roughness: 0.8,
    }),
  )
  flag.position.set(8.77, 3.18, -2.7)
  flag.rotation.y = 0.18
  group.add(flagPole, flag)
}

function createGate(group: THREE.Group, point: PointView) {
  const color = new THREE.Color(point.colorHex || '#2f6b63')
  const stone = new THREE.MeshStandardMaterial({ color: '#b8b09b', roughness: 0.72 })
  const pillarMaterial = new THREE.MeshStandardMaterial({
    color,
    roughness: 0.58,
    metalness: 0.1,
  })
  for (const x of [-4.1, 4.1]) {
    const pillar = new THREE.Mesh(new THREE.BoxGeometry(1.05, 5.25, 1.15), pillarMaterial)
    pillar.position.set(x, 2.62, 0)
    pillar.castShadow = true
    const cap = new THREE.Mesh(new THREE.BoxGeometry(1.35, 0.27, 1.45), stone)
    cap.position.set(x, 5.32, 0)
    cap.castShadow = true
    group.add(pillar, cap)
  }
  const beam = new THREE.Mesh(new THREE.BoxGeometry(9.4, 0.35, 0.78), stone)
  beam.position.y = 4.86
  beam.castShadow = true
  const sign = new THREE.Mesh(
    new THREE.BoxGeometry(5.1, 0.8, 0.24),
    new THREE.MeshStandardMaterial({
      color: '#153b3b',
      roughness: 0.45,
      metalness: 0.2,
    }),
  )
  sign.position.set(0, 5.36, 0.34)
  const accent = new THREE.Mesh(new THREE.BoxGeometry(3.15, 0.08, 0.06), shared.warmMetal)
  accent.position.set(0, 5.12, 0.48)
  const booth = new THREE.Mesh(new THREE.BoxGeometry(1.8, 2.2, 2.2), shared.glass)
  booth.position.set(6.15, 1.1, -0.35)
  booth.castShadow = true
  group.add(beam, sign, accent, booth)
}

function drawRoundedRect(
  ctx: CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
) {
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + width - radius, y)
  ctx.quadraticCurveTo(x + width, y, x + width, y + radius)
  ctx.lineTo(x + width, y + height - radius)
  ctx.quadraticCurveTo(x + width, y + height, x + width - radius, y + height)
  ctx.lineTo(x + radius, y + height)
  ctx.quadraticCurveTo(x, y + height, x, y + height - radius)
  ctx.lineTo(x, y + radius)
  ctx.quadraticCurveTo(x, y, x + radius, y)
  ctx.closePath()
}

function createLabel(point: PointView) {
  const canvas = document.createElement('canvas')
  canvas.width = 768
  canvas.height = 160
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  ctx.shadowColor = 'rgba(0, 0, 0, 0.4)'
  ctx.shadowBlur = 18
  ctx.shadowOffsetY = 6
  drawRoundedRect(ctx, 24, 20, 720, 112, 15)
  ctx.fillStyle = 'rgba(6, 27, 29, 0.92)'
  ctx.fill()
  ctx.shadowColor = 'transparent'

  ctx.fillStyle = '#cfa85d'
  drawRoundedRect(ctx, 24, 20, 112, 112, 15)
  ctx.fill()
  ctx.fillStyle = 'rgba(7, 30, 31, 0.92)'
  drawRoundedRect(ctx, 25, 21, 78, 110, 14)
  ctx.fill()

  ctx.fillStyle = '#f4ead2'
  ctx.font = '600 34px "Cascadia Mono", Consolas, monospace'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(String(pointOrder.get(point.id) ?? 0).padStart(2, '0'), 83, 77)

  ctx.textAlign = 'left'
  ctx.font = '600 46px "Microsoft YaHei", sans-serif'
  const name = point.name.length > 12 ? `${point.name.slice(0, 12)}…` : point.name
  ctx.fillText(name, 162, 65)
  ctx.fillStyle = '#cfa85d'
  ctx.font = '600 25px "Cascadia Mono", Consolas, monospace'
  ctx.fillText(point.category, 163, 104)

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  const sprite = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: texture,
      transparent: true,
      depthTest: false,
      depthWrite: false,
    }),
  )
  sprite.scale.set(6.3, 1.31, 1)
  sprite.center.set(0.5, 0.08)
  sprite.renderOrder = 20
  sprite.userData.pointId = point.id
  return sprite
}

function onPointerDown(event: PointerEvent) {
  pointerDown = { x: event.clientX, y: event.clientY }
}

function onPointerUp(event: PointerEvent) {
  if (!renderer || !camera || !scene) return
  if (Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y) > 6) return
  const rect = renderer.domElement.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hits = raycaster.intersectObjects(scene.children, true)
  for (const hit of hits) {
    let object: THREE.Object3D | null = hit.object
    while (object) {
      const id = object.userData.pointId as number | undefined
      if (id) {
        const point = props.points.find((item) => item.id === id)
        if (point) emit('pointSelected', point)
        return
      }
      object = object.parent
    }
  }
}

function focusPoint(pointId: number) {
  if (!camera || !controls) return
  const point = props.points.find((item) => item.id === pointId)
  const group = pointGroups.get(pointId)
  if (!point || !group) return
  selectPointVisual(pointId)
  const dims = pointDimensions(point)
  const footprint = point.category === 'SPORTS' ? 20 : Math.max(dims.w, dims.d)
  const height = point.category === 'SPORTS' ? 1 : dims.h
  const distance = point.category === 'SPORTS' ? 31 : Math.max(23, footprint * 2 + height * 0.75)
  const angle = 0.58
  flyTo(
    new THREE.Vector3(
      group.position.x + Math.sin(angle) * distance,
      Math.max(10, height * 0.95 + 5),
      group.position.z + Math.cos(angle) * distance,
    ),
    new THREE.Vector3(
      group.position.x,
      point.category === 'SPORTS' ? 0.4 : height * 0.43,
      group.position.z,
    ),
  )
}

function setCamera(preset: CameraView) {
  flyTo(
    new THREE.Vector3(preset.x, preset.y, preset.z),
    new THREE.Vector3(preset.targetX, preset.targetY, preset.targetZ),
  )
}

function resetCamera() {
  const overview = props.cameras.find((item) => item.key === 'overview')
  if (overview) setCamera(overview)
  else flyTo(new THREE.Vector3(2, 49, 58), new THREE.Vector3(0, 2, -2))
}

function flyTo(position: THREE.Vector3, target: THREE.Vector3) {
  if (!camera || !controls) return
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  cameraFlight = {
    start: camera.position.clone(),
    end: position,
    startTarget: controls.target.clone(),
    endTarget: target,
    startedAt: performance.now(),
    durationMs: reduced ? 40 : Math.max(300, props.config.cameraFlightSeconds * 1000),
  }
  controls.enabled = false
}

function selectPointVisual(pointId: number) {
  selectedPointId = pointId
  clearHighlight()
  const point = props.points.find((item) => item.id === pointId)
  const group = pointGroups.get(pointId)
  if (!point || !group || !scene) return
  const dims = pointDimensions(point)
  const radius =
    point.category === 'SPORTS'
      ? 10.5
      : point.category === 'GATE'
        ? 5.5
        : Math.max(dims.w, dims.d) * 0.68 + 0.8
  const highlightGroup = new THREE.Group()
  const ring = new THREE.Mesh(
    new THREE.RingGeometry(radius, radius + 0.16, 64),
    new THREE.MeshBasicMaterial({
      color: '#f2bf66',
      transparent: true,
      opacity: 0.82,
      side: THREE.DoubleSide,
      depthWrite: false,
    }),
  )
  ring.rotation.x = -Math.PI / 2
  ring.position.y = 0.15
  highlightGroup.add(ring)

  const cornerMarks: THREE.Mesh[] = []
  for (let i = 0; i < 4; i++) {
    const angle = Math.PI / 4 + i * (Math.PI / 2)
    const mark = new THREE.Mesh(
      new THREE.BoxGeometry(0.55, 0.1, 0.12),
      new THREE.MeshBasicMaterial({
        color: '#ffe0a1',
        transparent: true,
        opacity: 0.9,
      }),
    )
    mark.position.set(Math.cos(angle) * radius, 0.22, Math.sin(angle) * radius)
    mark.rotation.y = -angle + Math.PI / 2
    highlightGroup.add(mark)
    cornerMarks.push(mark)
  }
  highlightGroup.position.copy(group.position)
  scene.add(highlightGroup)
  highlight = {
    group: highlightGroup,
    ring,
    cornerMarks,
    until: performance.now() + Math.max(2200, props.config.highlightMs),
  }
}

function clearHighlight() {
  if (highlight && scene) {
    scene.remove(highlight.group)
    disposeObject(highlight.group)
    highlight = null
  }
}

function showRoute(route: RoutePlan) {
  clearRoute()
  if (!scene) return
  routeGroup = new THREE.Group()
  const points = route.path.map(([x, z]) => new THREE.Vector3(x, 0.23, z))
  routeMaterials = []

  if (points.length > 1) {
    const curve = new THREE.CatmullRomCurve3(points, false, 'centripetal', 0.18)
    const routeMaterial = new THREE.MeshStandardMaterial({
      color: '#f4bf61',
      emissive: '#d88b2b',
      emissiveIntensity: 1.8,
      roughness: 0.28,
      metalness: 0.16,
      transparent: true,
      opacity: 0.94,
    })
    routeMaterials.push(routeMaterial)
    const tube = new THREE.Mesh(
      new THREE.TubeGeometry(curve, Math.max(24, points.length * 16), 0.16, 8, false),
      routeMaterial,
    )
    routeGroup.add(tube)

    for (let i = 1; i < points.length; i++) {
      const from = points[i - 1]
      const to = points[i]
      const direction = to.clone().sub(from)
      if (direction.length() < 0.01) continue
      direction.normalize()
      const arrow = new THREE.Mesh(
        new THREE.ConeGeometry(0.35, 0.82, 12),
        new THREE.MeshStandardMaterial({
          color: '#ffe3a8',
          emissive: '#f1ad46',
          emissiveIntensity: 1.4,
          roughness: 0.35,
        }),
      )
      arrow.position.copy(from.clone().lerp(to, 0.55))
      arrow.position.y = 0.27
      arrow.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), direction)
      routeGroup.add(arrow)
    }
  }

  if (points.length > 0) {
    const start = points[0]
    const end = points[points.length - 1]
    const startMarker = new THREE.Mesh(
      new THREE.RingGeometry(0.42, 0.58, 32),
      new THREE.MeshBasicMaterial({
        color: '#8de0ca',
        transparent: true,
        opacity: 0.9,
        side: THREE.DoubleSide,
      }),
    )
    startMarker.rotation.x = -Math.PI / 2
    startMarker.position.set(start.x, 0.25, start.z)
    const endMarker = new THREE.Mesh(
      new THREE.RingGeometry(0.8, 1.25, 48),
      new THREE.MeshBasicMaterial({
        color: '#ffe0a1',
        transparent: true,
        opacity: 0.9,
        side: THREE.DoubleSide,
      }),
    )
    endMarker.rotation.x = -Math.PI / 2
    endMarker.position.set(end.x, 0.25, end.z)
    routeGroup.add(startMarker, endMarker)
  }

  scene.add(routeGroup)
  selectPointVisual(route.endPointId)
  focusRoute(points)
}

function focusRoute(points: THREE.Vector3[]) {
  if (!camera || !controls || points.length === 0) return
  const center = points.reduce(
    (sum, point) => sum.add(point),
    new THREE.Vector3(),
  ).multiplyScalar(1 / points.length)
  const extent = points.reduce(
    (maximum, point) => Math.max(maximum, Math.hypot(point.x - center.x, point.z - center.z)),
    4,
  )
  const distance = Math.max(28, extent * 2.25 + 18)
  flyTo(
    new THREE.Vector3(
      center.x + distance * 0.18,
      distance * 0.88,
      center.z + distance * 1.02,
    ),
    new THREE.Vector3(center.x, 0.4, center.z),
  )
}

function clearRoute() {
  if (routeGroup && scene) {
    scene.remove(routeGroup)
    disposeObject(routeGroup)
    routeGroup = null
    routeMaterials = []
  }
}

function updateCameraFlight(now: number) {
  if (!cameraFlight || !camera || !controls) return
  const t = Math.min(1, (now - cameraFlight.startedAt) / cameraFlight.durationMs)
  const eased = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2
  camera.position.lerpVectors(cameraFlight.start, cameraFlight.end, eased)
  controls.target.lerpVectors(cameraFlight.startTarget, cameraFlight.endTarget, eased)
  if (t >= 1) {
    cameraFlight = null
    controls.enabled = true
  }
}

function updateAnimatedScene(time: number, now: number) {
  if (highlight) {
    const active = now < highlight.until
    const ringMaterial = highlight.ring.material as THREE.MeshBasicMaterial
    ringMaterial.opacity = active ? 0.55 + Math.sin(time * 5.2) * 0.22 : 0
    highlight.ring.rotation.z = time * 0.17
    for (let i = 0; i < highlight.cornerMarks.length; i++) {
      const mark = highlight.cornerMarks[i]
      const markMaterial = mark.material as THREE.MeshBasicMaterial
      markMaterial.opacity = active ? 0.5 + Math.sin(time * 5.2 + i) * 0.3 : 0
      mark.position.y = 0.2 + Math.sin(time * 2.2 + i) * 0.05
    }
    if (!active) clearHighlight()
  }

  if (routeGroup) {
    for (const material of routeMaterials) {
      material.opacity = 0.78 + Math.sin(time * 3.1) * 0.14
    }
  }

}

function updateLabelVisibility(now: number) {
  if (!camera || !renderer || now - lastLabelUpdate < 220) return
  lastLabelUpdate = now
  const width = renderer.domElement.clientWidth
  const height = renderer.domElement.clientHeight
  const occupied: Array<{ left: number; right: number; top: number; bottom: number }> = []
  const cameraPosition = camera.position
  const sorted = [...pointLabels].sort((a, b) => {
    if (a.pointId === selectedPointId) return -1
    if (b.pointId === selectedPointId) return 1
    const groupA = pointGroups.get(a.pointId)
    const groupB = pointGroups.get(b.pointId)
    const distanceA = groupA ? groupA.position.distanceTo(cameraPosition) : Infinity
    const distanceB = groupB ? groupB.position.distanceTo(cameraPosition) : Infinity
    return distanceA - distanceB
  })

  let visibleCount = 0
  for (const entry of sorted) {
    const world = new THREE.Vector3()
    entry.sprite.getWorldPosition(world)
    const projected = world.clone().project(camera)
    const distance = cameraPosition.distanceTo(world)
    if (projected.z < -1 || projected.z > 1 || distance > 102) {
      entry.sprite.visible = false
      continue
    }
    const x = (projected.x * 0.5 + 0.5) * width
    const y = (-projected.y * 0.5 + 0.5) * height
    const desiredWidth = entry.pointId === selectedPointId ? 188 : 150
    const worldWidth =
      (desiredWidth * 2 * Math.tan(THREE.MathUtils.degToRad(camera.fov / 2)) * distance) /
      height
    entry.sprite.scale.set(worldWidth, worldWidth * (1.31 / 6.3), 1)
    const projectedWidth = desiredWidth
    const projectedHeight = projectedWidth * (1.31 / 6.3)
    const left = x - projectedWidth / 2
    const right = x + projectedWidth / 2
    const top = y - projectedHeight
    const bottom = y + projectedHeight * 0.15
    if (left < 0 || right > width || top < 0 || bottom > height) {
      entry.sprite.visible = false
      continue
    }
    const behindLeftRail = entry.pointId !== selectedPointId && x < Math.min(310, width * 0.25)
    const behindRightRail = entry.pointId !== selectedPointId && x > width - Math.min(300, width * 0.23)
    if (behindLeftRail || behindRightRail) {
      entry.sprite.visible = false
      continue
    }
    const intersects = occupied.some(
      (box) =>
        left < box.right + 8 &&
        right > box.left - 8 &&
        top < box.bottom + 5 &&
        bottom > box.top - 5,
    )
    if (intersects && entry.pointId !== selectedPointId) {
      entry.sprite.visible = false
      continue
    }
    if (visibleCount >= 5 && entry.pointId !== selectedPointId) {
      entry.sprite.visible = false
      continue
    }
    entry.sprite.visible = entry.pointId === selectedPointId || distance < 88
    if (!entry.sprite.visible) continue
    occupied.push({ left, right, top, bottom })
    visibleCount++
  }
}

function animate() {
  frameId = requestAnimationFrame(animate)
  if (!renderer || !scene || !camera || !controls) return
  const now = performance.now()
  const time = (now - animationStartedAt) / 1000
  updateCameraFlight(now)
  controls.target.x = THREE.MathUtils.clamp(controls.target.x, -30, 30)
  controls.target.z = THREE.MathUtils.clamp(controls.target.z, -30, 30)
  controls.target.y = Math.max(0.4, controls.target.y)
  controls.update()
  updateAnimatedScene(time, now)
  updateLabelVisibility(now)
  renderer.render(scene, camera)
}

function resize() {
  if (!host.value || !renderer || !camera) return
  camera.aspect = host.value.clientWidth / host.value.clientHeight
  camera.updateProjectionMatrix()
  renderer.setSize(host.value.clientWidth, host.value.clientHeight)
}

function disposeMaterial(material: THREE.Material) {
  for (const value of Object.values(material)) {
    if (value instanceof THREE.Texture) value.dispose()
  }
  material.dispose()
}

function disposeObject(root: THREE.Object3D) {
  const geometries = new Set<THREE.BufferGeometry>()
  const materials = new Set<THREE.Material>()
  root.traverse((object) => {
    const drawable = object as THREE.Mesh
    if (drawable.geometry instanceof THREE.BufferGeometry) geometries.add(drawable.geometry)
    if (drawable.material instanceof THREE.Material) materials.add(drawable.material)
    else if (Array.isArray(drawable.material)) {
      drawable.material.forEach((material) => materials.add(material))
    }
  })
  geometries.forEach((geometry) => geometry.dispose())
  materials.forEach((material) => disposeMaterial(material))
}

onMounted(init)
onBeforeUnmount(() => {
  cancelAnimationFrame(frameId)
  resizeObserver?.disconnect()
  renderer?.domElement.removeEventListener('pointerdown', onPointerDown)
  renderer?.domElement.removeEventListener('pointerup', onPointerUp)
  controls?.dispose()
  if (scene) disposeObject(scene)
  textureCache.forEach((texture) => texture.dispose())
  textureCache.clear()
  pointLabels.length = 0
  proceduralBuildingParts.length = 0
  pointGroups.clear()
  renderer?.dispose()
  renderer?.domElement.remove()
})

defineExpose({
  focusPoint,
  setCamera,
  resetCamera,
  showRoute,
  clearRoute,
})
</script>

<template>
  <div
    ref="host"
    class="campus-scene"
    role="img"
    aria-label="可交互的湖北工程学院新技术学院演示三维校园导览场景"
  >
    <div class="scene-vignette" aria-hidden="true" />
    <div class="scene-grain" aria-hidden="true" />
    <div class="scene-crosshair" aria-hidden="true">
      <span />
      <span />
    </div>
  </div>
</template>

<style scoped>
.campus-scene {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background:
    radial-gradient(circle at 52% 42%, #153a3b 0%, #081d20 62%, #061517 100%);
}

.campus-scene :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
}

.campus-scene :deep(canvas:active) {
  cursor: grabbing;
}

.scene-vignette,
.scene-grain {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.scene-vignette {
  background:
    linear-gradient(180deg, rgb(3 16 18 / 34%) 0%, transparent 22%, transparent 72%, rgb(3 16 18 / 53%) 100%),
    radial-gradient(circle at 56% 42%, transparent 0 38%, rgb(2 16 18 / 32%) 100%);
}

.scene-grain {
  opacity: 0.12;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='.3'/%3E%3C/svg%3E");
  mix-blend-mode: soft-light;
}

.scene-crosshair {
  position: absolute;
  left: 50%;
  top: 45%;
  width: 28px;
  height: 28px;
  transform: translate(-50%, -50%);
  opacity: 0.38;
  pointer-events: none;
}

.scene-crosshair::before,
.scene-crosshair::after {
  position: absolute;
  width: 4px;
  height: 4px;
  border-color: #f4e7c7;
  border-style: solid;
  content: '';
}

.scene-crosshair::before {
  top: 0;
  left: 0;
  border-width: 1px 0 0 1px;
}

.scene-crosshair::after {
  right: 0;
  bottom: 0;
  border-width: 0 1px 1px 0;
}

.scene-crosshair span {
  position: absolute;
  background: #f4e7c7;
}

.scene-crosshair span:first-child {
  width: 20px;
  height: 1px;
  top: 13px;
  left: 4px;
}

.scene-crosshair span:last-child {
  width: 1px;
  height: 20px;
  left: 13px;
  top: 4px;
}
</style>
