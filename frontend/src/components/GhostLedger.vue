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
const active = ref(0)
const hovered = ref<number | null>(null)
const dragging = ref(false)
const modules = ledgerModules

const readout = computed(() => modules[hovered.value ?? active.value] ?? modules[0])

const STEP = (Math.PI * 2) / modules.length
const CARD_W = 1.32
const CARD_H = 1.96

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let wheel: THREE.Group | null = null
let counterTexture: THREE.CanvasTexture | null = null
let counterCanvas: HTMLCanvasElement | null = null

let raf = 0
let running = false
let visible = true
let reduced = false
let lastFrame = 0
let angle = 0
let targetAngle = 0
let angularVelocity = 0
let draggingPointer = false
let startX = 0
let moved = 0
let pointerNdcX = 0
let pointerNdcY = 0
const pointerNdc = new THREE.Vector2()
let lastHover: number | null = null

const raycaster = new THREE.Raycaster()
const cardHits: THREE.Mesh[] = []
const cardMeshes: THREE.Mesh[] = []
const cardGlow: THREE.MeshStandardMaterial[] = []
const disposables: Array<{ dispose: () => void }> = []
const listeners: Array<() => void> = []
let resizeObserver: ResizeObserver | null = null
let intersectionObserver: IntersectionObserver | null = null
let resetPointer: (() => void) | null = null

function track<T extends { dispose: () => void }>(item: T): T {
  disposables.push(item)
  return item
}

function normalise(value: number): number {
  let next = value % (Math.PI * 2)
  if (next > Math.PI) next -= Math.PI * 2
  if (next < -Math.PI) next += Math.PI * 2
  return next
}

/* A printed index card: the module's own name, its sentence, a serial, and
   the punched holes along the bottom edge. */
function cardTexture(index: number): THREE.CanvasTexture {
  const item = modules[index]
  const canvasEl = document.createElement('canvas')
  canvasEl.width = 620
  canvasEl.height = 840
  const ctx = canvasEl.getContext('2d')
  if (!ctx) return new THREE.CanvasTexture(canvasEl)

  ctx.fillStyle = '#e5dcc7'
  ctx.fillRect(0, 0, 620, 840)

  // paper tooth
  ctx.globalAlpha = 0.05
  for (let dot = 0; dot < 2600; dot += 1) {
    ctx.fillStyle = Math.random() > 0.5 ? '#3a3427' : '#ffffff'
    ctx.fillRect(Math.random() * 620, Math.random() * 840, 1.6, 1.6)
  }
  ctx.globalAlpha = 1

  // a soft fold of light down the left edge
  const wash = ctx.createLinearGradient(0, 0, 620, 0)
  wash.addColorStop(0, 'rgba(255,255,255,0.55)')
  wash.addColorStop(0.28, 'rgba(255,255,255,0)')
  wash.addColorStop(1, 'rgba(58,48,30,0.07)')
  ctx.fillStyle = wash
  ctx.fillRect(0, 0, 620, 840)

  // printed header band, reversed out of the paper
  ctx.fillStyle = '#1c1b14'
  ctx.fillRect(0, 0, 620, 148)
  ctx.fillStyle = '#e5dcc7'
  ctx.font = '500 22px "Instrument Sans Variable", system-ui, sans-serif'
  ctx.fillText('GHOSTSHIFT', 56, 88)

  ctx.textAlign = 'right'
  ctx.fillStyle = '#b8b09c'
  ctx.font = '500 20px "Instrument Sans Variable", system-ui, sans-serif'
  ctx.fillText(`Nº ${String(index + 1).padStart(2, '0')}`, 566, 88)
  ctx.textAlign = 'left'

  ctx.fillStyle = '#e8613a'
  ctx.fillRect(56, 108, 58, 5)

  ctx.fillStyle = '#191811'
  ctx.font = '500 82px "Newsreader Variable", Georgia, serif'
  const words = item.label.split(/(?=[A-Z])/)
  const lines: string[] = []
  let current = ''
  for (const word of words) {
    if ((current + word).length > 9 && current) {
      lines.push(current)
      current = word
    } else {
      current += word
    }
  }
  if (current) lines.push(current)
  lines.forEach((line, lineIndex) => {
    ctx.fillText(line, 52, 268 + lineIndex * 84)
  })

  ctx.fillStyle = '#4a4436'
  ctx.font = '400 25px "Instrument Sans Variable", system-ui, sans-serif'
  const bodyWords = (item.short ?? '').split(' ')
  let line = ''
  let y = 350 + Math.max(0, lines.length - 1) * 84
  for (const word of bodyWords) {
    const next = line ? `${line} ${word}` : word
    if (ctx.measureText(next).width > 500) {
      ctx.fillText(line, 56, y)
      line = word
      y += 34
    } else {
      line = next
    }
  }
  if (line) ctx.fillText(line, 56, y)

  // the card's ruled lines
  ctx.strokeStyle = 'rgba(74,68,54,0.22)'
  ctx.lineWidth = 1
  for (let rule = 0; rule < 4; rule += 1) {
    const ruleY = 640 + rule * 34
    ctx.beginPath()
    ctx.moveTo(56, ruleY)
    ctx.lineTo(566, ruleY)
    ctx.stroke()
  }

  // punched holes along the base of the card
  ctx.fillStyle = '#14130d'
  for (let hole = 0; hole < 6; hole += 1) {
    const holeX = 64 + hole * 88
    ctx.beginPath()
    ctx.roundRect(holeX, 762, 22, 22, 3)
    ctx.fill()
  }

  const texture = new THREE.CanvasTexture(canvasEl)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.anisotropy = 4
  return texture
}

function counterLabel(index: number) {
  if (!counterCanvas) return
  const ctx = counterCanvas.getContext('2d')
  if (!ctx) return
  ctx.fillStyle = '#151814'
  ctx.fillRect(0, 0, 256, 96)
  ctx.fillStyle = '#d9ad63'
  ctx.font = '500 54px "Instrument Sans Variable", system-ui, sans-serif'
  ctx.textAlign = 'center'
  ctx.fillText(String(index + 1).padStart(2, '0'), 92, 66)
  ctx.fillStyle = '#6d6a5c'
  ctx.font = '500 26px "Instrument Sans Variable", system-ui, sans-serif'
  ctx.fillText(`/ ${String(modules.length).padStart(2, '0')}`, 190, 66)
  if (counterTexture) counterTexture.needsUpdate = true
}

function buildScene() {
  if (!canvas.value) return
  try {
    renderer = new THREE.WebGLRenderer({ canvas: canvas.value, antialias: true, alpha: true })
  } catch {
    fallback.value = true
    return
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
  renderer.setClearColor(0x000000, 0)
  renderer.outputColorSpace = THREE.SRGBColorSpace
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1

  scene = new THREE.Scene()
  scene.fog = new THREE.FogExp2(0x0b0d0b, 0.045)

  camera = new THREE.PerspectiveCamera(30, 1, 0.1, 60)
  camera.position.set(2.35, 3.05, props.mode === 'compact' ? 6.9 : 7.2)
  camera.lookAt(0, 0.98, 0.15)

  wheel = new THREE.Group()
  scene.add(wheel)

  const hemi = new THREE.HemisphereLight(0xfff2d8, 0x161811, 0.7)
  scene.add(hemi)
  const key = new THREE.DirectionalLight(0xfff0d4, 1.9)
  key.position.set(-4.5, 7.5, 6)
  scene.add(key)
  const frontFill = new THREE.DirectionalLight(0xfff2dc, 0.9)
  frontFill.position.set(0.6, 2.6, 7.2)
  scene.add(frontFill)
  const fill = new THREE.DirectionalLight(0x9fb3a6, 0.4)
  fill.position.set(5, 2.5, 3)
  scene.add(fill)
  const lamp = new THREE.SpotLight(0xffeccc, 18, 18, 0.72, 0.55, 1.3)
  lamp.position.set(-1.2, 6.4, 4.2)
  lamp.target.position.set(0, 1.0, 1.2)
  scene.add(lamp)
  scene.add(lamp.target)
  const rim = new THREE.PointLight(0xe8613a, 1.6, 7, 2)
  rim.position.set(1.6, 0.4, -2.2)
  scene.add(rim)

  // the machined base
  const baseGeometry = track(new THREE.CylinderGeometry(2.68, 2.8, 0.16, 72))
  const baseMaterial = track(
    new THREE.MeshStandardMaterial({ color: 0x3a352b, metalness: 0.72, roughness: 0.34 }),
  )
  const base = new THREE.Mesh(baseGeometry, baseMaterial)
  base.position.y = 0.08
  wheel.add(base)

  const rimGeometry = track(new THREE.TorusGeometry(2.72, 0.022, 8, 90))
  const rimMaterial = track(new THREE.MeshStandardMaterial({ color: 0xd9ad63, metalness: 0.9, roughness: 0.32 }))
  const rimRing = new THREE.Mesh(rimGeometry, rimMaterial)
  rimRing.rotation.x = Math.PI / 2
  rimRing.position.y = 0.17
  wheel.add(rimRing)

  const tickGeometry = track(new THREE.BoxGeometry(0.025, 0.008, 0.14))
  const tickMaterial = track(new THREE.MeshBasicMaterial({ color: 0xece4d2, transparent: true, opacity: 0.38 }))
  for (let tick = 0; tick < 72; tick += 1) {
    const mark = new THREE.Mesh(tickGeometry, tickMaterial)
    const a = (tick / 72) * Math.PI * 2
    mark.position.set(Math.cos(a) * 2.5, 0.17, Math.sin(a) * 2.5)
    mark.rotation.y = -a
    wheel.add(mark)
  }

  const hubGeometry = track(new THREE.CylinderGeometry(0.42, 0.46, 0.52, 32))
  const hubMaterial = track(new THREE.MeshStandardMaterial({ color: 0xc79a52, metalness: 0.95, roughness: 0.28 }))
  const hub = new THREE.Mesh(hubGeometry, hubMaterial)
  hub.position.y = 0.42
  wheel.add(hub)

  const hubCapGeometry = track(new THREE.CylinderGeometry(0.16, 0.2, 0.1, 24))
  const hubCapMaterial = track(new THREE.MeshStandardMaterial({ color: 0xe8613a, metalness: 0.4, roughness: 0.5 }))
  const hubCap = new THREE.Mesh(hubCapGeometry, hubCapMaterial)
  hubCap.position.y = 0.73
  wheel.add(hubCap)

  const cardGeometry = track(new THREE.BoxGeometry(CARD_W, CARD_H, 0.035))
  const edgeMaterial = track(new THREE.MeshStandardMaterial({ color: 0xd9cfb6, roughness: 0.95, metalness: 0 }))
  const backMaterial = track(new THREE.MeshStandardMaterial({ color: 0xe4dbc5, roughness: 0.95, metalness: 0 }))
  const shoeGeometry = track(new THREE.BoxGeometry(0.62, 0.1, 0.2))
  const shoeMaterial = track(new THREE.MeshStandardMaterial({ color: 0x2b3029, metalness: 0.7, roughness: 0.45 }))

  const radius = 1.85
  modules.forEach((_item, index) => {
    const holder = new THREE.Group()
    const a = index * STEP
    holder.position.set(Math.sin(a) * radius, 0, Math.cos(a) * radius)
    holder.rotation.y = a
    wheel?.add(holder)

    const texture = track(cardTexture(index))
    const faceMaterial = track(
      new THREE.MeshStandardMaterial({
        map: texture,
        roughness: 0.88,
        metalness: 0.02,
        emissive: new THREE.Color(0xe8613a),
        emissiveIntensity: 0,
      }),
    )
    const materials = [edgeMaterial, edgeMaterial, edgeMaterial, edgeMaterial, faceMaterial, backMaterial]
    const card = new THREE.Mesh(cardGeometry, materials)
    card.position.y = 1.19
    card.rotation.x = -0.055
    card.rotation.z = (index % 2 === 0 ? 1 : -1) * 0.006
    card.userData.index = index
    holder.add(card)
    cardHits.push(card)
    cardMeshes.push(card)
    cardGlow.push(faceMaterial)

    const shoe = new THREE.Mesh(shoeGeometry, shoeMaterial)
    shoe.position.set(0, 0.22, 0.02)
    holder.add(shoe)

    const clipGeometry = track(new THREE.BoxGeometry(0.06, 0.24, 0.05))
    const clip = new THREE.Mesh(clipGeometry, rimMaterial)
    clip.position.set(-0.4, 0.34, 0.02)
    holder.add(clip)
    const clip2 = new THREE.Mesh(clipGeometry, rimMaterial)
    clip2.position.set(0.4, 0.34, 0.02)
    holder.add(clip2)

    if (index === 0) active.value = 0
  })

  // the read head: a brass gantry that scans whatever card faces front
  const armMaterial = track(new THREE.MeshStandardMaterial({ color: 0xbfa06a, metalness: 0.9, roughness: 0.3 }))
  const postGeometry = track(new THREE.CylinderGeometry(0.05, 0.065, 2.55, 16))
  const post = new THREE.Mesh(postGeometry, armMaterial)
  post.position.set(0, 1.28, -2.62)
  wheel?.add(post)

  const boomGeometry = track(new THREE.CylinderGeometry(0.04, 0.04, 4.5, 12))
  const boom = new THREE.Mesh(boomGeometry, armMaterial)
  boom.rotation.x = Math.PI / 2
  boom.position.set(0, 2.55, -0.4)
  wheel?.add(boom)

  const stemGeometry = track(new THREE.CylinderGeometry(0.028, 0.028, 0.32, 10))
  const stem = new THREE.Mesh(stemGeometry, armMaterial)
  stem.position.set(0, 2.36, 1.72)
  wheel?.add(stem)

  const headHousingGeometry = track(new THREE.BoxGeometry(0.54, 0.26, 0.3))
  const headHousingMaterial = track(new THREE.MeshStandardMaterial({ color: 0x22271f, metalness: 0.55, roughness: 0.4 }))
  const head = new THREE.Mesh(headHousingGeometry, headHousingMaterial)
  head.position.set(0, 2.2, 1.72)
  wheel?.add(head)

  const lensGeometry = track(new THREE.CylinderGeometry(0.06, 0.06, 0.025, 20))
  const lensMaterial = track(new THREE.MeshStandardMaterial({ color: 0xe8613a, emissive: 0xe8613a, emissiveIntensity: 2.2 }))
  const lens = new THREE.Mesh(lensGeometry, lensMaterial)
  lens.position.set(0, 2.06, 1.72)
  wheel?.add(lens)

  // the counter window on the read head
  counterCanvas = document.createElement('canvas')
  counterCanvas.width = 256
  counterCanvas.height = 96
  counterTexture = track(new THREE.CanvasTexture(counterCanvas))
  counterTexture.colorSpace = THREE.SRGBColorSpace
  const counterMaterial = track(
    new THREE.MeshBasicMaterial({ map: counterTexture, transparent: false, toneMapped: false }),
  )
  const counterGeometry = track(new THREE.PlaneGeometry(0.46, 0.17))
  const counter = new THREE.Mesh(counterGeometry, counterMaterial)
  counter.position.set(0, 2.2, 1.88)
  wheel?.add(counter)

  // contact shadow under the machine
  const shadowCanvas = document.createElement('canvas')
  shadowCanvas.width = 256
  shadowCanvas.height = 256
  const sctx = shadowCanvas.getContext('2d')
  if (sctx) {
    const gradient = sctx.createRadialGradient(128, 128, 20, 128, 128, 126)
    gradient.addColorStop(0, 'rgba(0,0,0,0.55)')
    gradient.addColorStop(0.55, 'rgba(0,0,0,0.22)')
    gradient.addColorStop(1, 'rgba(0,0,0,0)')
    sctx.fillStyle = gradient
    sctx.fillRect(0, 0, 256, 256)
  }
  const shadowTexture = track(new THREE.CanvasTexture(shadowCanvas))
  const shadowGeometry = track(new THREE.PlaneGeometry(9.4, 9.4))
  const lightTheme = document.documentElement.dataset.theme === 'light'
  const shadowMaterial = track(
    new THREE.MeshBasicMaterial({
      map: shadowTexture,
      transparent: true,
      depthWrite: false,
      opacity: lightTheme ? 0.62 : 1,
    }),
  )
  const shadow = new THREE.Mesh(shadowGeometry, shadowMaterial)
  shadow.rotation.x = -Math.PI / 2
  shadow.position.y = -0.02
  scene.add(shadow)

  // dust in the air
  const dustCount = 90
  const dustPositions = new Float32Array(dustCount * 3)
  for (let i = 0; i < dustCount; i += 1) {
    dustPositions[i * 3] = (Math.random() - 0.5) * 9
    dustPositions[i * 3 + 1] = Math.random() * 4.2
    dustPositions[i * 3 + 2] = (Math.random() - 0.5) * 7
  }
  const dustGeometry = track(new THREE.BufferGeometry())
  dustGeometry.setAttribute('position', new THREE.BufferAttribute(dustPositions, 3))
  const dustMaterial = track(
    new THREE.PointsMaterial({
      color: 0xe9e2d0,
      size: 0.022,
      transparent: true,
      opacity: 0.35,
      depthWrite: false,
    }),
  )
  const dust = new THREE.Points(dustGeometry, dustMaterial)
  scene.add(dust)

  counterLabel(0)
  bindEvents()
  resize()
  applyActive()
  if (reduced) renderOnce()
  else start()
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
  if (!renderer || !scene || !camera) return
  renderer.render(scene, camera)
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

  if (!draggingPointer) {
    angle += angularVelocity * delta
    angularVelocity *= 0.92
    if (Math.abs(angularVelocity) < 0.0015) {
      angularVelocity = 0
      const snapped = Math.round(angle / STEP) * STEP
      angle += (snapped - angle) * Math.min(1, delta * 6.5)
      targetAngle = snapped
    }
  } else {
    angle += (targetAngle - angle) * Math.min(1, delta * 22)
  }

  const settled =
    !draggingPointer &&
    Math.abs(angularVelocity) < 0.0005 &&
    Math.abs(targetAngle - angle) < 0.0008 &&
    Math.abs(pointerNdcX * 0.09 - (wheel?.position.x ?? 0)) < 0.001 &&
    Math.abs(pointerNdcY * 0.05 + 0.03 - (wheel?.position.y ?? 0)) < 0.001

  if (settled) {
    // A still desk costs nothing: stop redrawing until the pointer or the file moves.
    return
  }

  if (wheel) {
    wheel.rotation.y = angle
    wheel.position.x += (pointerNdcX * 0.09 - wheel.position.x) * 0.05
    wheel.position.y += (pointerNdcY * 0.05 + 0.03 - wheel.position.y) * 0.05
  }

  const rounded = Math.round(angle / STEP)
  const index = ((rounded % modules.length) + modules.length) % modules.length
  if (index !== active.value) {
    active.value = index
    applyActive()
  }

  renderOnce()
}

function applyActive() {
  counterLabel(active.value)
  const focus = hovered.value ?? active.value
  cardGlow.forEach((material, index) => {
    material.emissiveIntensity = index === focus ? 0.16 : 0
  })
  cardMeshes.forEach((mesh, index) => {
    const lifted = index === focus ? 0.055 : 0
    mesh.position.y += (1.19 + lifted - mesh.position.y) * 0.4
  })
  if (canvas.value) {
    canvas.value.style.cursor = draggingPointer ? 'grabbing' : hovered.value !== null ? 'pointer' : 'grab'
  }
  renderOnce()
}

function updatePointer(event: PointerEvent) {
  if (!canvas.value) return
  const rect = canvas.value.getBoundingClientRect()
  pointerNdcX = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointerNdcY = ((event.clientY - rect.top) / rect.height) * 2 - 1
  pointerNdc.set(pointerNdcX, -pointerNdcY)
}

function raycast() {
  if (!camera || !cardHits.length) return
  raycaster.setFromCamera(pointerNdc, camera)
  const hits = raycaster.intersectObjects(cardHits, false)
  const next = hits.length ? Number(hits[0].object.userData.index) : null
  if (next !== lastHover) {
    lastHover = next
    hovered.value = next
    applyActive()
  }
}

function onPointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  draggingPointer = true
  dragging.value = true
  moved = 0
  startX = event.clientX
  angularVelocity = 0
  updatePointer(event)
  canvas.value?.setPointerCapture(event.pointerId)
  applyActive()
}

function onPointerMove(event: PointerEvent) {
  updatePointer(event)
  if (draggingPointer) {
    const dx = event.clientX - startX
    startX = event.clientX
    moved += Math.abs(dx)
    targetAngle += dx * 0.0075
    angle += dx * 0.0075
    angularVelocity = dx * 0.06
  }
  raycast()
  if (reduced) {
    if (wheel) wheel.rotation.y = angle
    renderOnce()
  }
}

function onPointerUp(event: PointerEvent) {
  if (!draggingPointer) return
  draggingPointer = false
  dragging.value = false
  canvas.value?.releasePointerCapture(event.pointerId)
  if (moved < 6 && hovered.value !== null && props.interactive) {
    const index = hovered.value
    const front = Math.round(angle / STEP)
    const currentIndex = ((front % modules.length) + modules.length) % modules.length
    if (currentIndex === index) {
      void router.push(modules[index].to)
    } else {
      const delta = normalise(-index * STEP - angle)
      targetAngle = angle + delta
      angularVelocity = 0
    }
  }
  applyActive()
}

function onPointerCancel() {
  draggingPointer = false
  dragging.value = false
  applyActive()
}

function onPointerLeave() {
  pointerNdcX = 0
  pointerNdcY = 0
  pointerNdc.set(0, 0)
  if (!draggingPointer) {
    lastHover = null
    hovered.value = null
    applyActive()
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight' && event.key !== 'Enter') return
  event.preventDefault()
  if (event.key === 'Enter') {
    if (props.interactive) void router.push(modules[active.value].to)
    return
  }
  const direction = event.key === 'ArrowRight' ? 1 : -1
  targetAngle = angle - direction * STEP
  angularVelocity = 0
  if (reduced) {
    angle = targetAngle
    if (wheel) wheel.rotation.y = angle
    const index = ((Math.round(angle / STEP) % modules.length) + modules.length) % modules.length
    active.value = index
    applyActive()
  }
}

function onVisibilityChange() {
  visible = document.visibilityState === 'visible'
  if (visible) start()
  else stop()
}

function setHover(index: number | null) {
  hovered.value = index
  lastHover = index
  applyActive()
}

function jumpTo(index: number) {
  const delta = normalise(-index * STEP - angle)
  targetAngle = angle + delta
  angularVelocity = 0
  if (reduced) {
    angle = targetAngle
    if (wheel) wheel.rotation.y = angle
    active.value = index
    applyActive()
  }
}

function bindEvents() {
  const el = canvas.value
  if (!el) return
  el.addEventListener('pointerdown', onPointerDown)
  el.addEventListener('pointermove', onPointerMove)
  el.addEventListener('pointerup', onPointerUp)
  el.addEventListener('pointercancel', onPointerCancel)
  el.addEventListener('pointerleave', onPointerLeave)
  el.addEventListener('keydown', onKeydown)
  document.addEventListener('visibilitychange', onVisibilityChange)
  listeners.push(() => {
    el.removeEventListener('pointerdown', onPointerDown)
    el.removeEventListener('pointermove', onPointerMove)
    el.removeEventListener('pointerup', onPointerUp)
    el.removeEventListener('pointercancel', onPointerCancel)
    el.removeEventListener('pointerleave', onPointerLeave)
    el.removeEventListener('keydown', onKeydown)
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
  const stageEl = host.value
  resetPointer = () => {
    pointerNdcX = 0
    pointerNdcY = 0
  }
  stageEl?.addEventListener('pointerleave', resetPointer)
  if (stageEl && resetPointer) {
    const removeReset = resetPointer
    listeners.push(() => stageEl.removeEventListener('pointerleave', removeReset))
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
  cardHits.length = 0
  cardMeshes.length = 0
  cardGlow.length = 0
  renderer?.dispose()
  renderer?.forceContextLoss()
  renderer = null
  scene = null
  camera = null
  wheel = null
  counterTexture = null
  counterCanvas = null
}

onMounted(async () => {
  reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  await Promise.race([
    Promise.all([
      document.fonts.load('500 62px "Newsreader Variable"'),
      document.fonts.load('500 19px "Instrument Sans Variable"'),
    ]).catch(() => undefined),
    new Promise((resolve) => setTimeout(resolve, 900)),
  ])
  buildScene()
})

onBeforeUnmount(() => {
  disposeScene()
})
</script>

<template>
  <div
    class="cardfile"
    :class="[`cardfile--${mode}`, { 'cardfile--fallback': fallback, 'cardfile--dragging': dragging }]"
  >
    <div ref="host" class="cardfile__stage">
      <canvas
        ref="canvas"
        class="cardfile__canvas"
        tabindex="0"
        role="img"
        :aria-label="`Card file of ${modules.length} modules. Use arrow keys to turn the file, Enter to open ${readout.label}.`"
      />

      <div v-if="fallback" class="cardfile__paper" aria-hidden="true">
        <span>{{ readout.label }}</span>
        <p>{{ readout.short }}</p>
      </div>

      <div class="cardfile__tube" aria-hidden="true">
        <span class="cardfile__tube-cap" />
        <span class="cardfile__tube-line" />
      </div>

      <div class="cardfile__sticker">
        <span class="cardfile__sticker-name">The card file</span>
        <p class="cardfile__sticker-copy">{{ readout.short }}</p>
      </div>

      <span class="cardfile__hint">{{ interactive ? 'Turn the file · open a card' : 'The card file stays in view' }}</span>
    </div>

    <div class="cardfile__keys" aria-label="Card file modules">
      <template v-for="(item, index) in modules" :key="item.key">
        <RouterLink
          v-if="interactive"
          :to="item.to"
          class="cardfile__key"
          :class="{ 'is-live': active === index, 'is-hot': hovered === index }"
          @mouseenter="setHover(index)"
          @mouseleave="setHover(null)"
          @focus="jumpTo(index)"
          @blur="setHover(null)"
        >
          {{ item.label }}
        </RouterLink>
        <button
          v-else
          type="button"
          class="cardfile__key"
          :class="{ 'is-live': active === index, 'is-hot': hovered === index }"
          @click="jumpTo(index)"
          @mouseenter="setHover(index)"
          @mouseleave="setHover(null)"
        >
          {{ item.label }}
        </button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.cardfile {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 34rem;
  padding: 0.35rem;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background:
    linear-gradient(180deg, rgb(255 255 255 / 0.03), transparent 30%),
    var(--surface);
  box-shadow: var(--shadow-plate);
  isolation: isolate;
  overflow: hidden;
}
.cardfile--compact {
  min-height: 27rem;
}
.cardfile__stage {
  position: relative;
  flex: 1;
  min-height: 0;
  border-radius: 1px;
  background:
    radial-gradient(ellipse 70% 55% at 50% 8%, rgb(217 173 99 / 0.1), transparent 65%),
    radial-gradient(ellipse 90% 80% at 50% 110%, rgb(0 0 0 / 0.6), transparent 70%),
    var(--desk);
  overflow: hidden;
}
.cardfile__canvas {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  touch-action: pan-y;
  cursor: grab;
}
.cardfile__canvas:focus-visible {
  outline-offset: -3px;
}
.cardfile--dragging .cardfile__canvas {
  cursor: grabbing;
}

/* a brass draw-tube detail, like the rod on a filing spindle */
.cardfile__tube {
  position: absolute;
  top: 0.9rem;
  left: 1rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  pointer-events: none;
  opacity: 0.7;
}
.cardfile__tube-cap {
  width: 0.4rem;
  height: 0.4rem;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 40%, transparent);
}
.cardfile__tube-line {
  width: 2.6rem;
  height: 1px;
  background: linear-gradient(90deg, var(--accent), transparent);
}

.cardfile__sticker {
  position: absolute;
  left: 0.9rem;
  bottom: 0.9rem;
  max-width: 12.5rem;
  padding: 0.6rem 0.7rem 0.65rem;
  border-radius: 1px;
  background: var(--paper);
  color: var(--paper-ink);
  box-shadow: 0 8px 20px -14px rgb(0 0 0 / 0.9);
  transform: rotate(-1.1deg);
  pointer-events: none;
}
.cardfile__sticker-name {
  display: block;
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-weight: 500;
  letter-spacing: -0.01em;
  line-height: 1.1;
}
.cardfile__sticker-copy {
  margin: 0.25rem 0 0;
  color: color-mix(in srgb, var(--paper-ink) 72%, transparent);
  font-size: 0.74rem;
  line-height: 1.4;
}
.cardfile__hint {
  position: absolute;
  right: 1rem;
  bottom: 1.1rem;
  color: rgb(233 226 208 / 0.62);
  font-size: 0.72rem;
  pointer-events: none;
}

.cardfile__keys {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 1px;
  margin-top: 0.35rem;
  border: 1px solid var(--line);
  border-radius: 1px;
  background: var(--line);
  overflow: hidden;
}
.cardfile__key {
  padding: 0.5rem 0.4rem;
  border: 0;
  background: var(--surface-2);
  color: var(--text-faint);
  font-size: 0.74rem;
  font-weight: 500;
  text-align: center;
  text-decoration: none;
  white-space: nowrap;
  transition:
    background 140ms ease,
    color 140ms ease;
}
.cardfile__key:hover,
.cardfile__key.is-hot {
  background: var(--surface-3);
  color: var(--text);
}
.cardfile__key.is-live {
  background: var(--paper);
  color: var(--paper-ink);
}
[data-theme='light'] .cardfile__key.is-live {
  background: var(--text);
  color: var(--paper);
}
.cardfile__paper {
  position: absolute;
  inset: 1.4rem;
  display: grid;
  align-content: start;
  gap: 0.6rem;
  padding: 1.2rem;
  border-radius: 2px;
  background: var(--paper);
  color: var(--paper-ink);
  transform: rotate(-2deg);
  box-shadow: 0 18px 30px -20px rgb(0 0 0 / 0.9);
}
.cardfile__paper span {
  font-family: var(--font-display);
  font-size: 2rem;
}
.cardfile__paper p {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
}

@media (max-width: 860px) {
  .cardfile,
  .cardfile--compact {
    min-height: 26rem;
  }
  .cardfile__sticker {
    max-width: 12rem;
  }
  .cardfile__hint {
    display: none;
  }
}
@media (max-width: 560px) {
  .cardfile,
  .cardfile--compact {
    min-height: 23rem;
  }
  .cardfile__keys {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .cardfile__sticker {
    left: 0.6rem;
    bottom: 0.6rem;
    padding: 0.5rem 0.55rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .cardfile__key {
    transition: none;
  }
}
</style>
