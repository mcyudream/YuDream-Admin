<script setup lang="ts">
// 站点内容模型：主题作者用 JSON 声明分区与字段（与主题配置 schema 同形），运营在「站点内容」页可视化填写。
// 修改模型时保留已填写且仍有对应字段的值；校验与保存走 set-features 命令管道。
import type { StudioContent, StudioContentEffect, StudioContentSection } from '@/types/site-studio'
import { computed, ref, watch } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { STUDIO_CONTENT_EFFECTS } from '@/types/site-studio'
import { STUDIO_EFFECT_LABELS, studioEffectField, studioEffectSection } from '@/utils/site-studio-effects'
import { cloneStudioJson, validateStudioDocument } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioContentModel' })

const store = useSiteStudioStore()
const toast = useYdToast()
const router = useRouter()

const content = computed(() => store.doc?.features?.content)
const fieldCount = computed(() => (content.value?.sections ?? []).reduce((sum, section) => sum + section.fields.length, 0))

const draft = ref('')
const draftError = ref('')
const selectedEffect = ref<StudioContentEffect | ''>('')
const availableEffects = computed(() => {
  const declared = new Set((content.value?.sections ?? []).flatMap(section => section.fields.map(field => field.effect)))
  return STUDIO_CONTENT_EFFECTS.filter(effect => !declared.has(effect)).map(effect => ({ label: STUDIO_EFFECT_LABELS[effect], value: effect }))
})
watch(content, (value) => {
  draft.value = JSON.stringify(value?.sections ?? [], null, 2)
  draftError.value = ''
}, { immediate: true })

/** 按新模型构造内容：保留仍有对应字段的已填值（值不再合法时由校验报错） */
function nextContent(sections: StudioContentSection[]): StudioContent {
  const keys = new Set(sections.flatMap(section => section.fields.map(field => field.key)))
  const values = Object.fromEntries(Object.entries(content.value?.values ?? {}).filter(([key]) => keys.has(key)))
  return { sections, values }
}

function parse(text: string) {
  const sections = JSON.parse(text) as StudioContentSection[]
  if (!store.doc) {
    throw new Error('站点文档未加载')
  }
  const doc = cloneStudioJson(store.doc)
  doc.features = { ...doc.features, content: nextContent(sections) }
  validateStudioDocument(doc)
  return sections
}

function onInput(value: string) {
  draft.value = value
  try {
    parse(value)
    draftError.value = ''
  }
  catch (error) {
    draftError.value = error instanceof Error ? error.message : '内容模型无效'
  }
}

function applyModel(): boolean {
  try {
    const sections = parse(draft.value)
    store.execute({ type: 'set-features', features: { ...store.doc?.features, content: nextContent(sections) } })
    toast.success('内容模型已应用到草稿')
    return true
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '内容模型保存失败')
    return false
  }
}

function addEffect() {
  if (!selectedEffect.value) {
    return
  }
  try {
    const sections = parse(draft.value)
    const effect = selectedEffect.value
    if (sections.some(section => section.fields.some(field => field.effect === effect))) {
      throw new Error('该功能已经在内容模型中声明')
    }
    const group = studioEffectSection(effect)
    const usedKeys = new Set(sections.flatMap(section => section.fields.map(field => field.key)))
    const usedCodes = new Set(sections.map(section => section.code))
    const field = studioEffectField(effect)
    for (let index = 2; usedKeys.has(field.key); index++) {
      field.key = `${field.key.replace(/\d+$/, '')}${index}`
    }
    let section = sections.find(item => item.code === group.code && item.title === group.title)
    if (!section) {
      let code = group.code
      for (let index = 2; usedCodes.has(code); index++) {
        code = `${group.code}-${index}`
      }
      section = { ...group, code, fields: [] }
      sections.push(section)
    }
    section.fields.push(field)
    draft.value = JSON.stringify(sections, null, 2)
    if (applyModel()) {
      selectedEffect.value = ''
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '添加功能字段失败')
  }
}

function removeModel() {
  const features = { ...store.doc?.features }
  delete features.content
  try {
    store.execute({ type: 'set-features', features })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '内容模型移除失败')
  }
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="站点内容模型">
    <h2 class="text-sm font-semibold m-0">
      站点内容模型
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      声明运营可以修改的内容（分区 → 字段，支持图片、音频、粒子与列表）。下方可直接添加站点功能字段；高级字段可编辑 JSON。
      页面元素在检查器「站点内容」中绑定单值字段，列表字段在「数据绑定」中作为「站点内容」数据源；运营在「站点内容」页可视化填写，随草稿保存与发布。
    </p>
    <p class="text-xs m-0">
      当前：{{ content ? `${content.sections.length} 个分区 · ${fieldCount} 个字段` : '未启用' }}
    </p>
    <div class="flex flex-wrap gap-2 items-end">
      <label class="text-xs flex flex-col gap-1">
        添加主题功能
        <YdSelect v-model="selectedEffect" :options="availableEffects" placeholder="选择功能" class="min-w-[200px]" />
      </label>
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!selectedEffect || Boolean(draftError) || !store.doc" @click="addEffect">
        <YdIcon name="morph:plus" /> 添加到内容模型
      </YdButton>
      <span class="text-xs text-muted-foreground">添加后在「站点内容」页直接编辑值。</span>
    </div>
    <YdCodeEditor
      v-auth="'platform:cms:edit'"
      :model-value="draft"
      language="json"
      height="320px"
      aria-label="站点内容模型 JSON"
      :error="draftError"
      @update:model-value="onInput"
    />
    <div class="flex flex-wrap gap-2">
      <YdButton v-auth="'platform:cms:edit'" :disabled="Boolean(draftError) || !store.doc" @click="applyModel">
        <YdIcon name="morph:check" /> 应用内容模型
      </YdButton>
      <YdButton variant="outline" :disabled="!content" @click="router.push('/platform/site-content')">
        <YdIcon name="morph:file-pen" /> 去填写站点内容
      </YdButton>
      <YdButton v-if="content" v-auth="'platform:cms:edit'" variant="ghost" @click="removeModel">
        移除内容模型
      </YdButton>
    </div>
  </section>
</template>
