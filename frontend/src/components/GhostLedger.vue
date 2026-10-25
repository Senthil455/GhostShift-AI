<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import * as THREE from 'three'
import { ledgerModules } from '@/lib/modules'

const props = withDefaults(
  defineProps<{
    mode?: 'hero' | 'compact'
    interactive?: boolean
  }>(),
  { mode: 'hero', interactive: true },
)

const router = useRouter()
const host = ref<HTMLDivElement | null>(null)
const canvas = ref<HTMLCanvasElement | null>(null)
const fallback = ref(false)
const hovered = ref<number | null>(null)
const dragging = ref(false)
const modules = ledgerModules

const hoveredModule = computed(() => (hovered.value === null ? null : modules[hovered.value]))

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let pivot: THREE.Group | null = null
let spin: THREE.Group | null = null
let raf = 0
let running = false
let visible = true
let reduced = false
let autoAngle = 0
let dragY = 0
let dragX = 0
let pointerX = 0
let pointerY = 0
let draggingPointer = false
let startX = 0
let startY = 0
let moved = 0
let lastFrame = 0
let resizeObserver: ResizeObserver | null = null
let intersectionObserver: IntersectionObserver | null = null
let motionQuery: MediaQueryList | null = null
const pointerNdc = new THREE.Vector2()
const raycaster = new THREE.Raycaster()
const plateMeshes: THREE.Mesh[] = []
const plateMaterials: THREE.MeshStandardMaterial[] = []
const edgeMaterials: THREE.LineBasicMaterial[] = []
const disposables: Array<{ dispose: () => void }> = []
const listeners: Array<() => void> = []

function plateGeometry(): THREE.ExtrudeGeometry {
  const width = 3.05
  const height = 1.74
  const cut = 0.3
  const shape = new THREE.Shape()
  shape.moveTo(-width / 2, -height / 2)
  shape.lineTo(width / 2 - cut, -height / 2)
  shape.lineTo(width / 2, -height / 2 + cut)
  shape.lineTo(width / 2, height / 2)
  shape.lineTo(-width / 2, height / 2)
  shape.closePath()
  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: 0.055,
    bevelEnabled: true,
    bevelThickness: 0.016,
    bevelSize: 0.016,
    bevelSegments: 2,
    steps: 1,
  })
  geometry.center()
  return geometry
}

function addDisposable(item: { dispose: () => void }) {
  disposables.push(item)
}

function buildScene() {
  if (!canvas.value) return
  try {
    renderer = new THREE.WebGLRenderer({
      canvas: canvas.value,
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance',
    })
  } catch {
    fallback.value = true
    return
  }

  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.6))
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.35

  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x0b0e13, 0.026)
  camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100)
  if (props.mode === 'compact') {
    camera.position.set(4.95, 3.1, 6.2)
    camera.lookAt(0, 0.05, 0)
  } else {
    camera.position.set(5.35, 3.35, 6.7)
    camera.lookAt(0, 0.1, 0)
  }

  pivot = new THREE.Group()
  spin = new THREE.Group()
  pivot.add(spin)
  scene.add(pivot)

  const ambient = new THREE.AmbientLight(0xffffff, 0.62)
  scene.add(ambient)
  const key = new THREE.DirectionalLight(0xffffff, 2.45)
  key.position.set(4.5, 7.5, 5.5)
  scene.add(key)
  const fill = new THREE.PointLight(0x8b9cff, 32, 15, 2)
  fill.position.set(-4.2, 2.2, 3.4)
  scene.add(fill)
  const rim = new THREE.PointLight(0xff6b4a, 22, 13, 2)
  rim.position.set(4.2, -0.8, -3.2)
  scene.add(rim)

  const plinthGeometry = new THREE.CylinderGeometry(2.55, 2.72, 0.16, 6)
  const plinthMaterial = new THREE.MeshStandardMaterial({
    color: 0x151a22,
    metalness: 0.58,
    roughness: 0.4,
  })
  const plinth = new THREE.Mesh(plinthGeometry, plinthMaterial)
  plinth.position.y = -1.76
  pivot.add(plinth)
  addDisposable(plinthGeometry)
  addDisposable(plinthMaterial)

  const plinthEdgeGeometry = new THREE.EdgesGeometry(plinthGeometry, 18)
  const plinthEdgeMaterial = new THREE.LineBasicMaterial({
    color: 0x8b9cff,
    transparent: true,
    opacity: 0.16,
  })
  const plinthEdges = new THREE.LineSegments(plinthEdgeGeometry, plinthEdgeMaterial)
  plinthEdges.position.copy(plinth.position)
  pivot.add(plinthEdges)
  addDisposable(plinthEdgeGeometry)
  addDisposable(plinthEdgeMaterial)

  const ringGeometry = new THREE.RingGeometry(2.04, 2.065, 72)
  const ringMaterial = new THREE.MeshBasicMaterial({
    color: 0x8b9cff,
    transparent: true,
    opacity: 0.28,
    side: THREE.DoubleSide,
  })
  const ring = new THREE.Mesh(ringGeometry, ringMaterial)
  ring.rotation.x = -Math.PI / 2
  ring.position.y = -1.665
  pivot.add(ring)
  addDisposable(ringGeometry)
  addDisposable(ringMaterial)

  const ringTwoGeometry = new THREE.RingGeometry(1.7, 1.708, 72)
  const ringTwoMaterial = new THREE.MeshBasicMaterial({
    color: 0xff6b4a,
    transparent: true,
    opacity: 0.12,
    side: THREE.DoubleSide,
  })
  const ringTwo = new THREE.Mesh(ringTwoGeometry, ringTwoMaterial)
  ringTwo.rotation.x = -Math.PI / 2
  ringTwo.position.y = -1.652
  pivot.add(ringTwo)
  addDisposable(ringTwoGeometry)
  addDisposable(ringTwoMaterial)

  const spindleGeometry = new THREE.CylinderGeometry(0.009, 0.009, 4.55, 8)
  const spindleMaterial = new THREE.MeshBasicMaterial({
    color: 0x8b9cff,
    transparent: true,
    opacity: 0.32,
  })
  const spindle = new THREE.Mesh(spindleGeometry, spindleMaterial)
  spindle.position.y = 0.25
  pivot.add(spindle)
  addDisposable(spindleGeometry)
  addDisposable(spindleMaterial)

  const capGeometry = new THREE.BoxGeometry(0.13, 0.13, 0.13)
  const capMaterial = new THREE.MeshBasicMaterial({ color: 0xff6b4a })
  const cap = new THREE.Mesh(capGeometry, capMaterial)
  cap.position.y = 2.55
  pivot.add(cap)
  addDisposable(capGeometry)
  addDisposable(capMaterial)

  const geometry = plateGeometry()
  const edgeGeometry = new THREE.EdgesGeometry(geometry, 24)
  addDisposable(geometry)
  addDisposable(edgeGeometry)

  const palette = [0x8b9cff, 0x8b9cff, 0x8b9cff, 0x8b9cff, 0xff6b4a, 0xff6b4a]
  const tickGeometry = new THREE.BoxGeometry(0.026, 0.018, 0.13)
  const tickMaterial = new THREE.MeshBasicMaterial({
    color: 0xf2eee5,
    transparent: true,
    opacity: 0.58,
  })
  const tabGeometry = new THREE.BoxGeometry(0.44, 0.032, 0.2)
  const nodeGeometry = new THREE.SphereGeometry(0.032, 8, 8)
  const nodeMaterial = new THREE.MeshBasicMaterial({ color: 0xff6b4a })
  addDisposable(tickGeometry)
  addDisposable(tickMaterial)
  addDisposable(tabGeometry)
  addDisposable(nodeGeometry)
  addDisposable(nodeMaterial)

  modules.forEach((module, index) => {
    const card = new THREE.Group()
    const angle = index * 1.28 + 0.35
    const radius = 0.16 + index * 0.022
    card.position.set(Math.cos(angle) * radius, -0.94 + index * 0.325, Math.sin(angle) * radius)
    card.rotation.y = index * 0.48 - 0.35
    card.scale.setScalar(0.92 + index * 0.014)

    const plateMaterial = new THREE.MeshStandardMaterial({
      color: palette[index] ?? 0x8b9cff,
      transparent: true,
      opacity: 0.24,
      roughness: 0.16,
      metalness: 0.58,
      side: THREE.DoubleSide,
      depthWrite: false,
    })
    const plate = new THREE.Mesh(geometry, plateMaterial)
    plate.rotation.x = -Math.PI / 2
    plate.userData.index = index
    card.add(plate)
    plateMeshes.push(plate)
    plateMaterials.push(plateMaterial)
    addDisposable(plateMaterial)

    const ghostMaterial = new THREE.MeshBasicMaterial({
      color: 0xf2eee5,
      transparent: true,
      opacity: 0.045,
      side: THREE.DoubleSide,
      depthWrite: false,
    })
    const ghost = new THREE.Mesh(geometry, ghostMaterial)
    ghost.rotation.x = -Math.PI / 2
    ghost.position.set(0.09, -0.055, 0.09)
    card.add(ghost)
    addDisposable(ghostMaterial)

    const edgeMaterial = new THREE.LineBasicMaterial({
      color: 0xf2eee5,
      transparent: true,
      opacity: 0.42,
    })
    const edges = new THREE.LineSegments(edgeGeometry, edgeMaterial)
    edges.rotation.x = -Math.PI / 2
    card.add(edges)
    edgeMaterials.push(edgeMaterial)
    addDisposable(edgeMaterial)

    const tabMaterial = new THREE.MeshBasicMaterial({
      color: palette[index] ?? 0x8b9cff,
      transparent: true,
      opacity: 0.7,
    })
    const tab = new THREE.Mesh(tabGeometry, tabMaterial)
    tab.position.set(0.78, 0.035, -0.8)
    card.add(tab)
    addDisposable(tabMaterial)

    for (let tick = 0; tick < 7; tick += 1) {
      const mark = new THREE.Mesh(tickGeometry, tickMaterial)
      mark.position.set(-0.94 + tick * 0.19, 0.04, -0.87)
      card.add(mark)
    }

    const node = new THREE.Mesh(nodeGeometry, nodeMaterial)
    node.position.set(0.78, 0.065, -0.8)
    card.add(node)

    const orbitGeometry = new THREE.TorusGeometry(1.28 + index * 0.025, 0.004, 5, 72)
    const orbitMaterial = new THREE.MeshBasicMaterial({
      color: index > 3 ? 0xff6b4a : 0x8b9cff,
      transparent: true,
      opacity: 0.13,
    })
    const orbit = new THREE.Mesh(orbitGeometry, orbitMaterial)
    orbit.rotation.x = -Math.PI / 2
    orbit.position.y = -0.06 + index * 0.02
    card.add(orbit)
    addDisposable(orbitGeometry)
    addDisposable(orbitMaterial)

    spin?.add(card)
  })

  const particleCount = 110
  const positions = new Float32Array(particleCount * 3)
  const colors = new Float32Array(particleCount * 3)
  const bone = new THREE.Color(0xf2eee5)
  const cobalt = new THREE.Color(0x8b9cff)
  for (let index = 0; index < particleCount; index += 1) {
    const angle = Math.random() * Math.PI * 2
    const radius = 1.1 + Math.random() * 2.4
    positions[index * 3] = Math.cos(angle) * radius
    positions[index * 3 + 1] = -1.45 + Math.random() * 3.6
    positions[index * 3 + 2] = Math.sin(angle) * radius
    const color = Math.random() > 0.18 ? cobalt : bone
    colors[index * 3] = color.r
    colors[index * 3 + 1] = color.g
    colors[index * 3 + 2] = color.b
  }
  const particleGeometry = new THREE.BufferGeometry()
  particleGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  particleGeometry.setAttribute('color', new THREE.BufferAttribute(colors, 3))
  const particleMaterial = new THREE.PointsMaterial({
    size: 0.027,
    vertexColors: true,
    transparent: true,
    opacity: 0.58,
    sizeAttenuation: true,
    depthWrite: false,
  })
  const particles = new THREE.Points(particleGeometry, particleMaterial)
  spin.add(particles)
  addDisposable(particleGeometry)
  addDisposable(particleMaterial)

  bindEvents()
  resize()
  applyHover()
  start()
}

function resize() {
  if (!host.value || !renderer || !camera) return
  const width = host.value.clientWidth
  const height = host.value.clientHeight
  if (!width || !height) return
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
  if (reduced) renderOnce()
}

function renderOnce() {
  if (renderer && scene && camera) renderer.render(scene, camera)
}

function applyDragTransform() {
  if (spin) spin.rotation.y = dragY
  if (pivot) {
    pivot.rotation.x = dragX
    pivot.rotation.z = 0
  }
}

function start() {
  if (reduced) {
    renderOnce()
    return
  }
  if (running || !visible) return
  running = true
  lastFrame = performance.now()
  raf = requestAnimationFrame(animate)
}

function stop() {
  running = false
  cancelAnimationFrame(raf)
}

function animate(now: number) {
  if (!running) return
  raf = requestAnimationFrame(animate)
  const delta = Math.min((now - lastFrame) / 1000 || 0, 0.05)
  lastFrame = now
  if (!reduced) autoAngle += delta * 0.095
  if (spin) spin.rotation.y = autoAngle + dragY
  if (pivot) {
    const targetX = dragX + pointerY * 0.055
    const targetZ = pointerX * 0.032
    pivot.rotation.x += (targetX - pivot.rotation.x) * 0.055
    pivot.rotation.z += (targetZ - pivot.rotation.z) * 0.055
  }
  renderOnce()
}

function updatePointer(event: PointerEvent) {
  if (!canvas.value) return
  const rect = canvas.value.getBoundingClientRect()
  pointerX = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointerY = ((event.clientY - rect.top) / rect.height) * 2 - 1
  pointerNdc.set(pointerX, -pointerY)
}

function raycast() {
  if (!camera || !plateMeshes.length) return
  raycaster.setFromCamera(pointerNdc, camera)
  const hits = raycaster.intersectObjects(plateMeshes, false)
  const next = hits.length ? Number(hits[0].object.userData.index) : null
  if (next !== hovered.value) {
    hovered.value = next
    applyHover()
    if (reduced) renderOnce()
  }
}

function applyHover() {
  plateMaterials.forEach((material, index) => {
    const active = hovered.value === index
    material.emissive.setHex(active ? 0xff6b4a : 0x000000)
    material.emissiveIntensity = active ? 0.42 : 0
    material.opacity = active ? 0.4 : 0.24
  })
  edgeMaterials.forEach((material, index) => {
    const active = hovered.value === index
    material.color.setHex(active ? 0xff6b4a : 0xf2eee5)
    material.opacity = active ? 0.98 : 0.42
  })
  if (canvas.value) {
    canvas.value.style.cursor = hovered.value !== null ? 'pointer' : draggingPointer ? 'grabbing' : 'grab'
  }
}

function setHover(index: number | null) {
  hovered.value = index
  applyHover()
  if (reduced) renderOnce()
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  draggingPointer = true
  dragging.value = true
  moved = 0
  startX = event.clientX
  startY = event.clientY
  updatePointer(event)
  canvas.value?.setPointerCapture(event.pointerId)
  applyHover()
}

function onPointerMove(event: PointerEvent) {
  updatePointer(event)
  if (draggingPointer) {
    const dx = event.clientX - startX
    const dy = event.clientY - startY
    moved += Math.abs(dx) + Math.abs(dy)
    startX = event.clientX
    startY = event.clientY
    dragY += dx * 0.006
    dragX = Math.max(-0.2, Math.min(0.2, dragX + dy * 0.003))
  }
  raycast()
  if (reduced) renderOnce()
}

function onPointerUp(event: PointerEvent) {
  if (!draggingPointer) return
  draggingPointer = false
  dragging.value = false
  canvas.value?.releasePointerCapture(event.pointerId)
  if (moved < 6 && hovered.value !== null && props.interactive) {
    void router.push(modules[hovered.value].to)
  }
  applyHover()
}

function onPointerCancel() {
  draggingPointer = false
  dragging.value = false
  applyHover()
}

function onPointerLeave() {
  pointerX = 0
  pointerY = 0
  if (!draggingPointer) {
    hovered.value = null
    applyHover()
    if (reduced) renderOnce()
  }
}

function onVisibilityChange() {
  visible = document.visibilityState === 'visible'
  if (visible) start()
  else stop()
}

function bindEvents() {
  const el = canvas.value
  if (!el) return
  el.addEventListener('pointerdown', onPointerDown)
  el.addEventListener('pointermove', onPointerMove)
  el.addEventListener('pointerup', onPointerUp)
  el.addEventListener('pointercancel', onPointerCancel)
  el.addEventListener('pointerleave', onPointerLeave)
  document.addEventListener('visibilitychange', onVisibilityChange)
  listeners.push(() => {
    el.removeEventListener('pointerdown', onPointerDown)
    el.removeEventListener('pointermove', onPointerMove)
    el.removeEventListener('pointerup', onPointerUp)
    el.removeEventListener('pointercancel', onPointerCancel)
    el.removeEventListener('pointerleave', onPointerLeave)
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  if ('ResizeObserver' in window && host.value) {
    resizeObserver = new ResizeObserver(() => resize())
    resizeObserver.observe(host.value)
  } else {
    const onResize = () => resize()
    window.addEventListener('resize', onResize)
    listeners.push(() => window.removeEventListener('resize', onResize))
  }

  if ('IntersectionObserver' in window && host.value) {
    intersectionObserver = new IntersectionObserver(
      (entries) => {
        const entry = entries[0]
        if (!entry) return
        visible = entry.isIntersecting && document.visibilityState === 'visible'
        if (visible) start()
        else stop()
      },
      { threshold: 0.05 },
    )
    intersectionObserver.observe(host.value)
  }
}

function disposeScene() {
  stop()
  resizeObserver?.disconnect()
  intersectionObserver?.disconnect()
  listeners.forEach((remove) => remove())
  listeners.length = 0
  disposables.forEach((item) => item.dispose())
  disposables.length = 0
  plateMeshes.length = 0
  plateMaterials.length = 0
  edgeMaterials.length = 0
  renderer?.dispose()
  renderer?.forceContextLoss()
  renderer = null
  scene = null
  camera = null
  pivot = null
  spin = null
}

onMounted(() => {
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  buildScene()
})

onBeforeUnmount(() => {
  disposeScene()
})
</script>

<template>
  <div
    class="ledger"
    :class="[`ledger--${mode}`, { 'ledger--fallback': fallback, 'ledger--dragging': dragging }]"
  >
    <div ref="host" class="ledger__stage">
      <canvas ref="canvas" class="ledger__canvas" aria-hidden="true" />

      <div v-if="fallback" class="ledger__fallback" aria-hidden="true">
        <span class="ledger__fallback-plate ledger__fallback-plate--back" />
        <span class="ledger__fallback-plate ledger__fallback-plate--mid" />
        <span class="ledger__fallback-plate ledger__fallback-plate--front">
          <span />
          <i />
        </span>
      </div>

      <div class="ledger__top">
        <span class="ledger__eyebrow">The archive</span>
        <span class="ledger__count">
          {{ modules.length.toString().padStart(2, '0') }} plates
          <i />
        </span>
      </div>
    </div>

    <div class="ledger__index" aria-label="Archive modules">
      <template v-for="(item, index) in modules" :key="item.key">
        <RouterLink
          v-if="interactive"
          :to="item.to"
          class="ledger__index-item"
          :class="{ 'is-hot': hovered === index }"
          @mouseenter="setHover(index)"
          @mouseleave="setHover(null)"
          @focus="setHover(index)"
          @blur="setHover(null)"
        >
          <span class="ledger__index-no">{{ item.order }}</span>
          <span>{{ item.label }}</span>
          <span class="ledger__index-mark" />
        </RouterLink>
        <div
          v-else
          class="ledger__index-item"
          :class="{ 'is-hot': hovered === index }"
          @mouseenter="setHover(index)"
          @mouseleave="setHover(null)"
        >
          <span class="ledger__index-no">{{ item.order }}</span>
          <span>{{ item.label }}</span>
          <span class="ledger__index-mark" />
        </div>
      </template>
    </div>

    <div class="ledger__bottom">
      <div class="ledger__readout">
        <span class="ledger__readout-no">{{ hoveredModule?.order ?? '00' }}</span>
        <div>
          <strong>{{ hoveredModule?.label ?? 'GhostShift archive' }}</strong>
          <p>{{ hoveredModule?.short ?? 'Six modules suspended in one local workspace.' }}</p>
        </div>
      </div>
      <span class="ledger__hint">{{ interactive ? 'Drag to shift · click a plate' : 'A live model of the workspace' }}</span>
    </div>
  </div>
</template>

<style scoped>
.ledger {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 35rem;
  overflow: hidden;
  isolation: isolate;
  background: #0b0e13;
  color: #f2eee5;
  clip-path: polygon(0 0, calc(100% - 30px) 0, 100% 30px, 100% 100%, 0 100%);
  box-shadow: inset 0 0 90px rgb(0 0 0 / 0.36);
}
.ledger--compact {
  min-height: 28rem;
}
.ledger__stage {
  position: relative;
  flex: 1;
  min-height: 0;
}
.ledger__canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  touch-action: pan-y;
  cursor: grab;
}
.ledger--dragging .ledger__canvas {
  cursor: grabbing;
}
.ledger__top,
.ledger__bottom {
  position: absolute;
  left: 1.3rem;
  right: 1.3rem;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  pointer-events: none;
}
.ledger__top {
  top: 1.15rem;
}
.ledger__bottom {
  position: static;
  flex: none;
  align-items: flex-end;
  padding: 0.85rem 1.3rem;
  border-top: 1px solid #2a3040;
  background: #0b0e13;
}
.ledger__eyebrow {
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: #f2eee5;
}
.ledger__count {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.72rem;
  color: #9b9a94;
}
.ledger__count i {
  width: 0.42rem;
  height: 0.42rem;
  background: #ff6b4a;
}
.ledger__readout {
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  max-width: 24rem;
}
.ledger__readout-no {
  padding-top: 0.15rem;
  font-size: 0.68rem;
  font-weight: 600;
  color: #8b9cff;
}
.ledger__readout strong {
  display: block;
  font-family: var(--font-display);
  font-size: 1.35rem;
  font-weight: 600;
  letter-spacing: -0.035em;
  line-height: 1;
}
.ledger__readout p {
  margin: 0.35rem 0 0;
  color: #a9a59c;
  font-size: 0.78rem;
  line-height: 1.45;
}
.ledger__hint {
  color: #8b8a84;
  font-size: 0.7rem;
  white-space: nowrap;
}
.ledger__index {
  position: absolute;
  top: 3.55rem;
  right: 1.3rem;
  display: flex;
  flex-direction: column;
  width: 9.8rem;
}
.ledger__index-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.42rem 0.45rem 0.42rem 0.6rem;
  border-left: 1px solid rgb(242 238 229 / 0.14);
  color: #a9a59c;
  font-size: 0.76rem;
  line-height: 1.2;
  transition:
    color 150ms ease,
    background 150ms ease,
    border-color 150ms ease;
}
.ledger__index-item:hover,
.ledger__index-item.is-hot {
  border-left-color: #ff6b4a;
  background: rgb(242 238 229 / 0.055);
  color: #f2eee5;
}
.ledger__index-no {
  width: 1.15rem;
  color: #777a84;
  font-size: 0.62rem;
  font-variant-numeric: tabular-nums;
}
.ledger__index-mark {
  width: 0.28rem;
  height: 0.28rem;
  margin-left: auto;
  background: #ff6b4a;
  opacity: 0;
}
.ledger__index-item.is-hot .ledger__index-mark {
  opacity: 1;
}
.ledger__fallback {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: #0b0e13;
}
.ledger__fallback-plate {
  position: absolute;
  width: 18rem;
  height: 10rem;
  border: 1px solid rgb(139 156 255 / 0.6);
  background: rgb(139 156 255 / 0.08);
  clip-path: polygon(0 0, calc(100% - 12%) 0, 100% 12%, 100% 100%, 0 100%);
  transform: rotateX(56deg) rotateZ(-32deg);
}
.ledger__fallback-plate--back {
  transform: translate3d(-2rem, 2rem, 0) rotateX(56deg) rotateZ(-32deg);
  opacity: 0.35;
}
.ledger__fallback-plate--mid {
  transform: translate3d(-1rem, 1rem, 0) rotateX(56deg) rotateZ(-32deg);
  opacity: 0.6;
}
.ledger__fallback-plate--front {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 1rem;
  background: rgb(139 156 255 / 0.16);
}
.ledger__fallback-plate--front span {
  width: 0.4rem;
  height: 0.4rem;
  background: #ff6b4a;
}
.ledger__fallback-plate--front i {
  width: 4rem;
  height: 0.2rem;
  background: #f2eee5;
  opacity: 0.55;
}
@media (max-width: 760px) {
  .ledger,
  .ledger--compact {
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .ledger__stage {
    position: relative;
    inset: auto;
    flex: none;
    height: 24rem;
  }
  .ledger__index {
    position: static;
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 0.4rem;
    width: auto;
    padding: 0.65rem 1rem;
    border-top: 1px solid #2a3040;
    background: #0b0e13;
  }
  .ledger__index-item {
    font-size: 0.72rem;
    padding: 0.34rem 0.35rem;
  }
  .ledger__bottom {
    position: static;
    padding: 0.8rem 1rem;
  }
  .ledger__hint {
    display: none;
  }
  .ledger__readout p {
    max-width: none;
  }
  .ledger__top {
    left: 1rem;
    right: 1rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .ledger__index-item {
    transition: none;
  }
}
</style>
