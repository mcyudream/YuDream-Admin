<script setup lang="ts">
// 公开页适配：让登录/注册、知识库、插件市场、插件公开页等宿主公开页沿用站点外观。
// 颜色与字体换算为宿主变量（站点变量、组件库变量与设计令牌）；可选用站点页头/页脚作为这些页面的外壳。经 set-features 命令保存。
import type { StudioPublicSkin, StudioSkinRole } from '@/types/site-studio'
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'

defineOptions({ name: 'StudioPublicSkin' })

const store = useSiteStudioStore()
const toast = useYdToast()

const DEFAULT_SKIN: StudioPublicSkin = { chrome: true, dark: true, background: '#0e1014', surface: '#1f2430', text: '#ffffff', muted: '#a8a8a8', border: '#3a3f4b', primary: '#3c8527', primaryText: '#ffffff', bottomOffset: '120px' }
const skin = computed(() => store.doc?.features?.publicSkin)
const colorFields: { key: keyof StudioPublicSkin, label: string }[] = [
  { key: 'background', label: '页面背景' },
  { key: 'surface', label: '卡片/面板' },
  { key: 'text', label: '正文文字' },
  { key: 'muted', label: '次要文字' },
  { key: 'border', label: '边框' },
  { key: 'primary', label: '主色（按钮、链接）' },
  { key: 'primaryText', label: '主色上的文字' },
]
const fontOptions = computed(() => [{ label: '沿用宿主字体', value: '' }, ...(store.doc?.theme.fonts ?? []).map(font => ({ label: font.family, value: font.family }))])
const classOptions = computed(() => [{ label: '不沿用', value: '' }, ...(store.doc?.theme.classes ?? []).map(item => ({ label: item.name, value: item.name }))])
const roleFields: { role: StudioSkinRole, label: string }[] = [
  { role: 'panel', label: '卡片 / 面板' },
  { role: 'button', label: '按钮' },
  { role: 'control', label: '输入框 / 选择器' },
  { role: 'heading', label: '大标题（h1/h2）' },
]
function setRoleClass(role: StudioSkinRole, value: unknown) {
  const classes = { ...skin.value?.classes }
  if (value) {
    classes[role] = String(value)
  }
  else {
    delete classes[role]
  }
  const next: StudioPublicSkin = { ...(skin.value ?? DEFAULT_SKIN), classes }
  if (!Object.keys(classes).length) {
    delete next.classes
  }
  apply(next)
}
const imageOptions = computed(() => [{ label: '无背景图', value: '' }, ...(store.doc?.assets ?? []).filter(asset => asset.kind === 'image').map(asset => ({ label: asset.name, value: asset.id }))])

function apply(next: StudioPublicSkin | undefined) {
  const features = { ...store.doc?.features }
  if (next) {
    features.publicSkin = next
  }
  else {
    delete features.publicSkin
  }
  try {
    store.execute({ type: 'set-features', features })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '公开页适配保存失败')
  }
}

function patch(change: Partial<StudioPublicSkin>) {
  const next = { ...(skin.value ?? DEFAULT_SKIN), ...change }
  for (const key of ['fontFamily', 'backgroundImageAssetId', 'bottomOffset'] as const) {
    if (!next[key]) {
      delete next[key]
    }
  }
  apply(next)
}

function setColor(key: keyof StudioPublicSkin, value: unknown) {
  const hex = String(value || '').trim().slice(0, 7).toLowerCase()
  if (/^#[0-9a-f]{6}$/.test(hex)) {
    patch({ [key]: hex })
  }
  else {
    toast.error('颜色须为 #rrggbb')
  }
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="公开页适配">
    <h2 class="text-sm font-semibold m-0">
      公开页适配
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      让登录/注册、知识库、插件市场与插件公开页（如大事记）沿用本站外观：颜色、字体与背景换算为宿主变量，只在公开页面生效、不影响后台。
      开启「站点外壳」后，这些页面使用本站的页头/页脚（如底部物品栏、账号菜单）。保存并发布后生效。
    </p>
    <div v-if="!skin">
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc" @click="apply({ ...DEFAULT_SKIN })">
        <YdIcon name="morph:paintbrush" /> 启用公开页适配
      </YdButton>
    </div>
    <template v-else>
      <div class="flex flex-wrap gap-4 items-center">
        <label class="text-xs flex gap-2 items-center">
          <YdSwitch :model-value="skin.chrome" aria-label="使用站点外壳" @update:model-value="(value: unknown) => patch({ chrome: Boolean(value) })" />
          <span>使用站点外壳（页头/页脚）</span>
        </label>
        <label class="text-xs flex gap-2 items-center">
          <YdSwitch :model-value="Boolean(skin.pixel)" aria-label="像素风" @update:model-value="(value: unknown) => patch({ pixel: Boolean(value) })" />
          <span>像素风（直角、硬边框、斜面）</span>
        </label>
        <label class="text-xs flex gap-2 items-center">
          <YdSwitch :model-value="skin.dark" aria-label="深色方案" @update:model-value="(value: unknown) => patch({ dark: Boolean(value) })" />
          <span>深色方案</span>
        </label>
        <label class="text-xs flex gap-2 items-center">
          <span>底部留白</span>
          <YdInput :model-value="skin.bottomOffset ?? ''" class="w-24" placeholder="120px" aria-label="底部留白" @change="(event: Event) => patch({ bottomOffset: ((event.target as HTMLInputElement | null)?.value || '').trim() })" />
        </label>
      </div>
      <div class="gap-2 grid grid-cols-1 sm:grid-cols-2">
        <div v-for="field in colorFields" :key="field.key" class="flex gap-2 items-center">
          <span class="text-xs w-28">{{ field.label }}</span>
          <YdColorPicker
            v-auth="'platform:cms:edit'"
            :model-value="String(skin[field.key])"
            format="hex"
            :aria-label="field.label"
            @update:model-value="(value: unknown) => setColor(field.key, value)"
          />
          <code class="text-xs font-mono">{{ skin[field.key] }}</code>
        </div>
      </div>
      <div class="gap-2 grid grid-cols-1 sm:grid-cols-2">
        <label class="text-xs flex flex-col gap-1">
          <span>字体（主题字体族）</span>
          <YdSelect :model-value="skin.fontFamily ?? ''" :options="fontOptions" aria-label="公开页字体" @update:model-value="(value: unknown) => patch({ fontFamily: String(value || '') })" />
        </label>
        <label class="text-xs flex flex-col gap-1">
          <span>背景图（叠加暗色遮罩）</span>
          <YdSelect :model-value="skin.backgroundImageAssetId ?? ''" :options="imageOptions" aria-label="公开页背景图" @update:model-value="(value: unknown) => patch({ backgroundImageAssetId: String(value || '') })" />
        </label>
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-xs font-medium">沿用主题样式类</span>
        <span class="text-xs text-muted-foreground">嵌入主题页的宿主与插件页面（大事记、插件市场、知识库、活动等）直接套用这些主题样式类的外观（背景、边框、阴影、文字色与悬停状态，不改尺寸布局），与主题页同一风格。</span>
        <div class="gap-2 grid grid-cols-1 sm:grid-cols-2">
          <label v-for="field in roleFields" :key="field.role" class="text-xs flex flex-col gap-1">
            <span>{{ field.label }}</span>
            <YdSelect :model-value="skin.classes?.[field.role] ?? ''" :options="classOptions" :aria-label="`${field.label}沿用的样式类`" @update:model-value="(value: unknown) => setRoleClass(field.role, value)" />
          </label>
        </div>
      </div>
      <div>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" @click="apply(undefined)">
          关闭公开页适配
        </YdButton>
      </div>
    </template>
  </section>
</template>
