<script setup lang="ts">
// 主题样式表：共享样式类（含 hover/当前页等状态与断点）、关键帧动画、字体，以受限 CSS 文本编辑。
// 输入实时解析并对整份文档做校验，通过后经 set-theme 命令一次性替换 classes/keyframes/fonts。
import type { SiteDocument } from '@/types/site-studio'
import { computed, ref, watch } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { formatStudioStylesheet, parseStudioStylesheet } from '@/utils/site-studio-stylesheet'
import { validateStudioDocument } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioThemeStylesheet' })

const store = useSiteStudioStore()
const toast = useYdToast()

const draft = ref('')
const error = ref('')
const dirty = ref(false)
const summary = computed(() => {
  const theme = store.doc?.theme
  return `样式类 ${theme?.classes?.length ?? 0} 个 · 动画 ${theme?.keyframes?.length ?? 0} 个 · 字体 ${theme?.fonts?.length ?? 0} 个`
})
const fontAssets = computed(() => (store.doc?.assets ?? []).filter(asset => asset.kind === 'font'))

function current() {
  return store.doc ? formatStudioStylesheet(store.doc) : ''
}

watch(() => store.editRevision, () => {
  if (!dirty.value) {
    draft.value = current()
    error.value = ''
  }
}, { immediate: true })

function check(value: string) {
  const doc = store.doc
  if (!doc) {
    throw new Error('尚未创建站点文档')
  }
  const parsed = parseStudioStylesheet(value, doc.assets)
  const theme = { ...doc.theme, classes: parsed.classes, keyframes: parsed.keyframes, fonts: parsed.fonts }
  validateStudioDocument(JSON.parse(JSON.stringify({ ...doc, theme })) as SiteDocument)
  return parsed
}

function onInput(value: string) {
  draft.value = value
  dirty.value = true
  try {
    check(value)
    error.value = ''
  }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : '样式表校验失败'
  }
}

function apply() {
  try {
    const parsed = check(draft.value)
    store.execute({ type: 'set-theme', classes: parsed.classes, keyframes: parsed.keyframes, fonts: parsed.fonts })
    dirty.value = false
    draft.value = current()
    toast.success('主题样式表已应用')
  }
  catch (cause) {
    error.value = cause instanceof Error ? cause.message : '样式表应用失败'
    toast.error(error.value)
  }
}

function reset() {
  dirty.value = false
  draft.value = current()
  error.value = ''
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="主题样式表">
    <h2 class="text-sm font-semibold m-0">
      主题样式表
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      {{ summary }}。节点在「属性」里填写 <code class="font-mono">class</code> 即可引用样式类；节点自身样式优先于样式类。
      支持 <code class="font-mono">.名称{}</code>、状态 <code class="font-mono">:hover</code> <code class="font-mono">:focus</code> <code class="font-mono">:active</code>
      <code class="font-mono">[aria-current="page"]</code>（当前页链接） <code class="font-mono">[aria-pressed="true"]</code>（已开启的开关）、
      <code class="font-mono">@media (max-width: 992px / 768px)</code>、<code class="font-mono">@keyframes</code>，以及引用字体资产的 <code class="font-mono">@font-face</code>。
    </p>
    <p v-if="fontAssets.length" class="text-xs text-muted-foreground m-0">
      可用字体资产：<code v-for="asset in fontAssets" :key="asset.id" class="font-mono mr-2">{{ asset.name }} → {{ asset.url }}</code>
    </p>
    <YdCodeEditor
      v-auth="'platform:cms:edit'"
      :model-value="draft"
      language="css"
      height="420px"
      aria-label="主题样式表"
      :error="error"
      @update:model-value="onInput"
    />
    <div class="flex flex-wrap gap-2">
      <YdButton v-auth="'platform:cms:edit'" :disabled="Boolean(error) || !dirty || !store.doc" @click="apply">
        <YdIcon name="morph:check" /> 应用样式表
      </YdButton>
      <YdButton variant="ghost" :disabled="!dirty" @click="reset">
        放弃修改
      </YdButton>
    </div>
  </section>
</template>
