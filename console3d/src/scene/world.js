/**
 * 3D-Lagebild (Three.js, MIT-Lizenz – dauerhaft kostenfrei, keine Tier-Grenzen).
 *
 * Aufbau der Szene:
 *   • Bodenraster + Horizontglühen  → räumlicher Kontext
 *   • Agent-Kern in der Mitte       → Zustand des selbstlernenden Agenten
 *   • Kanal-Ring (12 Pylone)        → Detection-/Echtzeit-Kanäle
 *   • Asset-Knoten                  → Position, Status, Signal, Akku
 *   • Effekte                       → Detektions-Impulse, Kanalstrahlen,
 *                                     Geofence, Heatmap, Radar-Sweep
 */

import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'

import { CHANNELS, CHANNEL_MAP, STATUS } from '../data/catalog.js'
import { createProjector } from '../core/geo.js'

const TAU = Math.PI * 2
const CHANNEL_RADIUS = 34
const GROUND_RADIUS = 120

export function createWorld (container, labelLayer, options = {}) {
  const projector = createProjector(options.origin || { lat: 51.4344, lon: 6.7623 })

  /* ---------------- Renderer / Szene / Kamera ---------------- */

  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.setSize(container.clientWidth, container.clientHeight)
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.shadowMap.enabled = false
  container.appendChild(renderer.domElement)

  const scene = new THREE.Scene()
  scene.background = new THREE.Color('#050d18')
  scene.fog = new THREE.FogExp2('#050d18', 0.0085)

  const camera = new THREE.PerspectiveCamera(52, container.clientWidth / container.clientHeight, 0.5, 700)
  camera.position.set(0, 42, 62)

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.06
  controls.minDistance = 14
  controls.maxDistance = 190
  controls.maxPolarAngle = Math.PI * 0.478
  controls.target.set(0, 2, 0)
  controls.autoRotateSpeed = 0.32

  /* ---------------- Licht ---------------- */

  scene.add(new THREE.AmbientLight('#4a6f96', 1.1))
  const key = new THREE.DirectionalLight('#8fd7ff', 1.5)
  key.position.set(40, 70, 30)
  scene.add(key)
  const rim = new THREE.DirectionalLight('#0072ff', 0.9)
  rim.position.set(-50, 26, -40)
  scene.add(rim)
  const coreLight = new THREE.PointLight('#00d4ff', 120, 90, 2)
  coreLight.position.set(0, 9, 0)
  scene.add(coreLight)

  /* ---------------- Boden & Raster ---------------- */

  const groundGroup = new THREE.Group()
  scene.add(groundGroup)

  const groundTexture = makeRadialTexture()
  const ground = new THREE.Mesh(
    new THREE.CircleGeometry(GROUND_RADIUS, 96),
    new THREE.MeshBasicMaterial({
      map: groundTexture, transparent: true, opacity: 0.92, depthWrite: false
    })
  )
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.02
  groundGroup.add(ground)

  const gridFine = new THREE.GridHelper(GROUND_RADIUS * 2, 120, '#12324f', '#0e2740')
  gridFine.material.transparent = true
  gridFine.material.opacity = 0.32
  gridFine.material.depthWrite = false
  groundGroup.add(gridFine)

  const gridCoarse = new THREE.GridHelper(GROUND_RADIUS * 2, 12, '#1d4d73', '#173d5d')
  gridCoarse.material.transparent = true
  gridCoarse.material.opacity = 0.5
  gridCoarse.material.depthWrite = false
  gridCoarse.position.y = 0.01
  groundGroup.add(gridCoarse)

  // Entfernungsringe (250 m / 500 m / 1 km)
  const rangeRings = new THREE.Group()
  ;[6, 12, 24].forEach((r, i) => {
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(r - 0.06, r + 0.06, 128),
      new THREE.MeshBasicMaterial({ color: '#1f6c99', transparent: true, opacity: 0.35 - i * 0.07, side: THREE.DoubleSide, depthWrite: false })
    )
    ring.rotation.x = -Math.PI / 2
    ring.position.y = 0.02
    rangeRings.add(ring)
  })
  groundGroup.add(rangeRings)

  // Horizontglühen
  const horizon = new THREE.Mesh(
    new THREE.RingGeometry(GROUND_RADIUS - 26, GROUND_RADIUS, 128),
    new THREE.MeshBasicMaterial({
      color: '#0b4f7a', transparent: true, opacity: 0.32,
      side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false
    })
  )
  horizon.rotation.x = -Math.PI / 2
  horizon.position.y = 0.05
  groundGroup.add(horizon)

  // Sternenstaub für Tiefe
  scene.add(makeStarfield())

  /* ---------------- Agent-Kern ---------------- */

  const agentGroup = new THREE.Group()
  agentGroup.position.y = 9
  scene.add(agentGroup)

  const coreInner = new THREE.Mesh(
    new THREE.IcosahedronGeometry(2.1, 2),
    new THREE.MeshStandardMaterial({
      color: '#00d4ff', emissive: '#00d4ff', emissiveIntensity: 2.4,
      roughness: 0.25, metalness: 0.1
    })
  )
  agentGroup.add(coreInner)

  const coreShell = new THREE.Mesh(
    new THREE.IcosahedronGeometry(3.5, 1),
    new THREE.MeshBasicMaterial({ color: '#3fe0ff', wireframe: true, transparent: true, opacity: 0.36 })
  )
  agentGroup.add(coreShell)

  const coreHalo = new THREE.Mesh(
    new THREE.SphereGeometry(4.6, 32, 32),
    new THREE.MeshBasicMaterial({
      color: '#0091ff', transparent: true, opacity: 0.1,
      blending: THREE.AdditiveBlending, side: THREE.BackSide, depthWrite: false
    })
  )
  agentGroup.add(coreHalo)

  const orbitRings = []
  for (let i = 0; i < 3; i++) {
    const ring = new THREE.Mesh(
      new THREE.TorusGeometry(5.2 + i * 1.5, 0.055, 10, 128),
      new THREE.MeshBasicMaterial({ color: i === 1 ? '#9c6bff' : '#00e0ff', transparent: true, opacity: 0.55 })
    )
    ring.rotation.x = Math.PI / 2 + (i - 1) * 0.55
    ring.rotation.z = i * 0.6
    orbitRings.push(ring)
    agentGroup.add(ring)
  }

  // Bodenanker unter dem Kern
  const coreBeam = new THREE.Mesh(
    new THREE.CylinderGeometry(0.14, 1.6, 9, 24, 1, true),
    new THREE.MeshBasicMaterial({
      color: '#00d4ff', transparent: true, opacity: 0.14,
      blending: THREE.AdditiveBlending, side: THREE.DoubleSide, depthWrite: false
    })
  )
  coreBeam.position.y = 4.5
  scene.add(coreBeam)

  /* ---------------- Kanal-Pylone ---------------- */

  const channelGroup = new THREE.Group()
  scene.add(channelGroup)
  const channelNodes = new Map()

  CHANNELS.forEach((channel, index) => {
    const angle = (index / CHANNELS.length) * TAU
    const x = Math.cos(angle) * CHANNEL_RADIUS
    const z = Math.sin(angle) * CHANNEL_RADIUS
    const group = new THREE.Group()
    group.position.set(x, 0, z)

    const mast = new THREE.Mesh(
      new THREE.CylinderGeometry(0.08, 0.16, 4.4, 8),
      new THREE.MeshStandardMaterial({ color: '#2c4a68', roughness: 0.6, metalness: 0.4 })
    )
    mast.position.y = 2.2
    group.add(mast)

    const head = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.62, 0),
      new THREE.MeshStandardMaterial({
        color: channel.color, emissive: channel.color, emissiveIntensity: 1.1, roughness: 0.3
      })
    )
    head.position.y = 4.9
    group.add(head)

    const disc = new THREE.Mesh(
      new THREE.RingGeometry(0.9, 1.35, 32),
      new THREE.MeshBasicMaterial({
        color: channel.color, transparent: true, opacity: 0.28,
        side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false
      })
    )
    disc.rotation.x = -Math.PI / 2
    disc.position.y = 0.06
    group.add(disc)

    channelGroup.add(group)
    channelNodes.set(channel.id, { group, head, disc, base: new THREE.Vector3(x, 4.9, z), pulse: 0 })
  })

  // Verbindungsring zwischen den Pylonen
  const channelRing = new THREE.Mesh(
    new THREE.TorusGeometry(CHANNEL_RADIUS, 0.05, 8, 200),
    new THREE.MeshBasicMaterial({ color: '#1a5f8a', transparent: true, opacity: 0.5 })
  )
  channelRing.rotation.x = Math.PI / 2
  channelRing.position.y = 0.08
  scene.add(channelRing)

  /* ---------------- Asset-Knoten ---------------- */

  const assetGroup = new THREE.Group()
  scene.add(assetGroup)
  /** @type {Map<string, any>} */
  const assetNodes = new Map()
  const pickables = []

  function buildAssetNode (asset) {
    const color = new THREE.Color(STATUS[asset.status]?.color || '#78909c')
    const group = new THREE.Group()

    const pillar = new THREE.Mesh(
      new THREE.CylinderGeometry(0.22, 0.3, 3.2, 12),
      new THREE.MeshStandardMaterial({ color: '#22405d', roughness: 0.55, metalness: 0.45 })
    )
    pillar.position.y = 1.6
    group.add(pillar)

    const crystal = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.95, 0),
      new THREE.MeshStandardMaterial({
        color, emissive: color, emissiveIntensity: 1.5, roughness: 0.22, metalness: 0.15
      })
    )
    crystal.position.y = 4.1
    group.add(crystal)

    const glow = new THREE.Mesh(
      new THREE.SphereGeometry(1.5, 20, 20),
      new THREE.MeshBasicMaterial({
        color, transparent: true, opacity: 0.14,
        blending: THREE.AdditiveBlending, side: THREE.BackSide, depthWrite: false
      })
    )
    glow.position.y = 4.1
    group.add(glow)

    const base = new THREE.Mesh(
      new THREE.RingGeometry(0.85, 1.25, 40),
      new THREE.MeshBasicMaterial({
        color, transparent: true, opacity: 0.55, side: THREE.DoubleSide, depthWrite: false
      })
    )
    base.rotation.x = -Math.PI / 2
    base.position.y = 0.04
    group.add(base)

    const beam = new THREE.Mesh(
      new THREE.CylinderGeometry(0.05, 0.6, 4.1, 16, 1, true),
      new THREE.MeshBasicMaterial({
        color, transparent: true, opacity: 0.12,
        blending: THREE.AdditiveBlending, side: THREE.DoubleSide, depthWrite: false
      })
    )
    beam.position.y = 2.05
    group.add(beam)

    // Auswahl-Retikel
    const reticle = new THREE.Mesh(
      new THREE.RingGeometry(1.7, 1.95, 4),
      new THREE.MeshBasicMaterial({ color: '#00d4ff', transparent: true, opacity: 0.9, side: THREE.DoubleSide })
    )
    reticle.rotation.x = -Math.PI / 2
    reticle.position.y = 0.12
    reticle.visible = false
    group.add(reticle)

    // Geofence
    const fence = new THREE.Mesh(
      new THREE.RingGeometry(5.4, 5.7, 64),
      new THREE.MeshBasicMaterial({
        color: '#9c6bff', transparent: true, opacity: 0.32,
        side: THREE.DoubleSide, depthWrite: false
      })
    )
    fence.rotation.x = -Math.PI / 2
    fence.position.y = 0.06
    fence.visible = false
    group.add(fence)

    // Unsichtbarer, großzügiger Klickkörper
    const hit = new THREE.Mesh(
      new THREE.CylinderGeometry(1.8, 1.8, 6, 8),
      new THREE.MeshBasicMaterial({ visible: false })
    )
    hit.position.y = 3
    hit.userData.assetId = asset.id
    group.add(hit)
    pickables.push(hit)

    // Akku-Anzeige als kleiner Balken
    const battTrack = new THREE.Mesh(
      new THREE.BoxGeometry(1.5, 0.1, 0.1),
      new THREE.MeshBasicMaterial({ color: '#1d3348' })
    )
    battTrack.position.set(0, 5.6, 0)
    group.add(battTrack)

    const battFill = new THREE.Mesh(
      new THREE.BoxGeometry(1.5, 0.14, 0.14),
      new THREE.MeshBasicMaterial({ color: '#00e676' })
    )
    battFill.position.set(0, 5.6, 0)
    group.add(battFill)

    assetGroup.add(group)

    const label = document.createElement('div')
    label.className = 'tag3d'
    labelLayer.appendChild(label)

    return {
      id: asset.id, group, crystal, glow, base, beam, reticle, fence, hit,
      battFill, battTrack, label, color, pulse: 0, target: new THREE.Vector3()
    }
  }

  /* ---------------- Effekte ---------------- */

  const effectGroup = new THREE.Group()
  scene.add(effectGroup)
  const pulses = []
  const beams = []

  function spawnPulse (position, color) {
    const mesh = new THREE.Mesh(
      new THREE.RingGeometry(0.6, 0.9, 48),
      new THREE.MeshBasicMaterial({
        color, transparent: true, opacity: 0.85,
        side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false
      })
    )
    mesh.rotation.x = -Math.PI / 2
    mesh.position.copy(position)
    mesh.position.y = 0.14
    effectGroup.add(mesh)
    pulses.push({ mesh, life: 0, max: 1.5 })
  }

  function spawnBeam (from, to, color) {
    const mid = from.clone().add(to).multiplyScalar(0.5)
    mid.y += from.distanceTo(to) * 0.28 + 4
    const curve = new THREE.QuadraticBezierCurve3(from.clone(), mid, to.clone())
    const geometry = new THREE.TubeGeometry(curve, 26, 0.075, 6, false)
    const material = new THREE.MeshBasicMaterial({
      color, transparent: true, opacity: 0.85, blending: THREE.AdditiveBlending, depthWrite: false
    })
    const mesh = new THREE.Mesh(geometry, material)
    effectGroup.add(mesh)
    beams.push({ mesh, life: 0, max: 0.95 })
  }

  // Radar-Sweep
  const sweep = new THREE.Mesh(
    new THREE.CircleGeometry(CHANNEL_RADIUS + 6, 64, 0, 0.55),
    new THREE.MeshBasicMaterial({
      color: '#00d4ff', transparent: true, opacity: 0.16,
      side: THREE.DoubleSide, blending: THREE.AdditiveBlending, depthWrite: false
    })
  )
  sweep.rotation.x = -Math.PI / 2
  sweep.position.y = 0.1
  sweep.visible = false
  scene.add(sweep)
  let sweepTime = 0

  // Heatmap
  const heatGroup = new THREE.Group()
  heatGroup.visible = false
  scene.add(heatGroup)

  /* ---------------- Postprocessing ---------------- */

  const composer = new EffectComposer(renderer)
  composer.addPass(new RenderPass(scene, camera))
  const bloom = new UnrealBloomPass(
    new THREE.Vector2(container.clientWidth, container.clientHeight), 0.72, 0.62, 0.2
  )
  composer.addPass(bloom)
  composer.addPass(new OutputPass())

  /* ---------------- Interaktion ---------------- */

  const raycaster = new THREE.Raycaster()
  const pointer = new THREE.Vector2()
  let downPos = null

  renderer.domElement.addEventListener('pointerdown', (e) => {
    downPos = { x: e.clientX, y: e.clientY }
  })

  renderer.domElement.addEventListener('pointerup', (e) => {
    if (!downPos) return
    const moved = Math.hypot(e.clientX - downPos.x, e.clientY - downPos.y)
    downPos = null
    if (moved > 6) return
    const rect = renderer.domElement.getBoundingClientRect()
    pointer.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((e.clientY - rect.top) / rect.height) * 2 + 1
    raycaster.setFromCamera(pointer, camera)
    const hits = raycaster.intersectObjects(pickables, false)
    if (hits.length && options.onSelect) {
      options.onSelect(hits[0].object.userData.assetId, e.shiftKey || e.ctrlKey || e.metaKey)
    } else if (options.onSelect && !hits.length && e.detail === 2) {
      options.onSelect(null, false)
    }
  })

  /* ---------------- Kamera-Flug ---------------- */

  let flight = null

  function flyTo (position, distance = 22, height = 14) {
    const dir = new THREE.Vector3().subVectors(camera.position, controls.target).setY(0)
    if (dir.lengthSq() < 0.001) dir.set(0, 0, 1)
    dir.normalize().multiplyScalar(distance)
    flight = {
      t: 0,
      fromTarget: controls.target.clone(),
      toTarget: position.clone().setY(2.5),
      fromCam: camera.position.clone(),
      toCam: position.clone().add(new THREE.Vector3(dir.x, height, dir.z))
    }
  }

  /* ---------------- Öffentliche API ---------------- */

  let agentRunning = true
  let selection = new Set()
  const clock = new THREE.Clock()

  const world = {
    projector,

    syncAssets (assets) {
      const seen = new Set()
      for (const asset of assets) {
        seen.add(asset.id)
        let node = assetNodes.get(asset.id)
        if (!node) {
          node = buildAssetNode(asset)
          assetNodes.set(asset.id, node)
        }
        const { x, z } = projector.toScene(asset.lat, asset.lon)
        node.target.set(x, 0, z)
        const color = new THREE.Color(STATUS[asset.status]?.color || '#78909c')
        node.color = color
        node.crystal.material.color.copy(color)
        node.crystal.material.emissive.copy(color)
        node.glow.material.color.copy(color)
        node.base.material.color.copy(color)
        node.beam.material.color.copy(color)
        node.status = asset.status
        node.rssi = asset.rssi
        node.battery = asset.battery

        const pct = Math.max(0, Math.min(100, asset.battery ?? 0)) / 100
        node.battFill.scale.x = Math.max(0.02, pct)
        node.battFill.position.x = -(1.5 * (1 - pct)) / 2
        node.battFill.material.color.set(pct > 0.6 ? '#00e676' : pct > 0.25 ? '#ffc400' : '#ff4d6a')
        node.battTrack.visible = asset.battery != null
        node.battFill.visible = asset.battery != null

        node.label.innerHTML =
          `<b>${escapeHtml(asset.shortName)}</b> · ${asset.rssi ? asset.rssi + ' dBm' : '—'}`
      }

      for (const [id, node] of [...assetNodes]) {
        if (seen.has(id)) continue
        assetGroup.remove(node.group)
        node.label.remove()
        const idx = pickables.indexOf(node.hit)
        if (idx >= 0) pickables.splice(idx, 1)
        assetNodes.delete(id)
      }
    },

    setSelection (ids) {
      selection = new Set(ids)
      for (const [id, node] of assetNodes) {
        const on = selection.has(id)
        node.reticle.visible = on
        node.label.classList.toggle('is-selected', on)
      }
    },

    setAgentRunning (running) {
      agentRunning = running
      controls.autoRotate = false
      coreInner.material.emissiveIntensity = running ? 2.4 : 0.5
      coreLight.intensity = running ? 120 : 22
      coreHalo.material.opacity = running ? 0.1 : 0.03
    },

    /** Detektionsimpuls: Ring am Asset + Strahl vom zuständigen Kanal-Pylon. */
    pulseDetection (assetId, channelId) {
      const node = assetNodes.get(assetId)
      const channel = channelNodes.get(channelId)
      const color = new THREE.Color(CHANNEL_MAP[channelId]?.color || '#00d4ff')
      if (node) {
        spawnPulse(node.group.position, color)
        node.pulse = 1
      }
      if (channel) {
        channel.pulse = 1
        if (node) spawnBeam(channel.base, node.group.position.clone().setY(4.1), color)
      }
    },

    focus (assetId) {
      const node = assetNodes.get(assetId)
      if (node) flyTo(node.group.position, 20, 13)
      else flyTo(new THREE.Vector3(0, 0, 0), 60, 40)
    },

    setView (mode) {
      if (mode === 'top') {
        flight = {
          t: 0,
          fromTarget: controls.target.clone(),
          toTarget: new THREE.Vector3(0, 0, 0),
          fromCam: camera.position.clone(),
          toCam: new THREE.Vector3(0.01, 96, 0.01)
        }
      } else if (mode === 'tactical') {
        flight = {
          t: 0,
          fromTarget: controls.target.clone(),
          toTarget: new THREE.Vector3(0, 4, 0),
          fromCam: camera.position.clone(),
          toCam: new THREE.Vector3(28, 11, 30)
        }
      } else {
        flight = {
          t: 0,
          fromTarget: controls.target.clone(),
          toTarget: new THREE.Vector3(0, 2, 0),
          fromCam: camera.position.clone(),
          toCam: new THREE.Vector3(0, 42, 62)
        }
      }
    },

    setGeofence (enabled) {
      for (const [id, node] of assetNodes) {
        node.fence.visible = enabled && (selection.size === 0 || selection.has(id))
      }
    },

    setHeatmap (enabled, detections) {
      heatGroup.visible = enabled
      heatGroup.clear()
      if (!enabled) return
      const material = new THREE.MeshBasicMaterial({
        color: '#ff7043', transparent: true, opacity: 0.16,
        blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide
      })
      const geometry = new THREE.CircleGeometry(2.6, 20)
      for (const d of detections.slice(0, 160)) {
        if (d.lat == null || d.lon == null) continue
        const { x, z } = projector.toScene(d.lat, d.lon)
        const disc = new THREE.Mesh(geometry, material)
        disc.rotation.x = -Math.PI / 2
        disc.position.set(x, 0.08, z)
        heatGroup.add(disc)
      }
    },

    runSweep () {
      sweep.visible = true
      sweepTime = 0
    },

    resize () {
      const w = container.clientWidth
      const h = container.clientHeight
      if (!w || !h) return
      camera.aspect = w / h
      camera.updateProjectionMatrix()
      renderer.setSize(w, h)
      composer.setSize(w, h)
      bloom.setSize(w, h)
    },

    /** Frame-Update; wird vom Renderloop in main.js aufgerufen. */
    update () {
      const dt = Math.min(clock.getDelta(), 0.05)
      const t = clock.elapsedTime

      // Agent-Kern
      if (agentRunning) {
        coreShell.rotation.y += dt * 0.35
        coreShell.rotation.x += dt * 0.12
        coreInner.rotation.y -= dt * 0.5
        const breathe = 1 + Math.sin(t * 1.6) * 0.05
        coreInner.scale.setScalar(breathe)
        coreHalo.scale.setScalar(1 + Math.sin(t * 1.1) * 0.06)
        orbitRings.forEach((ring, i) => { ring.rotation.z += dt * (0.28 + i * 0.16) })
      }

      // Kanal-Pylone
      for (const [, node] of channelNodes) {
        node.pulse = Math.max(0, node.pulse - dt * 1.6)
        node.head.scale.setScalar(1 + node.pulse * 0.7)
        node.head.rotation.y += dt * 0.6
        node.disc.material.opacity = 0.18 + node.pulse * 0.5
        node.disc.scale.setScalar(1 + node.pulse * 0.6)
      }

      // Assets
      for (const [id, node] of assetNodes) {
        node.group.position.lerp(node.target, 1 - Math.pow(0.001, dt))
        node.crystal.rotation.y += dt * 0.8
        node.pulse = Math.max(0, node.pulse - dt * 1.4)
        const bob = Math.sin(t * 1.5 + node.group.position.x) * 0.12
        node.crystal.position.y = 4.1 + bob
        node.glow.position.y = 4.1 + bob
        node.glow.scale.setScalar(1 + node.pulse * 0.5)
        node.base.scale.setScalar(1 + node.pulse * 0.35)
        node.beam.material.opacity = 0.1 + node.pulse * 0.28
        if (node.reticle.visible) node.reticle.rotation.z += dt * 0.9
        node.battFill.position.y = 5.6 + bob
        node.battTrack.position.y = 5.6 + bob
      }

      // Effekte
      for (let i = pulses.length - 1; i >= 0; i--) {
        const p = pulses[i]
        p.life += dt
        const k = p.life / p.max
        p.mesh.scale.setScalar(1 + k * 9)
        p.mesh.material.opacity = 0.85 * (1 - k)
        if (k >= 1) { effectGroup.remove(p.mesh); p.mesh.geometry.dispose(); p.mesh.material.dispose(); pulses.splice(i, 1) }
      }
      for (let i = beams.length - 1; i >= 0; i--) {
        const b = beams[i]
        b.life += dt
        const k = b.life / b.max
        b.mesh.material.opacity = 0.85 * (1 - k) * (k < 0.15 ? k / 0.15 : 1)
        if (k >= 1) { effectGroup.remove(b.mesh); b.mesh.geometry.dispose(); b.mesh.material.dispose(); beams.splice(i, 1) }
      }

      // Radar-Sweep
      if (sweep.visible) {
        sweepTime += dt
        sweep.rotation.z -= dt * 3.4
        sweep.material.opacity = 0.16 * Math.max(0, 1 - sweepTime / 3.2)
        if (sweepTime > 3.2) sweep.visible = false
      }

      // Kamera-Flug
      if (flight) {
        flight.t = Math.min(1, flight.t + dt * 1.5)
        const e = easeInOut(flight.t)
        controls.target.lerpVectors(flight.fromTarget, flight.toTarget, e)
        camera.position.lerpVectors(flight.fromCam, flight.toCam, e)
        if (flight.t >= 1) flight = null
      }

      controls.update()
      composer.render()
      updateLabels()
    },

    dispose () {
      renderer.dispose()
      container.innerHTML = ''
      labelLayer.innerHTML = ''
    }
  }

  /* ---------------- Labels projizieren ---------------- */

  const projected = new THREE.Vector3()

  function updateLabels () {
    const w = container.clientWidth
    const h = container.clientHeight
    for (const [, node] of assetNodes) {
      projected.copy(node.group.position)
      projected.y += 6.6
      projected.project(camera)
      const visible = projected.z < 1
      if (!visible) { node.label.style.opacity = '0'; continue }
      const x = (projected.x * 0.5 + 0.5) * w
      const y = (-projected.y * 0.5 + 0.5) * h
      node.label.style.transform = `translate(-50%, -50%) translate(${x.toFixed(1)}px, ${y.toFixed(1)}px)`
      const dist = camera.position.distanceTo(node.group.position)
      node.label.style.opacity = dist > 150 ? '0' : dist > 100 ? '0.45' : '1'
    }
  }

  world.resize()
  return world
}

/* ------------------------------------------------------------------ */
/* Hilfsfunktionen                                                     */
/* ------------------------------------------------------------------ */

function easeInOut (t) {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2
}

function escapeHtml (value) {
  return String(value).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ))
}

function makeRadialTexture () {
  const size = 1024
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = size
  const ctx = canvas.getContext('2d')

  const gradient = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2)
  gradient.addColorStop(0, 'rgba(20, 62, 100, 0.95)')
  gradient.addColorStop(0.45, 'rgba(11, 34, 56, 0.8)')
  gradient.addColorStop(1, 'rgba(5, 13, 24, 0)')
  ctx.fillStyle = gradient
  ctx.fillRect(0, 0, size, size)

  // dezente Straßenzüge für urbanen Kontext
  ctx.strokeStyle = 'rgba(90, 170, 220, 0.10)'
  ctx.lineWidth = 2
  for (let i = 0; i < 26; i++) {
    ctx.beginPath()
    const a = (i / 26) * Math.PI * 2
    ctx.moveTo(size / 2, size / 2)
    ctx.lineTo(size / 2 + Math.cos(a) * size / 2, size / 2 + Math.sin(a) * size / 2)
    ctx.stroke()
  }

  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  return texture
}

function makeStarfield () {
  const count = 900
  const positions = new Float32Array(count * 3)
  for (let i = 0; i < count; i++) {
    const r = 180 + Math.random() * 220
    const theta = Math.random() * TAU
    const phi = Math.acos(Math.random() * 0.7 + 0.1)
    positions[i * 3] = r * Math.sin(phi) * Math.cos(theta)
    positions[i * 3 + 1] = Math.abs(r * Math.cos(phi)) * 0.6
    positions[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta)
  }
  const geometry = new THREE.BufferGeometry()
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3))
  return new THREE.Points(geometry, new THREE.PointsMaterial({
    color: '#6fb6e8', size: 0.9, sizeAttenuation: true, transparent: true, opacity: 0.5, depthWrite: false
  }))
}
