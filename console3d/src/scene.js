// SecureGuard Ops Center — Three.js scene.
// Ground grid, distance rings, starfield, pulsing agent core, 12 channel
// pylons, asset nodes with CSS2D labels, UnrealBloomPass, OrbitControls,
// resize handling and mobile perf (capped pixel ratio, visibility pause).
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { CSS2DRenderer, CSS2DObject } from 'three/examples/jsm/renderers/CSS2DRenderer.js'
import { CHANNELS } from './ops.js'

const STATUS_COLOR = {
  ONLINE: 0x00e676,
  OFFLINE: 0xff1744,
  MAINTENANCE: 0xffc400,
  SEARCHING: 0x448aff,
  UNKNOWN: 0x8899aa
}

export function createScene(container, labelsContainer) {
  const renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2))
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.domElement.classList.add('three')
  container.appendChild(renderer.domElement)

  const labelRenderer = new CSS2DRenderer()
  labelRenderer.setSize(container.clientWidth, container.clientHeight)
  labelRenderer.domElement.id = 'labels'
  // Replace the static #labels placeholder with the CSS2D root.
  const old = document.getElementById('labels')
  if (old && old.parentNode) old.parentNode.removeChild(old)
  labelRenderer.domElement.style.position = 'absolute'
  labelRenderer.domElement.style.top = '0'
  labelRenderer.domElement.style.pointerEvents = 'none'
  labelsContainer.appendChild(labelRenderer.domElement)

  const scene = new THREE.Scene()
  scene.background = new THREE.Color(0x060d18)
  scene.fog = new THREE.FogExp2(0x060d18, 0.0009)

  const camera = new THREE.PerspectiveCamera(62, container.clientWidth / container.clientHeight, 0.1, 4000)
  camera.position.set(0, 190, 240)

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 40
  controls.maxDistance = 900
  controls.maxPolarAngle = Math.PI * 0.52
  controls.target.set(0, 0, 0)

  scene.add(new THREE.AmbientLight(0x274160, 1.6))
  const dir = new THREE.DirectionalLight(0xbfe3ff, 1.4)
  dir.position.set(300, 500, 200)
  scene.add(dir)
  const rim = new THREE.PointLight(0x00d4ff, 300, 800, 2)
  rim.position.set(0, 60, 0)
  scene.add(rim)

  // ---- ground grid -------------------------------------------------
  const gridSize = 1200
  const grid = new THREE.GridHelper(gridSize, 60, 0x153a5c, 0x0b1e33)
  grid.material.opacity = 0.5
  grid.material.transparent = true
  grid.position.y = -0.5
  scene.add(grid)

  // ---- distance rings ----------------------------------------------
  const ringGeo = new THREE.RingGeometry(0.985, 1.0, 128)
  ;[150, 300, 450].forEach((r, i) => {
    const ring = new THREE.Mesh(
      ringGeo,
      new THREE.MeshBasicMaterial({ color: 0x00d4ff, side: THREE.DoubleSide, transparent: true, opacity: 0.16 - i * 0.03 })
    )
    ring.rotation.x = -Math.PI / 2
    ring.scale.setScalar(r)
    ring.position.y = 0.2
    scene.add(ring)
  })

  // ---- starfield ----------------------------------------------------
  const starGeo = new THREE.BufferGeometry()
  const starN = 1800
  const starPos = new Float32Array(starN * 3)
  for (let i = 0; i < starN; i++) {
    const th = Math.random() * Math.PI * 2
    const ph = Math.acos(2 * Math.random() - 1)
    const r = 800 + Math.random() * 1600
    starPos[i * 3] = r * Math.sin(ph) * Math.cos(th)
    starPos[i * 3 + 1] = Math.abs(r * Math.cos(ph)) * 0.8 + 60
    starPos[i * 3 + 2] = r * Math.sin(ph) * Math.sin(th)
  }
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPos, 3))
  const stars = new THREE.Points(starGeo, new THREE.PointsMaterial({ color: 0x9fc6ff, size: 1.6, sizeAttenuation: true, transparent: true, opacity: 0.7 }))
  scene.add(stars)

  // ---- agent core (pulsing) ----------------------------------------
  const coreGroup = new THREE.Group()
  const core = new THREE.Mesh(new THREE.IcosahedronGeometry(16, 2), new THREE.MeshStandardMaterial({ color: 0x00d4ff, emissive: 0x00d4ff, emissiveIntensity: 1.6, roughness: 0.2, metalness: 0.4 }))
  const halo = new THREE.Mesh(new THREE.SphereGeometry(26, 32, 32), new THREE.MeshBasicMaterial({ color: 0x00d4ff, transparent: true, opacity: 0.14 }))
  coreGroup.add(core)
  coreGroup.add(halo)
  scene.add(coreGroup)

  // ---- 12 channel pylons -------------------------------------------
  const pylonMeshes = []
  CHANNELS.forEach((ch, i) => {
    const ang = (i / CHANNELS.length) * Math.PI * 2
    const radius = 150
    const x = Math.cos(ang) * radius
    const z = Math.sin(ang) * radius
    const group = new THREE.Group()
    const pillar = new THREE.Mesh(new THREE.CylinderGeometry(2.6, 3.4, 46, 10), new THREE.MeshStandardMaterial({ color: 0x1d3a5c, roughness: 0.5, metalness: 0.6 }))
    pillar.position.y = 23
    const cap = new THREE.Mesh(new THREE.SphereGeometry(4.4, 16, 16), new THREE.MeshStandardMaterial({ color: new THREE.Color(ch.color), emissive: new THREE.Color(ch.color), emissiveIntensity: 0.8, roughness: 0.3 }))
    cap.position.y = 47
    group.add(pillar)
    group.add(cap)
    group.position.set(x, 0, z)
    group.userData = { channel: ch.id, color: new THREE.Color(ch.color), usage: 0 }
    scene.add(group)
    pylonMeshes.push(group)

    const el = document.createElement('div')
    el.className = 'css-label'
    el.textContent = ch.label
    const label = new CSS2DObject(el)
    label.position.set(x, 56, z)
    scene.add(label)
  })

  // ---- asset nodes (placeholder, rebuilt on data) -------------------
  const assetNodes = new Map() // id -> { group, label }
  const assetGroup = new THREE.Group()
  scene.add(assetGroup)

  function disposeAssetNode(entry) {
    if (!entry) return
    assetGroup.remove(entry.group)
    scene.remove(entry.group)
    entry.label.element.remove()
  }

  const nodeGeo = new THREE.SphereGeometry(5, 20, 20)
  const ringNodeGeo = new THREE.RingGeometry(0.9, 1.0, 24)

  function makeAssetNode(asset) {
    const id = String(asset.id)
    const color = STATUS_COLOR[String(asset.status).toUpperCase()] || STATUS_COLOR.UNKNOWN
    const group = new THREE.Group()
    const mesh = new THREE.Mesh(nodeGeo, new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.9, roughness: 0.3 }))
    const ring = new THREE.Mesh(ringNodeGeo, new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide, transparent: true, opacity: 0.5 }))
    ring.rotation.x = -Math.PI / 2
    ring.scale.setScalar(6)
    ring.position.y = -7
    group.add(mesh)
    group.add(ring)
    const el = document.createElement('div')
    el.className = 'css-label'
    el.textContent = asset.shortName || asset.name || id
    const label = new CSS2DObject(el)
    label.position.set(0, 14, 0)
    group.add(label)
    const [x, y, z] = assetPosition(asset)
    group.position.set(x, y, z)
    group.userData = { id, color, status: String(asset.status).toUpperCase() }
    assetGroup.add(group)
    return { group, label }
  }

  function assetPosition(asset) {
    if (typeof asset.latitude === 'number' && typeof asset.longitude === 'number' && isFinite(asset.latitude + asset.longitude)) {
      // Berlin-centre-relative projection; deterministic so diffs are stable.
      const y = (asset.latitude - 52.52) * 1300
      const x = (asset.longitude - 13.405) * 800
      return [x, 6, y]
    }
    const i = Math.abs(hashCode(String(asset.id || asset.mac || asset.shortName)))
    const ang = (i % 360) * (Math.PI / 180)
    const r = 90 + (i % 70)
    return [Math.cos(ang) * r, 6, Math.sin(ang) * r]
  }

  function hashCode(s) {
    let h = 0
    for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0
    return h
  }

  function refreshAssets(assets, selectedIds) {
    const desired = new Set((assets || []).map((a) => String(a.id)))
    const sel = new Set((selectedIds || []).map(String))
    const built = 0
    // Remove nodes that no longer exist.
    assetNodes.forEach((entry, id) => {
      if (!desired.has(id)) {
        disposeAssetNode(entry)
        assetNodes.delete(id)
      }
    })
    // Add new nodes.
    ;(assets || []).forEach((asset) => {
      const id = String(asset.id)
      if (!assetNodes.has(id)) {
        assetNodes.set(id, makeAssetNode(asset))
      }
    })
    // Update changed nodes only (no needless DOM/geometry rebuilds).
    ;(assets || []).forEach((asset) => {
      const id = String(asset.id)
      const entry = assetNodes.get(id)
      if (!entry) return
      const status = String(asset.status).toUpperCase()
      const color = STATUS_COLOR[status] || STATUS_COLOR.UNKNOWN
      const g = entry.group
      if (g.userData.status !== status) {
        g.userData.status = status
        const leaf = g.children.find((c) => c.isMesh && c.geometry === nodeGeo)
        if (leaf) {
          leaf.material.color.setHex(color)
          leaf.material.emissive.setHex(color)
        }
        entry.label.element.className = 'css-label' + (status === 'OFFLINE' ? ' alarm' : '')
      }
      const [x, y, z] = assetPosition(asset)
      g.position.set(x, y, z)
      const selNow = sel.has(id)
      if (g.userData.selected !== selNow) {
        g.userData.selected = selNow
        const ring = g.children.find((c) => c.isMesh && c.geometry === ringNodeGeo)
        if (ring) ring.material.opacity = selNow ? 1 : 0.5
        entry.label.element.style.outline = selNow ? '1px solid #00d4ff' : 'none'
      }
    })
  }

  function setPylonUsage(channelLoad, opacityScale) {
    pylonMeshes.forEach((group) => {
      const usage = Number(channelLoad && channelLoad[group.userData.channel]) || 0
      const g = group.userData
      g.usage = usage
      const cap = group.children.find((c) => c.isMesh && c.geometry.type === 'SphereGeometry')
      if (cap) {
        const intensity = 0.6 + usage * 2.6
        cap.material.emissiveIntensity = intensity * (opacityScale || 1)
        const s = 1 + usage * 1.4
        cap.scale.setScalar(s)
      }
      const pillar = group.children.find((c) => c.isMesh && c.geometry.type === 'CylinderGeometry')
      if (pillar) {
        const h = 20 + usage * 26
        pillar.scale.y = h / 46
        pillar.position.y = h / 2
      }
    })
  }

  // ---- post-processing (bloom) -------------------------------------
  const composer = new EffectComposer(renderer, new THREE.WebGLRenderTarget(container.clientWidth, container.clientHeight, { type: THREE.HalfFloatType }))
  composer.addPass(new RenderPass(scene, camera))
  const bloom = new UnrealBloomPass(new THREE.Vector2(container.clientWidth, container.clientHeight), 0.55, 0.4, 0.72)
  composer.addPass(bloom)

  // ---- animation loop ----------------------------------------------
  let running = true
  let raf = 0
  let t = 0
  function animate() {
    if (!running) return
    raf = requestAnimationFrame(animate)
    t += 1
    const dt = 1 / 60
    const pulse = 1 + Math.sin(t * 0.05) * 0.12
    core.scale.setScalar(pulse)
    halo.scale.setScalar(pulse * 1.12)
    halo.material.opacity = 0.1 + Math.sin(t * 0.05) * 0.04
    coreGroup.rotation.y += dt * 0.25
    stars.rotation.y += dt * 0.004
    controls.update()
    composer.render()
    labelRenderer.render(scene, camera)
  }

  function start() {
    if (running) return
    running = true
    animate()
  }

  function stop() {
    running = false
    if (raf) cancelAnimationFrame(raf)
    raf = 0
  }

  function resize() {
    const w = container.clientWidth
    const h = container.clientHeight
    if (!w || !h) return
    camera.aspect = w / h
    camera.updateProjectionMatrix()
    renderer.setSize(w, h)
    composer.setSize(w, h)
    labelRenderer.setSize(w, h)
  }

  function dispose() {
    stop()
    controls.dispose()
    composer.dispose()
    renderer.dispose()
    assetNodes.forEach((entry) => disposeAssetNode(entry))
    assetNodes.clear()
    if (renderer.domElement.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement)
    if (labelRenderer.domElement.parentNode) labelRenderer.domElement.parentNode.removeChild(labelRenderer.domElement)
  }

  window.addEventListener('resize', resize)

  return {
    start,
    stop,
    resize,
    dispose,
    refreshAssets,
    setPylonUsage,
    renderer,
    camera,
    controls
  }
}
