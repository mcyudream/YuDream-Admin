<script setup lang="ts">
import { useYdMotion, YdAction, YdAnimatedBeam, YdAnimatedCountTo, YdAnimatedCountToGroup, YdBlurReveal, YdButton, YdCountTo, YdFlipWords, YdIcon, YdPatternBg, YdRadioGroup, YdSelect, YdSparklesText, YdSpotlightCard, YdSwitch, YdTag, YdTextHighlight, YdTextShiny } from '@yudream/components'
import { computed, onScopeDispose, ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const props = defineProps<{ showcaseGroup?: string }>()
const showIllustrations = computed(() => props.showcaseGroup !== 'Motion')
const showMotion = computed(() => props.showcaseGroup !== 'Illustrations')

const { reduced } = useYdMotion()
const systemReduced = typeof window !== 'undefined'
  && window.matchMedia('(prefers-reduced-motion: reduce)').matches
/** 雅致/紧凑的减少偏好只柔化节奏；系统级减少偏好额外放平指针驱动的装饰效果。 */
const softenMotion = computed(() => reduced.value || systemReduced)

/* ---------------- 云海晨昏：原创 CSS 天空场景 ---------------- */

const skyMood = ref('noon')
const skyMoodOptions = [
  { label: '破晓', value: 'dawn' },
  { label: '晴午', value: 'noon' },
  { label: '暮野', value: 'dusk' },
]
const skyStatusText: Record<string, string> = {
  dawn: '破晓：光从山脊爬上来，云层被慢慢点亮。',
  noon: '晴午：日头高悬，云影在山坡上分明。',
  dusk: '暮野：星子浮出来，弯月前来接班。',
}
const skyStatus = computed(() => skyStatusText[skyMood.value] ?? '')

const cloudBank = [
  { top: '18%', scale: 1.05, delay: 0 },
  { top: '40%', scale: 0.7, delay: -26 },
  { top: '58%', scale: 0.92, delay: -52 },
  { top: '30%', scale: 0.58, delay: -78 },
  { top: '50%', scale: 1.2, delay: -14 },
  { top: '66%', scale: 0.8, delay: -40 },
]
const cloudDensity = ref([3])
const windForce = ref([45])
const visibleClouds = computed(() => cloudBank.slice(0, cloudDensity.value[0] ?? 0))
const cloudDriftSeconds = computed(() => Math.max(14, 120 - (windForce.value[0] ?? 0)))

const skyPointer = ref({ x: 0, y: 0 })
const skyStyle = computed(() => ({
  '--vg-mx': skyPointer.value.x.toFixed(3),
  '--vg-my': skyPointer.value.y.toFixed(3),
  '--vg-cloud-drift': `${cloudDriftSeconds.value}s`,
}))
function onSkyPointerMove(event: PointerEvent) {
  if (softenMotion.value || event.pointerType !== 'mouse') {
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  skyPointer.value = {
    x: ((event.clientX - rect.left) / rect.width) * 2 - 1,
    y: ((event.clientY - rect.top) / rect.height) * 2 - 1,
  }
}
function onSkyPointerLeave() {
  skyPointer.value = { x: 0, y: 0 }
}

const skyStars = Array.from({ length: 12 }, (_, index) => ({
  left: `${(index * 47 + 8) % 94}%`,
  top: `${(index * 23 + 6) % 44}%`,
  size: index % 3 === 0 ? 3 : 2,
  delay: `${index * -0.7}s`,
}))

/* ---------------- 插画工坊：23 个命名条目（特征对齐参考目录） ---------------- */

const tileGroup = ref('all')
const tileGroupOptions = [
  { label: '全部条目', value: 'all' },
  { label: '卡片插画', value: 'cards' },
  { label: '产品场景', value: 'scenes' },
  { label: '地图区域', value: 'maps' },
]
const tileGroupSizes: Record<string, number> = { cards: 16, scenes: 5, maps: 2 }
const visibleTileCount = computed(() => tileGroup.value === 'all' ? 23 : (tileGroupSizes[tileGroup.value] ?? 0))
function tileGroupVisible(group: string) {
  return tileGroup.value === 'all' || tileGroup.value === group
}

const pressedTiles = ref<Record<string, boolean>>({})
function isOn(key: string) {
  return pressedTiles.value[key] === true
}
function toggleTile(key: string) {
  pressedTiles.value[key] = !isOn(key)
}
function tileHint(key: string, idle: string, active: string) {
  return isOn(key) ? active : idle
}

/* 区域地图：点击在高亮块之间轮换（参考条目：可切换区域） */
const regionIndex = ref(0)
function cycleRegion() {
  regionIndex.value = (regionIndex.value + 1) % 4
}

/* 大陆地图：六块抽象大陆轮换高亮 */
const worldIndex = ref(0)
function cycleWorld() {
  worldIndex.value = (worldIndex.value + 1) % 6
}

/* 层叠选层：点击抬起下一层 */
const layerIndex = ref(0)
function cycleLayer() {
  layerIndex.value = (layerIndex.value + 1) % 3
}

/* 型号转轮：点击滚动到下一个型号（原创命名，不指代真实产品） */
const wheelModels = ['轻舟 2.0', '流萤 mini', '望舒 max', '北落 1.5']
const wheelIndex = ref(0)
function spinWheel() {
  wheelIndex.value = (wheelIndex.value + 1) % wheelModels.length
}

/* 窗口叠层：点击轮换置顶窗口（悬停也可置顶） */
const tabFocus = ref(0)
function cycleTab() {
  tabFocus.value = (tabFocus.value + 1) % 3
}

/* 声纹圆球：呼吸 → 思考 → 发声 三态轮换 */
const orbStates = ['呼吸中', '思考中', '发声中']
const orbState = ref(0)
function cycleOrb() {
  orbState.value = (orbState.value + 1) % orbStates.length
}
const orbDots = Array.from({ length: 12 }, (_, index) => {
  const angle = (index / 12) * Math.PI * 2
  return {
    left: `${50 + Math.cos(angle) * 32}%`,
    top: `${50 + Math.sin(angle) * 32}%`,
    delay: `${index * -0.18}s`,
  }
})

/* 两朵有眼睛的云：瞳孔跟随指针（参考条目的跟随/眨眼特征） */
const cloudEyes = ref({
  frost: { x: 0, y: 0 },
  glow: { x: 0, y: 0 },
})
function onCloudPointerMove(event: PointerEvent, key: 'frost' | 'glow') {
  if (softenMotion.value || event.pointerType !== 'mouse') {
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  cloudEyes.value[key] = {
    x: ((event.clientX - rect.left) / rect.width) * 2 - 1,
    y: ((event.clientY - rect.top) / rect.height) * 2 - 1,
  }
}
function cloudEyeStyle(key: 'frost' | 'glow') {
  const eye = cloudEyes.value[key]
  return {
    '--vg-eye-x': eye.x.toFixed(2),
    '--vg-eye-y': eye.y.toFixed(2),
  }
}

/* 集成光束条目里的小型光束舞台（YdAnimatedBeam 承载，暂停即卸载） */
const tileBeamStage = ref<HTMLElement>()
const tileBeamLeft = ref<HTMLElement>()
const tileBeamCore = ref<HTMLElement>()
const tileBeamRight = ref<HTMLElement>()

/* ---------------- 动效实验室：8 个命名条目（特征对齐参考目录） ---------------- */

/* Number Ticker */
const ticker = ref(1286)

/* Text Reveal */
const revealKey = ref(0)

/* Gradient Text */
const gradientTextOn = ref(true)
const highlightKey = ref(0)

/* Grid Background */
const gridDrift = ref(true)
const gridSpeed = ref([10000])

/* Shimmer Loader */
const skeletonOn = ref(true)
const skeletonKey = ref(0)

/* Gradient Floor */
const floorDrift = ref(true)
const floorGlow = ref([64])

/* Tilt Card */
const tiltState = ref({ x: 0, y: 0 })
const tiltCardStyle = computed(() => ({
  'transform': `perspective(760px) rotateX(${(-tiltState.value.y * 7).toFixed(2)}deg) rotateY(${(tiltState.value.x * 9).toFixed(2)}deg)`,
  '--vg-ball-x': `${(tiltState.value.x * 46).toFixed(1)}px`,
  '--vg-ball-y': `${(tiltState.value.y * 30).toFixed(1)}px`,
}))
function onTiltPointerMove(event: PointerEvent) {
  if (softenMotion.value || event.pointerType !== 'mouse') {
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  tiltState.value = {
    x: ((event.clientX - rect.left) / rect.width) * 2 - 1,
    y: ((event.clientY - rect.top) / rect.height) * 2 - 1,
  }
}
function resetTilt() {
  tiltState.value = { x: 0, y: 0 }
}

/* Blur Crossfade：切换时模糊淡入淡出，容器高度滑向下一景的尺寸 */
const sceneDeck = [
  { key: 'tide', label: '潮汐', height: 180 },
  { key: 'aurora', label: '极光', height: 224 },
  { key: 'dune', label: '沙丘', height: 200 },
]
const sceneIndex = ref(0)
const sceneAutoplay = ref(false)
const sceneLabel = computed(() => sceneDeck[sceneIndex.value].label)
const deckHeight = computed(() => `${sceneDeck[sceneIndex.value].height}px`)
function stepScene(delta: number) {
  sceneIndex.value = (sceneIndex.value + delta + sceneDeck.length) % sceneDeck.length
}
const scenePeriod = computed(() => softenMotion.value ? 7200 : 4200)
let sceneTimer: ReturnType<typeof setInterval> | undefined
watch(sceneAutoplay, (playing) => {
  if (sceneTimer) {
    clearInterval(sceneTimer)
    sceneTimer = undefined
  }
  if (playing) {
    sceneTimer = setInterval(stepScene, scenePeriod.value, 1)
  }
})
onScopeDispose(() => {
  if (sceneTimer) {
    clearInterval(sceneTimer)
  }
})
</script>

<template>
  <div class="visual-gallery">
    <!-- 云海晨昏：区域选择 + 云量/风速拖拽 + 指针视差 + 昼暮模糊交叠 -->
    <section v-if="showIllustrations" class="sky-hero" aria-label="云海晨昏交互场景">
      <header class="sky-head">
        <p class="sky-eyebrow">
          YUDREAM / 视觉画廊
        </p>
        <h2>云海晨昏</h2>
        <p class="sky-sub">
          一片原创 CSS 天空的三种时刻：切换场景区域、拖动云量与风速，把指针移进画面推动云层。所有颜色来自主题语义变量。
        </p>
      </header>
      <div
        class="sky-stage"
        :class="`mood-${skyMood}`"
        :style="skyStyle"
        @pointermove="onSkyPointerMove"
        @pointerleave="onSkyPointerLeave"
      >
        <span class="sky-sky sky-dawn" aria-hidden="true" />
        <span class="sky-sky sky-noon" aria-hidden="true" />
        <span class="sky-sky sky-dusk" aria-hidden="true" />
        <div class="sky-layer sky-orbit" aria-hidden="true">
          <span class="sky-sun" />
          <span class="sky-moon" />
          <span
            v-for="(star, index) in skyStars"
            :key="index"
            class="sky-star"
            :style="{ left: star.left, top: star.top, width: `${star.size}px`, height: `${star.size}px`, animationDelay: star.delay }"
          />
        </div>
        <div class="sky-layer sky-clouds" aria-hidden="true">
          <span
            v-for="(cloud, index) in visibleClouds"
            :key="index"
            class="sky-cloud"
            :style="{ 'top': cloud.top, 'animationDelay': `${cloud.delay}s`, '--vg-cloud-scale': cloud.scale }"
          />
        </div>
        <div class="sky-layer sky-hills" aria-hidden="true">
          <span class="sky-hill sky-hill-a" />
          <span class="sky-hill sky-hill-b" />
        </div>
        <div class="sky-hud">
          <div class="hud-block">
            <span class="hud-label">场景区域</span>
            <YdRadioGroup v-model="skyMood" :options="skyMoodOptions" aria-label="选择场景区域" />
          </div>
          <div class="hud-block">
            <span class="hud-label">云量 {{ cloudDensity[0] }}</span>
            <YdSlider v-model="cloudDensity" :min="0" :max="6" :step="1" />
          </div>
          <div class="hud-block">
            <span class="hud-label">风速 {{ windForce[0] }}</span>
            <YdSlider v-model="windForce" :min="0" :max="100" :step="5" />
          </div>
          <output class="hud-status" aria-live="polite">{{ skyStatus }}</output>
        </div>
      </div>
    </section>

    <!-- 插画工坊：23 个命名条目 -->
    <DemoCard v-if="showIllustrations" kind="插画" title="插画工坊 · 23 个命名条目" description="每个条目对应参考目录的一件作品：以 YD 语义变量原创重绘，保留各自的交互特征，不复制原作画面。悬停或点击体验。">
      <div class="tile-toolbar">
        <YdSelect v-model="tileGroup" :options="tileGroupOptions" aria-label="筛选插画分组" class="tile-filter" />
        <span class="tile-count" aria-live="polite">共 23 个命名条目 · 当前显示 {{ visibleTileCount }} 个</span>
      </div>

      <div v-show="tileGroupVisible('cards')" class="tile-block">
        <p class="tile-block-label">
          卡片插画（16）
        </p>
        <div class="tile-grid">
          <YdAction class="tile" :aria-pressed="isOn('agent-team')" @click="toggleTile('agent-team')">
            <span class="tile-scene scene-team" :class="{ 'is-on': isOn('agent-team') }" aria-hidden="true">
              <span class="team-prompt" />
              <span class="team-line team-line-a" />
              <span class="team-line team-line-b" />
              <span class="team-line team-line-c" />
              <span class="team-dot team-dot-a" />
              <span class="team-dot team-dot-b" />
              <span class="team-dot team-dot-c" />
            </span>
            <span class="tile-name">智能体小队</span>
            <span class="tile-ref">Agent Team</span>
            <span class="tile-hint">{{ tileHint('agent-team', '点击：输入指令并分派', '已分派 · 再点重看') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('empty-calendar')" @click="toggleTile('empty-calendar')">
            <span class="tile-scene scene-calendar" :class="{ 'is-on': isOn('empty-calendar') }" aria-hidden="true">
              <span class="cal-card cal-card-a" />
              <span class="cal-card cal-card-b" />
              <span class="cal-card cal-card-c" />
            </span>
            <span class="tile-name">空日历堆</span>
            <span class="tile-ref">Empty Calendar</span>
            <span class="tile-hint">{{ tileHint('empty-calendar', '悬停展开卡片', '已展开 · 点击收拢') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('enrichment-orbit')" @click="toggleTile('enrichment-orbit')">
            <span class="tile-scene scene-orbit" :class="{ 'is-on': isOn('enrichment-orbit') }" aria-hidden="true">
              <span class="orbit-ring orbit-ring-outer"><span class="orbit-node" /></span>
              <span class="orbit-ring orbit-ring-inner"><span class="orbit-node" /></span>
              <span class="orbit-core" />
            </span>
            <span class="tile-name">数据轨道</span>
            <span class="tile-ref">Enrichment Orbit</span>
            <span class="tile-hint">{{ tileHint('enrichment-orbit', '点击暂停轨道', '已暂停 · 点击继续') }}</span>
          </YdAction>
          <YdAction
            class="tile"
            :aria-pressed="isOn('frost-cloud')"
            :style="cloudEyeStyle('frost')"
            @pointermove="onCloudPointerMove($event, 'frost')"
            @pointerleave="cloudEyes.frost = { x: 0, y: 0 }"
            @click="toggleTile('frost-cloud')"
          >
            <span class="tile-scene scene-cloudy scene-frost" :class="{ 'is-on': isOn('frost-cloud') }" aria-hidden="true">
              <span class="frost-sky" />
              <span class="puff-cloud">
                <span class="cloud-eye cloud-eye-l" />
                <span class="cloud-eye cloud-eye-r" />
              </span>
            </span>
            <span class="tile-name">霜白云</span>
            <span class="tile-ref">Frost Cloud</span>
            <span class="tile-hint">{{ tileHint('frost-cloud', '指针跟随 · 点击弹跳眨眼', '弹跳眨眼中') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('ghosted-chat')" @click="toggleTile('ghosted-chat')">
            <span class="tile-scene scene-ghost" :class="{ 'is-on': isOn('ghosted-chat') }" aria-hidden="true">
              <span class="ghost-bubble ghost-bubble-a"><span class="ghost-checks">✓✓</span></span>
              <span class="ghost-bubble ghost-bubble-b"><span class="ghost-checks">✓✓</span></span>
              <span class="ghost-thumb"><YdIcon name="morph:thumbs-down" /></span>
            </span>
            <span class="tile-name">已读不回</span>
            <span class="tile-ref">Ghosted Chat</span>
            <span class="tile-hint">{{ tileHint('ghosted-chat', '悬停出现差评回复', '差评已固定') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('glass-folder')" @click="toggleTile('glass-folder')">
            <span class="tile-scene scene-folder" :class="{ 'is-on': isOn('glass-folder') }" aria-hidden="true">
              <span class="folder-back" />
              <span class="folder-paper" />
              <span class="folder-lid" />
            </span>
            <span class="tile-name">玻璃文件夹</span>
            <span class="tile-ref">Glass Folder</span>
            <span class="tile-hint">{{ tileHint('glass-folder', '悬停掀开袋口', '已锁定打开') }}</span>
          </YdAction>
          <YdAction
            class="tile"
            :aria-pressed="isOn('glow-cloud')"
            :style="cloudEyeStyle('glow')"
            @pointermove="onCloudPointerMove($event, 'glow')"
            @pointerleave="cloudEyes.glow = { x: 0, y: 0 }"
            @click="toggleTile('glow-cloud')"
          >
            <span class="tile-scene scene-cloudy scene-glow" :class="{ 'is-on': isOn('glow-cloud') }" aria-hidden="true">
              <span class="puff-cloud">
                <span class="cloud-eye cloud-eye-l" />
                <span class="cloud-eye cloud-eye-r" />
              </span>
            </span>
            <span class="tile-name">辉光云</span>
            <span class="tile-ref">Glow Cloud</span>
            <span class="tile-hint">{{ tileHint('glow-cloud', '指针跟随 · 点击弹跳眨眼', '弹跳眨眼中') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('integration-grid')" @click="toggleTile('integration-grid')">
            <span class="tile-scene scene-igrid" :class="{ 'is-on': isOn('integration-grid') }" aria-hidden="true">
              <span class="igrid-cell igrid-1" />
              <span class="igrid-cell igrid-2" />
              <span class="igrid-cell igrid-3" />
              <span class="igrid-cell igrid-c" />
              <span class="igrid-cell igrid-4" />
              <span class="igrid-cell igrid-5" />
              <span class="igrid-cell igrid-6" />
            </span>
            <span class="tile-name">集成蜂墙</span>
            <span class="tile-ref">Integration Grid</span>
            <span class="tile-hint">{{ tileHint('integration-grid', '点击：从中心点亮一圈', '已点亮蜂墙') }}</span>
          </YdAction>
          <YdAction class="tile" @click="cycleLayer()">
            <span class="tile-scene scene-lstack" aria-hidden="true">
              <span class="lstack">
                <span class="layer-plate" :class="{ 'is-active': layerIndex === 0 }" />
                <span class="layer-plate" :class="{ 'is-active': layerIndex === 1 }" />
                <span class="layer-plate" :class="{ 'is-active': layerIndex === 2 }" />
              </span>
              <span class="lstack-list">
                <span class="lstack-row" :class="{ 'is-active': layerIndex === 0 }" />
                <span class="lstack-row" :class="{ 'is-active': layerIndex === 1 }" />
                <span class="lstack-row" :class="{ 'is-active': layerIndex === 2 }" />
              </span>
            </span>
            <span class="tile-name">层叠选层</span>
            <span class="tile-ref">Layer Stack</span>
            <span class="tile-hint">点击抬起下一层 · 第 {{ layerIndex + 1 }} 层</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('lead-sourcing')" @click="toggleTile('lead-sourcing')">
            <span class="tile-scene scene-lead" :class="{ 'is-on': isOn('lead-sourcing') }" aria-hidden="true">
              <span class="lead-node lead-node-a" />
              <span class="lead-line" />
              <span class="lead-node lead-node-b" />
            </span>
            <span class="tile-name">线索连线</span>
            <span class="tile-ref">Lead Sourcing</span>
            <span class="tile-hint">{{ tileHint('lead-sourcing', '点击反向流光', '已反向') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('logo-cluster')" @click="toggleTile('logo-cluster')">
            <span class="tile-scene scene-cluster" :class="{ 'is-on': isOn('logo-cluster') }" aria-hidden="true">
              <span class="cluster-sat cluster-sat-1" />
              <span class="cluster-sat cluster-sat-2" />
              <span class="cluster-sat cluster-sat-3" />
              <span class="cluster-sat cluster-sat-4" />
              <span class="cluster-sat cluster-sat-5" />
              <span class="cluster-core" />
            </span>
            <span class="tile-name">标志星簇</span>
            <span class="tile-ref">Logo Cluster</span>
            <span class="tile-hint">{{ tileHint('logo-cluster', '点击：标志弹出归位', '已收拢') }}</span>
          </YdAction>
          <YdAction class="tile" @click="spinWheel()">
            <span class="tile-scene scene-wheel" aria-hidden="true">
              <span class="wheel-window">
                <span class="wheel-strip" :style="{ transform: `translateY(${wheelIndex * -26}px)` }">
                  <span v-for="model in wheelModels" :key="model" class="wheel-row">{{ model }}</span>
                </span>
              </span>
            </span>
            <span class="tile-name">型号转轮</span>
            <span class="tile-ref">Model Wheel</span>
            <span class="tile-hint">点击滚动到下一个型号</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('outreach-branches')" @click="toggleTile('outreach-branches')">
            <span class="tile-scene scene-obranch" :class="{ 'is-on': isOn('outreach-branches') }" aria-hidden="true">
              <span class="obranch-src" />
              <span class="obranch-line obranch-line-a" />
              <span class="obranch-line obranch-line-b" />
              <span class="obranch-line obranch-line-c" />
              <span class="obranch-dot obranch-dot-a" />
              <span class="obranch-dot obranch-dot-b" />
              <span class="obranch-dot obranch-dot-c" />
            </span>
            <span class="tile-name">外联分支</span>
            <span class="tile-ref">Outreach Branches</span>
            <span class="tile-hint">{{ tileHint('outreach-branches', '点击：沿分支发出脉冲', '脉冲已发出') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('side-folder')" @click="toggleTile('side-folder')">
            <span class="tile-scene scene-sfolder" :class="{ 'is-on': isOn('side-folder') }" aria-hidden="true">
              <span class="sfolder-pocket">
                <span class="sfolder-doc sfolder-doc-a" />
                <span class="sfolder-doc sfolder-doc-b" />
                <span class="sfolder-doc sfolder-doc-c" />
              </span>
              <span class="sfolder-lid" />
            </span>
            <span class="tile-name">侧滑文件夹</span>
            <span class="tile-ref">Side Folder</span>
            <span class="tile-hint">{{ tileHint('side-folder', '悬停滑出文档', '已锁定滑出') }}</span>
          </YdAction>
          <YdAction class="tile" @click="cycleTab()">
            <span class="tile-scene scene-tabwin" aria-hidden="true">
              <span v-for="win in 3" :key="win" class="tabwin" :class="[`tabwin-${win}`, { 'is-focus': tabFocus === win - 1 }]">
                <span class="tabwin-bar" />
              </span>
            </span>
            <span class="tile-name">窗口叠层</span>
            <span class="tile-ref">Tab Overload</span>
            <span class="tile-hint">悬停置顶窗口 · 点击轮换（第 {{ tabFocus + 1 }} 个）</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('team-invite')" @click="toggleTile('team-invite')">
            <span class="tile-scene scene-tinvite" :class="{ 'is-on': isOn('team-invite') }" aria-hidden="true">
              <span class="tinvite-avatar tinvite-avatar-a" />
              <span class="tinvite-avatar tinvite-avatar-b" />
              <span class="tinvite-slot"><span class="tinvite-ghost" /></span>
              <span class="tinvite-cursor" />
              <span class="tinvite-dot" />
            </span>
            <span class="tile-name">团队邀请</span>
            <span class="tile-ref">Team Invite</span>
            <span class="tile-hint">{{ tileHint('team-invite', '点击：拉人入列并上线', '已上线') }}</span>
          </YdAction>
        </div>
      </div>

      <div v-show="tileGroupVisible('scenes')" class="tile-block">
        <p class="tile-block-label">
          产品场景（5）
        </p>
        <div class="tile-grid">
          <YdAction class="tile" :aria-pressed="isOn('glow-keyboard')" @click="toggleTile('glow-keyboard')">
            <span class="tile-scene scene-kbd" :class="{ 'is-on': isOn('glow-keyboard') }" aria-hidden="true">
              <span class="kbd-type" />
              <span class="kbd-glass">
                <span v-for="key in 12" :key="key" class="kbd-key" :style="{ animationDelay: `${(key - 1) * 90}ms` }" />
              </span>
            </span>
            <span class="tile-name">辉光键盘</span>
            <span class="tile-ref">Glow Keyboard</span>
            <span class="tile-hint">{{ tileHint('glow-keyboard', '点击：自动输入并掀起键波', '键波已掀起') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('integration-beam')" @click="toggleTile('integration-beam')">
            <span ref="tileBeamStage" class="tile-scene scene-beamstage" aria-hidden="true">
              <YdAnimatedBeam
                v-if="!isOn('integration-beam')"
                :container-ref="tileBeamStage"
                :from-ref="tileBeamLeft"
                :to-ref="tileBeamCore"
                :curvature="14"
                :duration="2.6"
              />
              <YdAnimatedBeam
                v-if="!isOn('integration-beam')"
                :container-ref="tileBeamStage"
                :from-ref="tileBeamCore"
                :to-ref="tileBeamRight"
                :curvature="-14"
                :duration="2.6"
                :delay="500"
              />
              <span ref="tileBeamLeft" class="beam-tool beam-tool-left" />
              <span ref="tileBeamCore" class="beam-core" />
              <span ref="tileBeamRight" class="beam-tool beam-tool-right" />
            </span>
            <span class="tile-name">集成光束</span>
            <span class="tile-ref">Integration Beam</span>
            <span class="tile-hint">{{ tileHint('integration-beam', '悬停聚焦工具', '脉冲已暂停') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('integration-flow')" @click="toggleTile('integration-flow')">
            <span class="tile-scene scene-iflow" :class="{ 'is-on': isOn('integration-flow') }" aria-hidden="true">
              <span class="flow-ring">
                <span class="flow-pill flow-pill-a" />
                <span class="flow-pill flow-pill-b" />
                <span class="flow-pill flow-pill-c" />
              </span>
              <span class="flow-hub" />
            </span>
            <span class="tile-name">集成环流</span>
            <span class="tile-ref">Integration Flow</span>
            <span class="tile-hint">{{ tileHint('integration-flow', '点击暂停旋转', '已暂停 · 点击继续') }}</span>
          </YdAction>
          <YdAction class="tile" :aria-pressed="isOn('project-flow')" @click="toggleTile('project-flow')">
            <span class="tile-scene scene-pflow" :class="{ 'is-on': isOn('project-flow') }" aria-hidden="true">
              <span class="pflow-root" />
              <span class="pflow-link pflow-link-a" />
              <span class="pflow-link pflow-link-b" />
              <span class="pflow-sub pflow-sub-a" />
              <span class="pflow-sub pflow-sub-b" />
              <span class="pflow-note" />
            </span>
            <span class="tile-name">任务分派</span>
            <span class="tile-ref">Project Flow</span>
            <span class="tile-hint">{{ tileHint('project-flow', '点击展开子任务', '已展开 · 点击收起') }}</span>
          </YdAction>
          <YdAction class="tile" @click="cycleOrb()">
            <span class="tile-scene scene-orb" :class="`orb-state-${orbState}`" aria-hidden="true">
              <span
                v-for="(dot, index) in orbDots"
                :key="index"
                class="orb-dot"
                :style="{ left: dot.left, top: dot.top, animationDelay: dot.delay }"
              />
              <span class="orb-ring" />
              <span class="orb-ripple orb-ripple-a" />
              <span class="orb-ripple orb-ripple-b" />
            </span>
            <span class="tile-name">声纹圆球</span>
            <span class="tile-ref">Voice Orb</span>
            <span class="tile-hint">点击切换状态 · {{ orbStates[orbState] }}</span>
          </YdAction>
        </div>
      </div>

      <div v-show="tileGroupVisible('maps')" class="tile-block">
        <p class="tile-block-label">
          地图区域（2）
        </p>
        <div class="tile-grid">
          <YdAction class="tile" @click="cycleRegion()">
            <span class="tile-scene scene-region" aria-hidden="true">
              <span class="dach-blob dach-a" :class="{ 'is-active': regionIndex === 0 }" />
              <span class="dach-blob dach-b" :class="{ 'is-active': regionIndex === 1 }" />
              <span class="dach-blob dach-c" :class="{ 'is-active': regionIndex === 2 }" />
              <span class="dach-blob dach-d" :class="{ 'is-active': regionIndex === 3 }" />
            </span>
            <span class="tile-name">区域地图</span>
            <span class="tile-ref">DACH Region</span>
            <span class="tile-hint">点击切换高亮区域 · 第 {{ regionIndex + 1 }}/4 块</span>
          </YdAction>
          <YdAction class="tile" @click="cycleWorld()">
            <span class="tile-scene scene-wmap" aria-hidden="true">
              <span class="wmap-blob wmap-1" :class="{ 'is-active': worldIndex === 0 }" />
              <span class="wmap-blob wmap-2" :class="{ 'is-active': worldIndex === 1 }" />
              <span class="wmap-blob wmap-3" :class="{ 'is-active': worldIndex === 2 }" />
              <span class="wmap-blob wmap-4" :class="{ 'is-active': worldIndex === 3 }" />
              <span class="wmap-blob wmap-5" :class="{ 'is-active': worldIndex === 4 }" />
              <span class="wmap-blob wmap-6" :class="{ 'is-active': worldIndex === 5 }" />
            </span>
            <span class="tile-name">大陆地图</span>
            <span class="tile-ref">World Map Continental</span>
            <span class="tile-hint">点击切换高亮大陆 · 第 {{ worldIndex + 1 }}/6 块</span>
          </YdAction>
        </div>
      </div>
    </DemoCard>

    <!-- 动效实验室 1/8：Blur Crossfade -->
    <DemoCard v-if="showMotion" kind="动效" title="模糊交叉淡切" description="切换内容时旧景模糊淡出、新景模糊淡入，容器高度平滑滑向下一景的尺寸；可手动切换或自动轮换。">
      <p class="ref-name">
        参考条目 · Blur Crossfade
      </p>
      <div class="deck-stage" :style="{ height: deckHeight }">
        <Transition name="scene-fade" mode="out-in">
          <div :key="sceneIndex" class="deck-scene" :class="`deck-${sceneDeck[sceneIndex].key}`" aria-hidden="true">
            <span class="deck-band deck-band-a" />
            <span class="deck-band deck-band-b" />
            <span class="deck-band deck-band-c" />
          </div>
        </Transition>
        <YdTag class="deck-tag">
          {{ sceneLabel }}
        </YdTag>
      </div>
      <div class="control-row">
        <YdButton variant="outline" size="icon" aria-label="上一景" @click="stepScene(-1)">
          <YdIcon name="morph:arrow-left" />
        </YdButton>
        <YdButton variant="outline" size="icon" aria-label="下一景" @click="stepScene(1)">
          <YdIcon name="morph:arrow-right" />
        </YdButton>
        <YdSwitch v-model="sceneAutoplay" aria-label="自动轮换场景" />
        <span class="control-label">自动轮换</span>
      </div>
    </DemoCard>

    <!-- 动效实验室 2/8：Gradient Floor -->
    <DemoCard v-if="showMotion" kind="动效" title="渐变地面" description="模糊色晕从底边发光，两层各按自己的节奏漂移、缓慢变形；开关控制漂移，滑块控制光强。">
      <p class="ref-name">
        参考条目 · Gradient Floor
      </p>
      <div
        class="floor-stage"
        :class="{ 'is-paused': !floorDrift }"
        :style="{ '--vg-floor-glow': (floorGlow[0] / 100).toFixed(2) }"
      >
        <span class="floor-layer floor-layer-a" aria-hidden="true" />
        <span class="floor-layer floor-layer-b" aria-hidden="true" />
        <p class="floor-copy">
          光从底边渗出来，两层色晕各自漂移、缓慢变形。
        </p>
      </div>
      <div class="control-grid">
        <div class="control-block">
          <span class="hud-label">光强 {{ floorGlow[0] }}</span>
          <YdSlider v-model="floorGlow" :min="20" :max="100" :step="5" />
        </div>
        <div class="control-row">
          <YdSwitch v-model="floorDrift" aria-label="色晕漂移" />
          <span class="control-label">漂移</span>
        </div>
      </div>
    </DemoCard>

    <!-- 动效实验室 3/8：Gradient Text -->
    <DemoCard v-if="showMotion" kind="动效" title="渐变文字" description="字面从流动的渐变里裁出来，颜色在字面上持续扫过；扫亮动效在首次可见时走一遍，可重播或切回静态。">
      <p class="ref-name">
        参考条目 · Gradient Text
      </p>
      <div :key="highlightKey" class="grad-stage">
        <p class="grad-word">
          <YdTextShiny v-if="gradientTextOn" :duration="3">
            让重点自己发光
          </YdTextShiny>
          <template v-else>
            让重点自己发光
          </template>
        </p>
        <p class="grad-sub">
          <YdTextHighlight :duration="1100">
            颜色第一次出现时会扫过整个词
          </YdTextHighlight>
        </p>
        <p class="grad-spark">
          <YdSparklesText text="点缀少量星芒，不抢正文" />
        </p>
      </div>
      <div class="control-row">
        <YdSwitch v-model="gradientTextOn" aria-label="切换流光标题" />
        <span class="control-label">流光 {{ gradientTextOn ? '开' : '关' }}</span>
        <YdButton variant="outline" size="sm" @click="highlightKey++">
          <YdIcon name="morph:rotate-ccw" /> 重播扫色
        </YdButton>
      </div>
    </DemoCard>

    <!-- 动效实验室 4/8：Grid Background -->
    <DemoCard v-if="showMotion" kind="动效" title="网格背景" description="细线网格在内容后面缓慢漂移，越靠边缘越淡；滑块调节漂移周期，开关切换漂移。">
      <p class="ref-name">
        参考条目 · Grid Background
      </p>
      <div class="gridbg-stage">
        <YdPatternBg variant="grid" size="md" :animate="gridDrift" :speed="gridSpeed[0]" class="gridbg-layer" />
        <div class="gridbg-content">
          <p>
            内容保持在网格之上，随时可读、可交互。
          </p>
        </div>
      </div>
      <div class="control-grid">
        <div class="control-block">
          <span class="hud-label">漂移周期 {{ gridSpeed[0] }}ms</span>
          <YdSlider v-model="gridSpeed" :min="4000" :max="20000" :step="1000" />
        </div>
        <div class="control-row">
          <YdSwitch v-model="gridDrift" aria-label="网格漂移" />
          <span class="control-label">漂移</span>
        </div>
      </div>
    </DemoCard>

    <!-- 动效实验室 5/8：Number Ticker -->
    <DemoCard v-if="showMotion" kind="动效" title="数字滚动" description="点击按钮推动指标，数字像里程表一样滚入位；数值本身始终准确可读。">
      <p class="ref-name">
        参考条目 · Number Ticker
      </p>
      <div class="ticker-line">
        <YdAnimatedCountToGroup class="ticker-value">
          <YdAnimatedCountTo :value="ticker" />
          <span class="ticker-sep">/</span>
          <YdCountTo :end-val="ticker * 3" />
        </YdAnimatedCountToGroup>
        <span class="yd-muted">当前值 {{ ticker }}（×3 = {{ ticker * 3 }}）</span>
      </div>
      <div class="control-row">
        <YdButton @click="ticker += 137">
          <YdIcon name="morph:plus" /> 增加流量
        </YdButton>
        <YdButton variant="outline" @click="ticker = 1286">
          <YdIcon name="morph:rotate-ccw" /> 复位
        </YdButton>
      </div>
    </DemoCard>

    <!-- 动效实验室 6/8：Shimmer Loader -->
    <DemoCard v-if="showMotion" kind="动效" title="微光加载" description="骨架块共享同一道光扫过，搭配微光文字表达等待；可重播，也可暂停对比静态。">
      <p class="ref-name">
        参考条目 · Shimmer Loader
      </p>
      <div :key="skeletonKey" class="sk-wrap" :class="{ 'is-paused': !skeletonOn }">
        <div class="skeleton-group" aria-hidden="true">
          <span class="sk-avatar" />
          <span class="sk-column">
            <span class="sk-line sk-line-a" />
            <span class="sk-line sk-line-b" />
          </span>
          <span class="sk-block" />
        </div>
        <p class="sk-label">
          <YdTextShiny v-if="skeletonOn" :duration="2.4">
            正在思考…
          </YdTextShiny>
          <template v-else>
            正在思考…
          </template>
        </p>
      </div>
      <div class="control-row">
        <YdSwitch v-model="skeletonOn" aria-label="播放骨架扫光" />
        <span class="control-label">扫光 {{ skeletonOn ? '播放中' : '已暂停' }}</span>
        <YdButton variant="outline" size="sm" @click="skeletonKey++">
          <YdIcon name="morph:rotate-ccw" /> 重播
        </YdButton>
      </div>
    </DemoCard>

    <!-- 动效实验室 7/8：Text Reveal -->
    <DemoCard v-if="showMotion" kind="动效" title="文字揭示" description="词组从轻微模糊里浮出，关键词扫亮，词语轮换；点击重播入场。">
      <p class="ref-name">
        参考条目 · Text Reveal
      </p>
      <YdBlurReveal :key="revealKey" :duration="0.5" :delay="0.05">
        <p class="reveal-eyebrow">
          视觉画廊 · 揭示动效
        </p>
        <h3 class="reveal-title">
          让 <YdFlipWords :words="['云层', '光束', '数据']" /> 慢慢浮现
        </h3>
        <p class="reveal-copy">
          <YdTextHighlight>悬停、点击、拖拽，都是画面的一部分。</YdTextHighlight>
        </p>
      </YdBlurReveal>
      <div class="control-row">
        <YdButton variant="outline" @click="revealKey++">
          <YdIcon name="morph:rotate-ccw" /> 重播入场
        </YdButton>
      </div>
    </DemoCard>

    <!-- 动效实验室 8/8：Tilt Card -->
    <DemoCard v-if="showMotion" kind="动效" title="倾斜卡片" description="卡片朝指针方向轻微倾斜，光斑跟随，小球随重力滚动；松开后弹回。">
      <p class="ref-name">
        参考条目 · Tilt Card
      </p>
      <div class="tilt-stage" @pointermove="onTiltPointerMove" @pointerleave="resetTilt">
        <YdSpotlightCard class="tilt-card" :style="tiltCardStyle">
          <div class="tilt-field" aria-hidden="true">
            <span class="tilt-ball" />
          </div>
          <h3 class="tilt-title">
            重力小球
          </h3>
          <p class="tilt-copy">
            指针就是重力方向；减少动态偏好下卡片自动放平，只用聚光灯反馈位置。
          </p>
          <YdButton variant="outline" size="sm" @click="resetTilt">
            <YdIcon name="morph:locate-fixed" /> 回正
          </YdButton>
        </YdSpotlightCard>
      </div>
    </DemoCard>

    <p class="yd-muted preference-note">
      全部演示仅使用本地状态，为参考条目的原创重绘：三套外观下功能动画持续运行；减少动态效果只柔化装饰的节奏与幅度，不会冻结计数与揭示。
    </p>
  </div>
</template>

<style scoped>
.visual-gallery {
  --vg-crossfade-blur: 10px;

  display: grid;
  gap: var(--yd-workspace-gap);
}

/* ---------------- 云海晨昏 ---------------- */

.sky-hero {
  display: grid;
  gap: var(--yd-content-gap);
  padding: clamp(20px, 4vw, 44px);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius);
}

.sky-eyebrow {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-3);
  letter-spacing: 0.18em;
}

.sky-head h2 {
  margin: 10px 0 6px;
  font-size: clamp(1.7rem, 4vw, 2.6rem);
  letter-spacing: -0.03em;
}

.sky-sub {
  max-width: 62ch;
  margin: 0;
  line-height: 1.7;
  color: var(--color-text-3);
}

.sky-stage {
  position: relative;
  height: clamp(300px, 44vw, 420px);
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.sky-sky {
  position: absolute;
  inset: 0;
  transition: opacity var(--yd-motion-duration-slow) var(--yd-motion-ease), filter var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.sky-dawn {
  background: linear-gradient(180deg, var(--color-bg-2), color-mix(in srgb, var(--yd-emphasis) 22%, var(--color-bg-1)));
}

.sky-noon {
  background: linear-gradient(180deg, color-mix(in srgb, var(--color-fill-2) 55%, var(--color-bg-2)), var(--color-bg-1));
}

.sky-dusk {
  background: linear-gradient(180deg, color-mix(in srgb, var(--color-text-1) 30%, var(--color-bg-2)), color-mix(in srgb, var(--color-text-1) 12%, var(--color-bg-1)));
}

.mood-dawn .sky-dawn {
  opacity: 1;
}

.mood-dawn .sky-noon,
.mood-dawn .sky-dusk {
  opacity: 0;
  filter: blur(var(--vg-crossfade-blur));
}

.mood-noon .sky-noon {
  opacity: 1;
}

.mood-noon .sky-dawn,
.mood-noon .sky-dusk {
  opacity: 0;
  filter: blur(var(--vg-crossfade-blur));
}

.mood-dusk .sky-dusk {
  opacity: 1;
}

.mood-dusk .sky-dawn,
.mood-dusk .sky-noon {
  opacity: 0;
  filter: blur(var(--vg-crossfade-blur));
}

.sky-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.sky-orbit {
  transform: translate3d(calc(var(--vg-mx, 0) * 12px), calc(var(--vg-my, 0) * 8px), 0);
}

.sky-clouds {
  transform: translate3d(calc(var(--vg-mx, 0) * 8px), calc(var(--vg-my, 0) * 5px), 0);
}

.sky-hills {
  transform: translate3d(calc(var(--vg-mx, 0) * 4px), calc(var(--vg-my, 0) * 2px), 0);
}

.sky-sun {
  position: absolute;
  top: 12%;
  left: 50%;
  width: 76px;
  height: 76px;
  background: radial-gradient(circle, var(--yd-emphasis) 0 44%, color-mix(in srgb, var(--yd-emphasis) 30%, transparent) 70%, transparent 74%);
  border-radius: 50%;
  transition: opacity var(--yd-motion-duration-slow) var(--yd-motion-ease), top var(--yd-motion-duration-slow) var(--yd-motion-ease), left var(--yd-motion-duration-slow) var(--yd-motion-ease), transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.mood-dawn .sky-sun {
  top: 46%;
  left: 18%;
  transform: scale(1.18);
}

.mood-dusk .sky-sun {
  opacity: 0;
  transform: scale(0.6);
}

.sky-moon {
  position: absolute;
  top: 14%;
  right: 20%;
  width: 54px;
  height: 54px;
  border-radius: 50%;
  box-shadow: inset -14px 8px 0 0 color-mix(in srgb, var(--color-text-1) 68%, var(--color-bg-1));
  opacity: 0;
  transition: opacity var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.mood-dusk .sky-moon {
  opacity: 1;
}

.sky-star {
  position: absolute;
  background: color-mix(in srgb, var(--color-text-1) 80%, var(--color-bg-1));
  border-radius: 50%;
  opacity: 0;
  transition: opacity var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.mood-dusk .sky-star {
  opacity: 0.9;
  animation: vg-twinkle calc(2.4s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite alternate;
}

@keyframes vg-twinkle {
  from {
    opacity: 0.35;
  }

  to {
    opacity: 1;
  }
}

.sky-cloud {
  position: absolute;
  left: -26%;
  width: 96px;
  height: 28px;
  background: color-mix(in srgb, var(--color-bg-1) 88%, transparent);
  border-radius: 999px;
  box-shadow: 0 1px 0 color-mix(in srgb, var(--color-text-1) 8%, transparent);
  transform: scale(var(--vg-cloud-scale, 1));
  animation: vg-cloud-drift calc(var(--vg-cloud-drift, 75s) * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.sky-cloud::before,
.sky-cloud::after {
  position: absolute;
  top: -12px;
  width: 34px;
  height: 34px;
  content: "";
  background: inherit;
  border-radius: 50%;
}

.sky-cloud::before {
  left: 14px;
}

.sky-cloud::after {
  left: 44px;
}

@keyframes vg-cloud-drift {
  from {
    left: -26%;
  }

  to {
    left: 108%;
  }
}

.sky-hill {
  position: absolute;
  bottom: -6px;
  width: 62%;
  height: 44%;
  background: color-mix(in srgb, var(--color-text-1) 14%, transparent);
  clip-path: polygon(50% 0, 100% 100%, 0 100%);
}

.sky-hill-a {
  left: -8%;
}

.sky-hill-b {
  right: -10%;
  height: 32%;
  background: color-mix(in srgb, var(--color-text-1) 8%, transparent);
}

.sky-hud {
  position: absolute;
  right: 14px;
  bottom: 14px;
  left: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px 22px;
  align-items: center;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--color-bg-1) 86%, transparent);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
  backdrop-filter: blur(6px);
}

.hud-block {
  display: grid;
  gap: 6px;
  min-width: 150px;
}

.hud-label {
  font-size: 12px;
  color: var(--color-text-3);
}

.hud-status {
  flex: 1 1 200px;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-2);
}

/* ---------------- 插画工坊：条目网格 ---------------- */

.tile-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: center;
}

.tile-filter {
  min-width: 180px;
  max-width: 240px;
}

.tile-count {
  font-size: 12px;
  color: var(--color-text-3);
}

.tile-block {
  display: grid;
  gap: 12px;
}

.tile-block-label {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.tile-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 164px), 1fr));
  gap: var(--yd-content-gap);
}

.tile {
  display: grid;
  gap: 2px;
  padding: 10px;
  text-align: left;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
  transition: transform var(--yd-motion-duration-fast) var(--yd-motion-ease), box-shadow var(--yd-motion-duration-fast) var(--yd-motion-ease), border-color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.tile:hover {
  box-shadow: var(--yd-surface-shadow-hover);
  transform: translateY(var(--yd-motion-lift));
}

.tile[aria-pressed="true"] {
  border-color: color-mix(in srgb, var(--color-text-1) 45%, var(--color-border-2));
}

.tile-scene {
  position: relative;
  display: grid;
  place-items: center;
  height: 104px;
  margin-bottom: 8px;
  overflow: hidden;
  background: var(--color-fill-1);
  border-radius: calc(var(--yd-control-radius) - 4px);
}

.tile-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.tile-ref {
  font-size: 10px;
  color: var(--color-text-3);
  letter-spacing: 0.06em;
}

.tile-hint {
  font-size: 11.5px;
  color: var(--color-text-3);
}

/* 智能体小队 Agent Team：指令输入后分派三位智能体 */

.team-prompt {
  position: absolute;
  top: 18%;
  left: 50%;
  width: 16px;
  height: 9px;
  background: color-mix(in srgb, var(--color-text-1) 26%, var(--color-fill-2));
  border-radius: 5px;
  transform: translateX(-50%);
  transition: width var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.scene-team.is-on .team-prompt {
  width: 62%;
}

.team-line {
  position: absolute;
  top: 42%;
  left: 50%;
  width: 2px;
  height: 24px;
  background: color-mix(in srgb, var(--color-text-1) 24%, var(--color-border-2));
  transform: rotate(var(--vg-line-r)) scaleY(0);
  transform-origin: top center;
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.team-line-a {
  --vg-line-r: 26deg;
}

.team-line-b {
  --vg-line-r: 0deg;
}

.team-line-c {
  --vg-line-r: -26deg;
}

.scene-team.is-on .team-line {
  transform: rotate(var(--vg-line-r)) scaleY(1);
}

.team-dot {
  position: absolute;
  top: 72%;
  width: 12px;
  height: 12px;
  background: var(--color-bg-1);
  border: 2px solid color-mix(in srgb, var(--color-text-1) 30%, var(--color-border-2));
  border-radius: 50%;
  opacity: 0.4;
  transform: scale(0.6);
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), border-color var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.scene-team.is-on .team-dot {
  border-color: var(--yd-emphasis);
  opacity: 1;
  transform: scale(1);
}

.team-dot-a {
  left: 22%;
}

.team-dot-b {
  left: 50%;
  margin-left: -6px;
}

.team-dot-c {
  right: 22%;
}

.scene-team.is-on .team-dot-a {
  transition-delay: 180ms;
}

.scene-team.is-on .team-dot-b {
  transition-delay: 90ms;
}

.scene-team.is-on .team-dot-c {
  transition-delay: 270ms;
}

/* 空日历堆 Empty Calendar：卡片堆叠，悬停/点击展开 */

.cal-card {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 36px;
  height: 40px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  transform: translate(-50%, -50%);
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

.cal-card::before {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 9px;
  content: "";
  background: color-mix(in srgb, var(--color-text-1) 18%, var(--color-fill-2));
  border-radius: 5px 5px 0 0;
}

.cal-card::after {
  position: absolute;
  top: 16px;
  left: 6px;
  width: 22px;
  height: 14px;
  content: "";
  background: radial-gradient(circle, color-mix(in srgb, var(--color-text-1) 22%, transparent) 1.5px, transparent 2px) 0 0 / 8px 7px repeat;
}

.scene-calendar:hover .cal-card,
.scene-calendar.is-on .cal-card {
  transform: translate(-50%, -50%) translateY(-6px);
}

.scene-calendar:hover .cal-card-a,
.scene-calendar.is-on .cal-card-a {
  transform: translate(-50%, -50%) translate(-17px, 2px) rotate(-10deg);
}

.scene-calendar:hover .cal-card-c,
.scene-calendar.is-on .cal-card-c {
  transform: translate(-50%, -50%) translate(17px, 2px) rotate(10deg);
}

/* 数据轨道 Enrichment Orbit：中心磁贴 + 双环彗星环绕 */

.orbit-ring {
  position: absolute;
  border: 1px dashed color-mix(in srgb, var(--color-text-1) 30%, var(--color-border-2));
  border-radius: 50%;
  animation: vg-spin calc(9s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.orbit-ring-outer {
  width: 74px;
  height: 74px;
}

.orbit-ring-inner {
  width: 46px;
  height: 46px;
  animation-duration: calc(5.5s * var(--yd-motion-loop-scale, 1));
  animation-direction: reverse;
}

.scene-orbit.is-on .orbit-ring {
  animation-play-state: paused;
}

.orbit-node {
  position: absolute;
  top: -4px;
  left: 50%;
  width: 7px;
  height: 7px;
  margin-left: -3.5px;
  background: var(--yd-emphasis);
  border-radius: 50%;
}

.orbit-core {
  position: relative;
  width: 18px;
  height: 18px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
}

.orbit-core::after {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 4px;
  height: 4px;
  content: "";
  background: var(--yd-emphasis);
  border-radius: 50%;
}

@keyframes vg-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 有眼睛的云：霜白云 / 辉光云 */

.scene-frost {
  background: color-mix(in srgb, var(--yd-emphasis) 12%, var(--color-bg-2));
}

.scene-glow {
  background: color-mix(in srgb, var(--color-text-1) 16%, var(--color-bg-2));
}

.frost-sky {
  position: absolute;
  inset: 0;
  background: radial-gradient(90% 70% at 50% 30%, color-mix(in srgb, var(--color-bg-1) 40%, transparent), transparent 75%);
}

.puff-cloud {
  position: relative;
  width: 56px;
  height: 20px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.puff-cloud::before,
.puff-cloud::after {
  position: absolute;
  top: -10px;
  width: 20px;
  height: 20px;
  content: "";
  background: inherit;
  border: inherit;
  border-radius: 50%;
}

.puff-cloud::before {
  left: 8px;
}

.puff-cloud::after {
  left: 26px;
}

.scene-glow .puff-cloud {
  box-shadow: 0 0 16px color-mix(in srgb, var(--yd-emphasis) 45%, transparent);
}

.cloud-eye {
  position: absolute;
  top: 2px;
  width: 7px;
  height: 7px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
  animation: vg-blink calc(4.2s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite;
}

.cloud-eye::after {
  position: absolute;
  top: 1.5px;
  left: 1.5px;
  width: 3px;
  height: 3px;
  content: "";
  background: var(--color-text-1);
  border-radius: 50%;
  transform: translate(calc(var(--vg-eye-x, 0) * 1.6px), calc(var(--vg-eye-y, 0) * 1.2px));
  transition: transform var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.cloud-eye-l {
  left: 14px;
}

.cloud-eye-r {
  right: 14px;
  animation-delay: -0.05s;
}

.scene-cloudy.is-on .cloud-eye-r {
  transform: scaleY(0.15);
  animation: none;
}

.scene-cloudy.is-on .puff-cloud {
  animation: vg-cloud-bounce calc(0.7s * var(--yd-motion-loop-scale, 1)) var(--yd-motion-ease-spring) 1;
}

@keyframes vg-blink {
  0%,
  92%,
  100% {
    transform: scaleY(1);
  }

  95% {
    transform: scaleY(0.1);
  }
}

@keyframes vg-cloud-bounce {
  0% {
    transform: translateY(0);
  }

  40% {
    transform: translateY(-10px);
  }

  70% {
    transform: translateY(2px);
  }

  100% {
    transform: translateY(0);
  }
}

/* 已读不回 Ghosted Chat：已读标记 + 悬停出现差评回复 */

.ghost-bubble {
  position: absolute;
  right: 16%;
  height: 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px 8px 2px;
}

.ghost-bubble-a {
  top: 24%;
  width: 42%;
}

.ghost-bubble-b {
  top: 48%;
  width: 32%;
}

.ghost-checks {
  position: absolute;
  top: 1px;
  right: 3px;
  font-size: 8px;
  color: var(--color-text-3);
}

.ghost-thumb {
  position: absolute;
  bottom: 18%;
  left: 16%;
  display: inline-flex;
  align-items: center;
  padding: 3px 7px;
  font-size: 11px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  opacity: 0;
  transform: translateY(5px);
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

.ghost-thumb :deep(svg) {
  width: 11px;
  height: 11px;
}

.scene-ghost:hover .ghost-thumb,
.scene-ghost.is-on .ghost-thumb {
  opacity: 1;
  transform: translateY(0);
}

/* 玻璃文件夹 Glass Folder：袋口掀开，文件浮起 */

.folder-back {
  position: absolute;
  bottom: 22%;
  left: 24%;
  width: 52%;
  height: 34%;
  background: color-mix(in srgb, var(--color-text-1) 18%, var(--color-fill-2));
  border-radius: 4px;
}

.folder-paper {
  position: absolute;
  bottom: 26%;
  left: 28%;
  width: 44%;
  height: 28%;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 3px;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.tile:hover .folder-paper,
.scene-folder.is-on .folder-paper {
  transform: translateY(-16px);
}

.folder-lid {
  position: absolute;
  bottom: 52%;
  left: 24%;
  width: 36%;
  height: 12px;
  background: color-mix(in srgb, var(--color-text-1) 26%, var(--color-fill-2));
  border-radius: 4px 4px 0 0;
  transform-origin: left bottom;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

.tile:hover .folder-lid,
.scene-folder.is-on .folder-lid {
  transform: rotate(-26deg);
}

/* 集成蜂墙 Integration Grid：六边格向边缘淡出，中心最亮 */

.igrid-cell {
  position: absolute;
  width: 18px;
  height: 21px;
  background: color-mix(in srgb, var(--color-text-1) 22%, var(--color-fill-2));
  clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%);
  transition: background var(--yd-motion-duration-normal) var(--yd-motion-ease), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.igrid-c {
  top: 41%;
  left: 50%;
  margin-left: -9px;
  background: color-mix(in srgb, var(--yd-emphasis) 60%, var(--color-fill-2));
  opacity: 1;
}

.igrid-1 {
  top: 16%;
  left: 50%;
  margin-left: -9px;
  opacity: 0.62;
}

.igrid-2 {
  top: 30%;
  left: 26%;
  opacity: 0.5;
}

.igrid-3 {
  top: 30%;
  right: 26%;
  opacity: 0.5;
}

.igrid-4 {
  top: 54%;
  left: 26%;
  opacity: 0.38;
}

.igrid-5 {
  top: 54%;
  right: 26%;
  opacity: 0.38;
}

.igrid-6 {
  top: 68%;
  left: 50%;
  margin-left: -9px;
  opacity: 0.28;
}

.scene-igrid.is-on .igrid-c {
  animation: vg-igrid-light calc(0.5s * var(--yd-motion-loop-scale, 1)) var(--yd-motion-ease) 1;
}

.scene-igrid.is-on .igrid-1,
.scene-igrid.is-on .igrid-2,
.scene-igrid.is-on .igrid-3 {
  animation: vg-igrid-light calc(0.5s * var(--yd-motion-loop-scale, 1)) var(--yd-motion-ease) 1;
  animation-delay: 160ms;
}

.scene-igrid.is-on .igrid-4,
.scene-igrid.is-on .igrid-5,
.scene-igrid.is-on .igrid-6 {
  animation: vg-igrid-light calc(0.5s * var(--yd-motion-loop-scale, 1)) var(--yd-motion-ease) 1;
  animation-delay: 320ms;
}

@keyframes vg-igrid-light {
  0% {
    background: color-mix(in srgb, var(--color-text-1) 22%, var(--color-fill-2));
  }

  50% {
    background: var(--yd-emphasis);
  }

  100% {
    background: color-mix(in srgb, var(--color-text-1) 22%, var(--color-fill-2));
  }
}

/* 层叠选层 Layer Stack：等层堆叠，列表项对应抬起 */

.lstack {
  position: absolute;
  top: 50%;
  left: 24%;
  width: 40px;
  height: 40px;
}

.layer-plate {
  position: absolute;
  top: 0;
  left: 0;
  width: 34px;
  height: 34px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  transform: rotateX(55deg) rotateZ(45deg);
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring), background var(--yd-motion-duration-normal) var(--yd-motion-ease), border-color var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.layer-plate:nth-child(1) {
  top: 18px;
}

.layer-plate:nth-child(2) {
  top: 9px;
}

.layer-plate:nth-child(3) {
  top: 0;
}

.layer-plate.is-active {
  background: var(--color-bg-1);
  border-color: var(--yd-emphasis);
  transform: rotateX(55deg) rotateZ(45deg) translateZ(0) translateY(-8px);
}

.lstack-list {
  position: absolute;
  top: 32%;
  right: 16%;
  display: grid;
  gap: 5px;
}

.lstack-row {
  width: 24px;
  height: 4px;
  background: color-mix(in srgb, var(--color-text-1) 18%, var(--color-fill-2));
  border-radius: 2px;
  transition: background var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.lstack-row.is-active {
  background: var(--yd-emphasis);
}

/* 线索连线 Lead Sourcing：两块磁贴 + 连线流光 */

.lead-node {
  position: absolute;
  width: 20px;
  height: 20px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.lead-node-a {
  top: 40%;
  left: 14%;
}

.lead-node-b {
  top: 40%;
  right: 14%;
}

.lead-node-b::after {
  position: absolute;
  top: 6px;
  left: 6px;
  width: 6px;
  height: 6px;
  content: "";
  background: var(--yd-emphasis);
  border-radius: 50%;
}

.lead-line {
  position: absolute;
  top: 49%;
  left: 32%;
  width: 36%;
  height: 3px;
  overflow: hidden;
  background: var(--color-fill-2);
  border-radius: 2px;
}

.lead-line::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(90deg, transparent, var(--yd-emphasis), transparent);
  transform: translateX(-100%);
  animation: vg-lead-shine calc(2.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.scene-lead.is-on .lead-line::after {
  animation-direction: reverse;
}

@keyframes vg-lead-shine {
  to {
    transform: translateX(100%);
  }
}

/* 标志星簇 Logo Cluster：中心圆盘，标志弹出归位 */

.cluster-core {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 22px;
  height: 22px;
  margin: -11px 0 0 -11px;
  background: var(--color-bg-1);
  border: 2px solid color-mix(in srgb, var(--yd-emphasis) 60%, var(--color-border-2));
  border-radius: 50%;
}

.cluster-sat {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 10px;
  height: 10px;
  margin: -5px 0 0 -5px;
  background: color-mix(in srgb, var(--color-text-1) 34%, var(--color-fill-2));
  border-radius: 4px;
  opacity: 0.5;
  transform: translate(0, 0) scale(0.5);
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.scene-cluster.is-on .cluster-sat-1 {
  opacity: 1;
  transform: translate(0, -28px) scale(1);
}

.scene-cluster.is-on .cluster-sat-2 {
  opacity: 1;
  transform: translate(26px, -9px) scale(1);
}

.scene-cluster.is-on .cluster-sat-3 {
  opacity: 1;
  transform: translate(16px, 22px) scale(1);
}

.scene-cluster.is-on .cluster-sat-4 {
  opacity: 1;
  transform: translate(-16px, 22px) scale(1);
}

.scene-cluster.is-on .cluster-sat-5 {
  opacity: 1;
  transform: translate(-26px, -9px) scale(1);
}

/* 型号转轮 Model Wheel：竖向转轮滚动 */

.wheel-window {
  width: 84px;
  height: 26px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  mask-image: linear-gradient(180deg, transparent, #000 30%, #000 70%, transparent);
}

.wheel-strip {
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring);
}

.wheel-row {
  display: grid;
  place-items: center;
  height: 26px;
  font-size: 11px;
  color: var(--color-text-2);
}

/* 外联分支 Outreach Branches：一分三，脉冲沿线流动 */

.obranch-src {
  position: absolute;
  top: 47%;
  left: 12%;
  width: 12px;
  height: 12px;
  background: var(--yd-emphasis);
  border-radius: 50%;
}

.obranch-line {
  position: absolute;
  top: 52%;
  left: 20%;
  width: 24%;
  height: 2px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--color-text-1) 20%, transparent) 50%, var(--yd-emphasis) 50%) 0 0 / 200% 100%;
  transform-origin: left center;
}

.obranch-line-a {
  transform: rotate(-22deg);
}

.obranch-line-b {
  transform: rotate(0deg);
}

.obranch-line-c {
  transform: rotate(22deg);
}

.scene-obranch.is-on .obranch-line {
  animation: vg-obranch-pulse calc(1.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

@keyframes vg-obranch-pulse {
  from {
    background-position: 0 0;
  }

  to {
    background-position: -200% 0;
  }
}

.obranch-dot {
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--color-bg-1);
  border: 1.5px solid color-mix(in srgb, var(--color-text-1) 30%, var(--color-border-2));
  border-radius: 50%;
}

.obranch-dot-a {
  top: 26%;
  right: 12%;
}

.obranch-dot-b {
  top: 48%;
  right: 10%;
}

.obranch-dot-c {
  top: 70%;
  right: 12%;
}

/* 侧滑文件夹 Side Folder：横放文件夹，文档滑出 */

.sfolder-pocket {
  position: absolute;
  top: 38%;
  left: 20%;
  width: 56px;
  height: 28px;
  border: 2px solid color-mix(in srgb, var(--color-text-1) 26%, var(--color-border-2));
  border-radius: 0 6px 6px 8px;
}

.sfolder-doc {
  position: absolute;
  bottom: 2px;
  width: 10px;
  height: 20px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 2px;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

.sfolder-doc-a {
  left: 5px;
}

.sfolder-doc-b {
  left: 18px;
}

.sfolder-doc-c {
  left: 31px;
}

.scene-sfolder:hover .sfolder-doc-a,
.scene-sfolder.is-on .sfolder-doc-a {
  transform: translateX(9px) rotate(6deg);
}

.scene-sfolder:hover .sfolder-doc-b,
.scene-sfolder.is-on .sfolder-doc-b {
  transform: translateX(6px) rotate(3deg);
  transition-delay: 50ms;
}

.scene-sfolder:hover .sfolder-doc-c,
.scene-sfolder.is-on .sfolder-doc-c {
  transition-delay: 100ms;
}

.sfolder-lid {
  position: absolute;
  top: 32%;
  left: 74%;
  width: 10px;
  height: 24px;
  background: color-mix(in srgb, var(--color-text-1) 24%, var(--color-fill-2));
  border-radius: 3px;
  transform-origin: top center;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.scene-sfolder:hover .sfolder-lid,
.scene-sfolder.is-on .sfolder-lid {
  transform: rotate(28deg);
}

/* 窗口叠层 Tab Overload：窗口互相叠压，聚焦置顶 */

.tabwin {
  position: absolute;
  width: 44px;
  height: 32px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
  opacity: 0.55;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), border-color var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.tabwin-bar {
  display: block;
  height: 8px;
  background: color-mix(in srgb, var(--color-text-1) 16%, var(--color-fill-2));
}

.tabwin-1 {
  top: 18%;
  left: 16%;
}

.tabwin-2 {
  top: 32%;
  left: 36%;
}

.tabwin-3 {
  top: 46%;
  left: 56%;
}

.tabwin:hover,
.tabwin.is-focus {
  z-index: 2;
  border-color: color-mix(in srgb, var(--color-text-1) 40%, var(--color-border-2));
  opacity: 1;
  transform: translateY(-4px);
}

/* 团队邀请 Team Invite：虚线槽位补齐上线 */

.tinvite-avatar {
  position: absolute;
  top: 34%;
  width: 20px;
  height: 20px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 50%;
}

.tinvite-avatar::before {
  position: absolute;
  top: 4px;
  left: 6px;
  width: 7px;
  height: 7px;
  content: "";
  background: color-mix(in srgb, var(--color-text-1) 30%, var(--color-fill-2));
  border-radius: 50%;
}

.tinvite-avatar-a {
  left: 12%;
}

.tinvite-avatar-b {
  left: 38%;
}

.tinvite-slot {
  position: absolute;
  top: 34%;
  right: 14%;
  width: 20px;
  height: 20px;
  border: 1.5px dashed color-mix(in srgb, var(--color-text-1) 34%, var(--color-border-2));
  border-radius: 50%;
  transition: border-color var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.tinvite-ghost {
  position: absolute;
  inset: 2px;
  background: var(--yd-emphasis);
  border-radius: 50%;
  opacity: 0;
  transform: translateX(-14px) scale(0.6);
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring);
}

.scene-tinvite.is-on .tinvite-ghost {
  opacity: 1;
  transform: translateX(0) scale(1);
}

.scene-tinvite.is-on .tinvite-slot {
  border-color: var(--yd-emphasis);
  border-style: solid;
}

.tinvite-cursor {
  position: absolute;
  top: 20%;
  left: 58%;
  width: 14px;
  height: 14px;
  background: var(--color-text-1);
  opacity: 0;
  clip-path: polygon(0 0, 100% 45%, 45% 60%, 60% 100%, 40% 100%, 30% 62%, 0 100%);
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.scene-tinvite.is-on .tinvite-cursor {
  opacity: 0.85;
  transform: translate(16px, 12px);
}

.tinvite-dot {
  position: absolute;
  top: 28%;
  right: 12%;
  width: 6px;
  height: 6px;
  background: var(--color-fill-3);
  border-radius: 50%;
  transition: background var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.scene-tinvite.is-on .tinvite-dot {
  background: var(--yd-status-success);
}

/* ---------------- 产品场景 ---------------- */

/* 辉光键盘 Glow Keyboard：自动输入 + 键波 */

.kbd-type {
  position: absolute;
  top: 20%;
  left: 50%;
  width: 14%;
  height: 9px;
  background: color-mix(in srgb, var(--yd-emphasis) 55%, var(--color-fill-2));
  border-radius: 5px;
  transform: translateX(-50%);
  transition: width 1.3s steps(9);
}

.scene-kbd.is-on .kbd-type {
  width: 74%;
}

.kbd-glass {
  position: absolute;
  bottom: 18%;
  left: 50%;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 3px;
  width: 74%;
  padding: 5px;
  background: color-mix(in srgb, var(--color-bg-1) 70%, transparent);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  transform: translateX(-50%);
}

.kbd-key {
  height: 9px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 3px;
}

.scene-kbd.is-on .kbd-key {
  animation: vg-key-flash calc(1.3s * var(--yd-motion-loop-scale, 1)) ease-out both;
}

@keyframes vg-key-flash {
  0%,
  100% {
    background: var(--color-bg-1);
  }

  40% {
    background: color-mix(in srgb, var(--yd-emphasis) 55%, var(--color-bg-1));
  }
}

/* 集成光束 Integration Beam：中心磁贴在两件工具之间发光脉冲 */

.scene-beamstage {
  background: var(--color-bg-1);
}

.beam-tool {
  position: absolute;
  top: 42%;
  width: 16px;
  height: 16px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring), border-color var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.beam-tool-left {
  left: 12%;
}

.beam-tool-right {
  right: 12%;
}

.tile:hover .beam-tool {
  border-color: var(--yd-emphasis);
  transform: scale(1.18);
}

.beam-core {
  position: absolute;
  top: 44%;
  left: 50%;
  width: 12px;
  height: 12px;
  margin-left: -6px;
  background: var(--yd-emphasis);
  border-radius: 50%;
}

/* 集成环流 Integration Flow：药丸沿虚线环流汇入中心 */

.flow-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 64px;
  height: 64px;
  margin: -32px 0 0 -32px;
  border: 1.5px dashed color-mix(in srgb, var(--color-text-1) 30%, var(--color-border-2));
  border-radius: 50%;
  animation: vg-spin calc(9s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.scene-iflow.is-on .flow-ring {
  animation-play-state: paused;
}

.flow-pill {
  position: absolute;
  width: 14px;
  height: 8px;
  background: var(--color-fill-3);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.flow-pill-a {
  top: -4px;
  left: 50%;
  margin-left: -7px;
}

.flow-pill-b {
  bottom: 8px;
  left: 0;
}

.flow-pill-c {
  right: 0;
  bottom: 8px;
}

.flow-hub {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 14px;
  height: 14px;
  margin: -7px 0 0 -7px;
  background: var(--yd-emphasis);
  border-radius: 50%;
  animation: vg-hub-pulse calc(1.8s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite;
}

.scene-iflow.is-on .flow-hub {
  animation-play-state: paused;
}

@keyframes vg-hub-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--yd-emphasis) 30%, transparent);
  }

  50% {
    box-shadow: 0 0 0 7px color-mix(in srgb, var(--yd-emphasis) 12%, transparent);
  }
}

/* 任务分派 Project Flow：任务展开为子任务与备注 */

.pflow-root {
  position: absolute;
  top: 44%;
  left: 12%;
  width: 14px;
  height: 14px;
  background: var(--yd-emphasis);
  border-radius: 5px;
}

.pflow-link {
  position: absolute;
  top: 50%;
  left: 24%;
  width: 20%;
  height: 2px;
  background: color-mix(in srgb, var(--color-text-1) 24%, var(--color-border-2));
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.pflow-link-a {
  transform: rotate(-18deg) scaleX(0);
}

.pflow-link-b {
  transform: rotate(18deg) scaleX(0);
}

.scene-pflow.is-on .pflow-link {
  transform: rotate(0deg) scaleX(1);
}

.scene-pflow.is-on .pflow-link-a {
  transform: rotate(-18deg) scaleX(1);
}

.scene-pflow.is-on .pflow-link-b {
  transform: rotate(18deg) scaleX(1);
}

.pflow-sub {
  position: absolute;
  width: 16px;
  height: 10px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 3px;
  opacity: 0;
  transform: scale(0.5);
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

.pflow-sub-a {
  top: 22%;
  right: 10%;
}

.pflow-sub-b {
  top: 62%;
  right: 10%;
}

.scene-pflow.is-on .pflow-sub {
  opacity: 1;
  transform: scale(1);
}

.scene-pflow.is-on .pflow-sub-b {
  transition-delay: 90ms;
}

.pflow-note {
  position: absolute;
  top: 42%;
  right: 8%;
  width: 20px;
  height: 8px;
  background: color-mix(in srgb, var(--yd-emphasis) 30%, var(--color-fill-2));
  border-radius: 3px;
  opacity: 0;
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease) 180ms;
}

.scene-pflow.is-on .pflow-note {
  opacity: 1;
}

/* 声纹圆球 Voice Orb：呼吸 / 思考 / 发声 三态 */

.orb-dot {
  position: absolute;
  width: 5px;
  height: 5px;
  margin: -2.5px 0 0 -2.5px;
  background: color-mix(in srgb, var(--color-text-1) 40%, var(--color-fill-2));
  border-radius: 50%;
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.orb-state-0 .orb-dot {
  animation: vg-orb-breath calc(2.2s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite alternate;
}

.orb-state-1 .orb-dot {
  opacity: 0.25;
  transform: scale(0.5);
}

.orb-state-2 .orb-dot {
  background: color-mix(in srgb, var(--yd-emphasis) 55%, var(--color-fill-2));
  animation: vg-orb-speak calc(0.5s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite alternate;
}

@keyframes vg-orb-breath {
  from {
    transform: scale(0.8);
  }

  to {
    transform: scale(1.4);
  }
}

@keyframes vg-orb-speak {
  from {
    transform: translateY(-1.5px);
  }

  to {
    transform: translateY(1.5px);
  }
}

.orb-ring {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 26px;
  height: 26px;
  margin: -13px 0 0 -13px;
  border: 2px dashed var(--yd-emphasis);
  border-radius: 50%;
  opacity: 0;
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.orb-state-1 .orb-ring {
  opacity: 1;
  animation: vg-spin calc(1.2s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.orb-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 24px;
  height: 24px;
  margin: -12px 0 0 -12px;
  border: 1.5px solid var(--yd-emphasis);
  border-radius: 50%;
  opacity: 0;
}

.orb-state-2 .orb-ripple {
  animation: vg-orb-ripple calc(1.4s * var(--yd-motion-loop-scale, 1)) ease-out infinite;
}

.orb-state-2 .orb-ripple-b {
  animation-delay: calc(0.7s * var(--yd-motion-loop-scale, 1));
}

@keyframes vg-orb-ripple {
  from {
    opacity: 0.7;
    transform: scale(0.5);
  }

  to {
    opacity: 0;
    transform: scale(1.7);
  }
}

/* ---------------- 地图区域 ---------------- */

.dach-blob,
.wmap-blob {
  position: absolute;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  opacity: 0.45;
  transition: background var(--yd-motion-duration-normal) var(--yd-motion-ease), opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), box-shadow var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.dach-blob.is-active,
.wmap-blob.is-active {
  z-index: 1;
  background: color-mix(in srgb, var(--yd-emphasis) 40%, var(--color-fill-2));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--yd-emphasis) 16%, transparent);
  opacity: 1;
}

.dach-a {
  top: 14%;
  left: 14%;
  width: 40%;
  height: 38%;
  border-radius: 65% 35% 55% 45% / 55% 60% 40% 45%;
}

.dach-b {
  top: 20%;
  right: 12%;
  width: 30%;
  height: 30%;
  border-radius: 40% 60% 50% 50% / 50% 45% 55% 50%;
}

.dach-c {
  right: 22%;
  bottom: 12%;
  width: 34%;
  height: 34%;
  border-radius: 55% 45% 60% 40% / 45% 55%;
}

.dach-d {
  bottom: 16%;
  left: 12%;
  width: 30%;
  height: 28%;
  border-radius: 50% 50% 40% 60% / 60% 40%;
}

.wmap-1 {
  top: 12%;
  left: 10%;
  width: 26%;
  height: 26%;
  border-radius: 60% 40% 55% 45% / 50% 60% 40% 50%;
}

.wmap-2 {
  top: 10%;
  right: 14%;
  width: 22%;
  height: 22%;
  border-radius: 45% 55% 50% 50% / 55% 45%;
}

.wmap-3 {
  top: 42%;
  left: 16%;
  width: 24%;
  height: 24%;
  border-radius: 55% 45% 60% 40% / 45% 55%;
}

.wmap-4 {
  top: 44%;
  right: 10%;
  width: 26%;
  height: 28%;
  border-radius: 50% 50% 45% 55% / 55% 45% 50% 50%;
}

.wmap-5 {
  bottom: 10%;
  left: 30%;
  width: 22%;
  height: 20%;
  border-radius: 60% 40% 50% 50% / 50% 55% 45% 50%;
}

.wmap-6 {
  right: 28%;
  bottom: 12%;
  width: 20%;
  height: 18%;
  border-radius: 45% 55% 55% 45% / 55% 45% 50% 50%;
}

/* ---------------- 动效实验室 ---------------- */

.ref-name {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-3);
  letter-spacing: 0.08em;
}

.control-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: center;
}

.control-label {
  font-size: 12.5px;
  color: var(--color-text-2);
}

.control-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 28px;
  align-items: center;
}

.control-block {
  display: grid;
  gap: 6px;
  width: 200px;
}

.ticker-line {
  display: grid;
  gap: 8px;
}

.ticker-value {
  font-size: 34px;
  font-weight: 650;
}

.ticker-sep {
  color: var(--color-text-3);
}

/* Blur Crossfade：高度滑向下一景尺寸 + 模糊淡切 */

.deck-stage {
  position: relative;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
  transition: height var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.deck-scene {
  position: absolute;
  inset: 0;
}

.deck-band {
  position: absolute;
  background: color-mix(in srgb, var(--yd-emphasis) 26%, transparent);
}

.deck-tide .deck-band {
  right: -20%;
  bottom: -6%;
  left: -20%;
  height: 46%;
  border-radius: 50% 50% 0 0;
}

.deck-tide .deck-band-b {
  bottom: -16%;
  height: 52%;
  background: color-mix(in srgb, var(--yd-emphasis) 40%, transparent);
}

.deck-tide .deck-band-c {
  bottom: -30%;
  height: 60%;
  background: color-mix(in srgb, var(--color-text-1) 16%, transparent);
}

.deck-aurora .deck-band {
  top: 18%;
  left: -12%;
  width: 130%;
  height: 22%;
  border-radius: 999px;
  filter: blur(10px);
  transform: rotate(-8deg);
}

.deck-aurora .deck-band-b {
  top: 40%;
  background: color-mix(in srgb, var(--yd-emphasis) 42%, transparent);
  transform: rotate(-4deg);
}

.deck-aurora .deck-band-c {
  top: 62%;
  background: color-mix(in srgb, var(--color-text-1) 18%, transparent);
  transform: rotate(-10deg);
}

.deck-dune .deck-band {
  bottom: -40%;
  width: 70%;
  height: 80%;
  border-radius: 50%;
}

.deck-dune .deck-band-a {
  left: -16%;
  background: color-mix(in srgb, var(--color-text-1) 14%, transparent);
}

.deck-dune .deck-band-b {
  right: -18%;
  background: color-mix(in srgb, var(--yd-emphasis) 30%, transparent);
}

.deck-dune .deck-band-c {
  bottom: -58%;
  left: 28%;
  background: color-mix(in srgb, var(--yd-emphasis) 48%, transparent);
}

.deck-tag {
  position: absolute;
  top: 12px;
  right: 12px;
}

.scene-fade-enter-active,
.scene-fade-leave-active {
  transition: opacity var(--yd-motion-duration-slow) var(--yd-motion-ease), filter var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.scene-fade-enter-from,
.scene-fade-leave-to {
  opacity: 0;
  filter: blur(var(--vg-crossfade-blur));
}

/* Gradient Floor：底边发光的双层色晕 */

.floor-stage {
  position: relative;
  height: 170px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.floor-layer {
  position: absolute;
  bottom: -46%;
  left: -20%;
  width: 140%;
  height: 78%;
  background: radial-gradient(60% 90% at 30% 100%, color-mix(in srgb, var(--yd-emphasis) 55%, transparent), transparent 75%);
  border-radius: 50%;
  opacity: var(--vg-floor-glow, 0.6);
  filter: blur(26px);
  animation: vg-floor-drift-a calc(11s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite alternate;
}

.floor-layer-b {
  background: radial-gradient(55% 85% at 72% 100%, color-mix(in srgb, var(--yd-emphasis) 32%, var(--color-text-1) 12%, transparent), transparent 78%);
  animation: vg-floor-drift-b calc(15s * var(--yd-motion-loop-scale, 1)) ease-in-out infinite alternate;
}

.floor-stage.is-paused .floor-layer {
  animation-play-state: paused;
}

@keyframes vg-floor-drift-a {
  from {
    transform: translateX(-6%) rotate(-3deg) scale(1);
  }

  to {
    transform: translateX(7%) rotate(2deg) scale(1.08);
  }
}

@keyframes vg-floor-drift-b {
  from {
    transform: translateX(5%) rotate(2deg) scale(1.05);
  }

  to {
    transform: translateX(-6%) rotate(-2deg) scale(0.96);
  }
}

.floor-copy {
  position: relative;
  padding: 14px 16px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--color-text-2);
}

/* Gradient Text */

.grad-stage {
  display: grid;
  gap: 8px;
}

.grad-word {
  margin: 0;
  font-size: clamp(1.5rem, 3vw, 2.2rem);
  font-weight: 650;
  letter-spacing: -0.02em;
}

.grad-sub {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
}

.grad-spark {
  margin: 0;
  color: var(--color-text-2);
}

/* Grid Background */

.gridbg-stage {
  position: relative;
  height: 170px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.gridbg-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.gridbg-content {
  position: relative;
  display: grid;
  place-items: center;
  height: 100%;
}

.gridbg-content p {
  padding: 8px 14px;
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-2);
  background: color-mix(in srgb, var(--color-bg-1) 82%, transparent);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

/* Shimmer Loader：骨架块共享一道扫光 */

.sk-wrap {
  display: grid;
  gap: 10px;
  max-width: 380px;
}

.skeleton-group {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.skeleton-group::after {
  position: absolute;
  inset: 0;
  content: "";
  background: linear-gradient(100deg, transparent 30%, color-mix(in srgb, var(--color-bg-2) 72%, transparent) 50%, transparent 70%);
  transform: translateX(-100%);
  animation: vg-skeleton-sweep calc(1.9s * var(--yd-motion-loop-scale, 1)) linear infinite;
}

.sk-wrap.is-paused .skeleton-group::after {
  animation-play-state: paused;
}

@keyframes vg-skeleton-sweep {
  to {
    transform: translateX(100%);
  }
}

.sk-avatar {
  flex: none;
  width: 34px;
  height: 34px;
  background: var(--color-fill-2);
  border-radius: 50%;
}

.sk-column {
  display: grid;
  flex: 1;
  gap: 7px;
}

.sk-line {
  height: 8px;
  background: var(--color-fill-2);
  border-radius: 4px;
}

.sk-line-a {
  width: 82%;
}

.sk-line-b {
  width: 58%;
}

.sk-block {
  width: 100%;
  height: 26px;
  background: var(--color-fill-2);
  border-radius: 6px;
}

.sk-label {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-2);
}

/* Text Reveal */

.reveal-eyebrow {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-3);
  letter-spacing: 0.18em;
}

.reveal-title {
  margin: 10px 0;
  font-size: clamp(1.4rem, 3vw, 2.1rem);
  letter-spacing: -0.02em;
}

.reveal-copy {
  margin: 0;
  font-size: 16px;
  line-height: 1.7;
}

/* Tilt Card */

.tilt-stage {
  perspective: 760px;
}

.tilt-card {
  display: grid;
  gap: 12px;
  justify-items: start;
  max-width: 420px;
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.tilt-field {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  height: 96px;
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 50% 0%, color-mix(in srgb, var(--yd-emphasis) 8%, transparent), transparent 70%),
    var(--color-fill-1);
  border-radius: calc(var(--yd-control-radius) - 4px);
}

.tilt-ball {
  width: 22px;
  height: 22px;
  background: var(--yd-emphasis);
  border-radius: 50%;
  box-shadow: 0 6px 14px color-mix(in srgb, var(--yd-emphasis) 30%, transparent);
  transform: translate(var(--vg-ball-x, 0), var(--vg-ball-y, 0));
  transition: transform 120ms ease-out;
}

.tilt-title {
  margin: 0;
  font-size: 17px;
}

.tilt-copy {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-3);
}

.preference-note {
  margin: 0;
  font-size: 12px;
}

/* ---------------- 减少动态偏好：只柔化装饰的幅度与速度 ---------------- */

@media (prefers-reduced-motion: reduce) {
  .visual-gallery {
    --vg-crossfade-blur: 3px;
  }

  .sky-cloud {
    animation-duration: calc(var(--vg-cloud-drift, 75s) * 3);
  }

  .scene-fade-enter-active,
  .scene-fade-leave-active {
    transition-duration: var(--yd-motion-duration-normal);
  }
}
</style>
