<script setup lang="ts">
// 数据模型编辑（CMS 后台）：在「站点内容」页直接创建/修改分区与字段，不再只依赖主题作者声明。
// 结构化编辑 + 列表条目字段 JSON（高级）；每次修改经 set-features 命令原子应用，
// 保留仍有对应字段的已填值，字段改键时把旧值带到新键；校验失败就地提示且不落草稿。
import type { StudioContentEffect, StudioContentField, StudioContentFieldType, StudioContentSection } from '@/types/site-studio'
import { computed, ref } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { STUDIO_CONTENT_EFFECTS, STUDIO_CONTENT_FIELD_TYPES, STUDIO_PARTICLE_PRESETS } from '@/types/site-studio'
import { STUDIO_EFFECT_LABELS, STUDIO_PARTICLE_LABELS, studioEffectField, studioEffectSection } from '@/utils/site-studio-effects'
import { cloneStudioJson, validateStudioDocument } from '@/utils/site-studio-validation'

defineOptions({ name: 'StudioContentModelEditor' })

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const content = computed(() => store.doc?.features?.content)
const sections = computed(() => content.value?.sections ?? [])
const fieldCount = computed(() => sections.value.reduce((sum, section) => sum + section.fields.length, 0))

const TYPE_LABELS: Record<string, string> = {
  text: '短文本',
  textarea: '长文本',
  number: '数字',
  switch: '开关',
  select: '下拉',
  color: '颜色',
  image: '图片',
  link: '链接',
  list: '列表',
  particles: '粒子',
  audio: '音频',
  video: '视频',
}

const typeOptions = STUDIO_CONTENT_FIELD_TYPES.map(type => ({ label: TYPE_LABELS[type] ?? type, value: type }))
const particleOptions = STUDIO_PARTICLE_PRESETS.map(value => ({ label: STUDIO_PARTICLE_LABELS[value], value }))

// 展开的「高级」行：fieldKey -> true
const expanded = ref<Record<string, boolean>>({})
// 列表条目字段草稿（fieldKey -> JSON 文本），应用成功后清空
const itemFieldDrafts = ref<Record<string, string>>({})
const itemFieldErrors = ref<Record<string, string>>({})

function uniqueKey(base: string, sections_: StudioContentSection[]) {
  const used = new Set(sections_.flatMap(section => section.fields.map(field => field.key)))
  let key = base
  for (let index = 2; used.has(key); index++) {
    key = `${base}${index}`
  }
  return key
}

function uniqueCode(base: string, sections_: StudioContentSection[]) {
  const used = new Set(sections_.map(section => section.code))
  let code = base
  for (let index = 2; used.has(code); index++) {
    code = `${base}-${index}`
  }
  return code
}

/** 应用整个模型：保留仍有字段的已填值，键重命名的值随 overrides 携带；校验失败不落草稿 */
function applySections(next: StudioContentSection[], valueOverrides: Record<string, unknown> = {}) {
  if (!store.doc) {
    return false
  }
  const keys = new Set(next.flatMap(section => section.fields.map(field => field.key)))
  const values: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(content.value?.values ?? {})) {
    if (keys.has(key)) {
      values[key] = value
    }
  }
  Object.assign(values, valueOverrides)
  try {
    const doc = cloneStudioJson(store.doc)
    doc.features = { ...doc.features, content: { sections: cloneStudioJson(next), values } }
    validateStudioDocument(doc)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '数据模型无效，本次修改未应用')
    return false
  }
  try {
    store.execute({ type: 'set-features', features: { ...store.doc.features, content: { sections: next, values } } })
    return true
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '数据模型保存失败')
    return false
  }
}

function mutate(mutator: (sections: StudioContentSection[]) => void, valueOverrides: Record<string, unknown> = {}) {
  const next = cloneStudioJson(sections.value) as StudioContentSection[]
  mutator(next)
  return applySections(next, valueOverrides)
}

// ===== 分区 =====

function addSection() {
  mutate((list) => {
    list.push({ code: uniqueCode('section', list), title: '新分区', fields: [] })
  })
}

function updateSection(index: number, patch: Partial<StudioContentSection>) {
  mutate((list) => {
    list[index] = { ...list[index]!, ...patch }
  })
}

function moveSection(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= sections.value.length) {
    return
  }
  mutate((list) => {
    [list[index], list[target]] = [list[target]!, list[index]!]
  })
}

function removeSection(index: number) {
  const section = sections.value[index]
  if (!section) {
    return
  }
  const hasValues = section.fields.some(field => content.value?.values[field.key] !== undefined)
  const run = () => mutate(list => list.splice(index, 1))
  if (!hasValues) {
    run()
    return
  }
  modal.confirm({
    title: `删除分区「${section.title}」？`,
    content: '分区里已填写的内容值会一并移除；该操作可撤销（工作台撤销历史内）。',
    confirmButtonText: '删除分区',
    cancelButtonText: '取消',
    onConfirm: run,
  })
}

// ===== 字段 =====

function addField(sectionIndex: number) {
  mutate((list) => {
    const section = list[sectionIndex]
    if (!section || section.fields.length >= 40) {
      return
    }
    section.fields.push({ key: uniqueKey('field', list), label: '新字段', type: 'text' })
  })
}

function patchField(sectionIndex: number, fieldIndex: number, patch: Partial<StudioContentField>, valueOverrides: Record<string, unknown> = {}) {
  mutate((list) => {
    const field = list[sectionIndex]?.fields[fieldIndex]
    if (field) {
      Object.assign(field, patch)
    }
  }, valueOverrides)
}

/** 改类型：清理与新类型不兼容的声明（options/itemFields/defaultValue） */
function changeFieldType(sectionIndex: number, fieldIndex: number, type: StudioContentFieldType) {
  mutate((list) => {
    const field = list[sectionIndex]?.fields[fieldIndex]
    if (!field) {
      return
    }
    field.type = type
    if (type !== 'select') {
      delete field.options
    }
    if (type !== 'list') {
      delete field.itemFields
    }
    if (type === 'switch') {
      field.defaultValue = false
    }
    else if (type === 'number') {
      field.defaultValue = undefined
    }
    else if (type === 'list' || type === 'particles' || type === 'textarea') {
      field.defaultValue = undefined
    }
  })
}

function renameField(sectionIndex: number, fieldIndex: number, newKey: string) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field || field.key === newKey) {
    return
  }
  const overrides: Record<string, unknown> = {}
  const values = content.value?.values ?? {}
  if (oldValueOf(field.key) !== undefined) {
    overrides[newKey] = values[field.key]
  }
  patchField(sectionIndex, fieldIndex, { key: newKey }, overrides)
}

function oldValueOf(key: string) {
  return content.value?.values[key]
}

function moveField(sectionIndex: number, fieldIndex: number, offset: number) {
  const list = sections.value[sectionIndex]?.fields ?? []
  const target = fieldIndex + offset
  if (target < 0 || target >= list.length) {
    return
  }
  mutate((draft) => {
    const fields = draft[sectionIndex]!.fields
    ;[fields[fieldIndex], fields[target]] = [fields[target]!, fields[fieldIndex]!]
  })
}

function removeField(sectionIndex: number, fieldIndex: number) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field) {
    return
  }
  const run = () => mutate(list => list[sectionIndex]?.fields.splice(fieldIndex, 1))
  if (oldValueOf(field.key) === undefined) {
    run()
    return
  }
  modal.confirm({
    title: `删除字段「${field.label}」？`,
    content: '该字段已填写的内容值会一并移除；页面上绑定此字段的元素将回到主题默认。',
    confirmButtonText: '删除字段',
    cancelButtonText: '取消',
    onConfirm: run,
  })
}

// ===== 主题功能快捷添加（粒子/音乐/音效等平台效果） =====

const selectedEffect = ref<StudioContentEffect | ''>('')
const availableEffects = computed(() => {
  const declared = new Set(sections.value.flatMap(section => section.fields.map(field => field.effect)))
  return STUDIO_CONTENT_EFFECTS.filter(effect => !declared.has(effect)).map(effect => ({ label: STUDIO_EFFECT_LABELS[effect], value: effect }))
})

function addEffect() {
  if (!selectedEffect.value) {
    return
  }
  const effect = selectedEffect.value
  const group = studioEffectSection(effect)
  let applied = false
  const overrides: Record<string, unknown> = {}
  mutate((list) => {
    if (list.some(section => section.fields.some(field => field.effect === effect))) {
      return
    }
    const field = studioEffectField(effect)
    field.key = uniqueKey(field.key, list)
    let section = list.find(item => item.code === group.code)
    if (!section) {
      section = { ...group, code: uniqueCode(group.code, list), fields: [] }
      list.push(section)
    }
    section.fields.push(field)
    if (field.defaultValue !== undefined) {
      overrides[field.key] = field.defaultValue
    }
    applied = true
  }, overrides)
  if (applied) {
    selectedEffect.value = ''
  }
}

// ===== 列表条目字段（JSON，高级） =====

function itemFieldText(key: string) {
  return itemFieldDrafts.value[key] ?? JSON.stringify(sections.value.flatMap(section => section.fields).find(field => field.key === key)?.itemFields ?? [], null, 1)
}

function applyItemFields(sectionIndex: number, fieldIndex: number) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field) {
    return
  }
  const text = itemFieldText(field.key)
  try {
    const itemFields = JSON.parse(text) as StudioContentField[]
    if (!Array.isArray(itemFields)) {
      throw new TypeError('条目字段须为数组')
    }
    patchField(sectionIndex, fieldIndex, { itemFields })
    itemFieldErrors.value = { ...itemFieldErrors.value, [field.key]: '' }
    itemFieldDrafts.value = { ...itemFieldDrafts.value, [field.key]: '' }
  }
  catch (error) {
    itemFieldErrors.value = { ...itemFieldErrors.value, [field.key]: error instanceof Error ? error.message : '条目字段 JSON 无效' }
  }
}

function defaultValueOf(field: StudioContentField): unknown {
  return field.defaultValue
}

function setDefault(sectionIndex: number, fieldIndex: number, value: unknown) {
  patchField(sectionIndex, fieldIndex, { defaultValue: value })
}

/** 默认值输入框类型：switch/number/select/particles 用对应控件，其余按文本 */
function defaultInputKind(field: StudioContentField): 'switch' | 'number' | 'select' | 'text' {
  if (field.type === 'switch') {
    return 'switch'
  }
  if (field.type === 'number') {
    return 'number'
  }
  if (field.type === 'select' || field.type === 'particles') {
    return 'select'
  }
  return 'text'
}

function selectOptionsOf(field: StudioContentField) {
  if (field.type === 'particles') {
    return particleOptions
  }
  return (field.options ?? []).map(option => ({ label: option.label, value: option.value }))
}

// ===== select 选项行编辑 =====

function addOption(sectionIndex: number, fieldIndex: number) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field || (field.options?.length ?? 0) >= 30) {
    return
  }
  const options = [...(field.options ?? []), { label: `选项${(field.options?.length ?? 0) + 1}`, value: `v${(field.options?.length ?? 0) + 1}` }]
  patchField(sectionIndex, fieldIndex, { options })
}

function updateOption(sectionIndex: number, fieldIndex: number, optionIndex: number, patch: Partial<{ label: string, value: string }>) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field?.options?.[optionIndex]) {
    return
  }
  const options = field.options.map((option, index) => index === optionIndex ? { ...option, ...patch } : option)
  patchField(sectionIndex, fieldIndex, { options })
}

function removeOption(sectionIndex: number, fieldIndex: number, optionIndex: number) {
  const field = sections.value[sectionIndex]?.fields[fieldIndex]
  if (!field?.options) {
    return
  }
  patchField(sectionIndex, fieldIndex, { options: field.options.filter((_, index) => index !== optionIndex) })
}
</script>

<template>
  <section class="flex flex-col gap-4" aria-label="数据模型">
    <div class="flex flex-wrap gap-2 items-end">
      <div class="flex-1 min-w-60">
        <h2 class="text-sm font-semibold m-0">
          数据模型
        </h2>
        <p class="text-xs text-muted-foreground m-0 mt-1">
          创建/修改分区与字段：字段在上方各分区页签中填写值，页面元素在检查器「站点内容」绑定单值字段、列表字段作为「站点内容」数据源。
          修改保留仍有字段的已填值；字段改键会把已填值带到新键。保存草稿后生效，随发布上线。
        </p>
      </div>
      <label class="text-xs flex flex-col gap-1">
        添加平台功能字段
        <YdSelect v-model="selectedEffect" :options="availableEffects" placeholder="粒子/音乐/音效等" class="min-w-[190px]" />
      </label>
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!selectedEffect" @click="addEffect">
        <YdIcon name="morph:magic-wand" /> 添加功能
      </YdButton>
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc || sections.length >= 20" @click="addSection">
        <YdIcon name="morph:plus" /> 添加分区
      </YdButton>
    </div>
    <p class="text-xs text-muted-foreground m-0">
      当前：{{ sections.length }} 个分区 · {{ fieldCount }} 个字段（每分区最多 40 个字段，最多 20 个分区）
    </p>

    <div v-for="(section, si) in sections" :key="section.code" class="p-3 border rounded flex flex-col gap-3">
      <div class="flex flex-wrap gap-2 items-center">
        <span class="text-xs text-muted-foreground w-10">分区</span>
        <YdInput :model-value="section.title" class="w-40" aria-label="分区标题" @change="(event: Event) => updateSection(si, { title: ((event.target as HTMLInputElement).value || '').trim() || section.title })" />
        <YdInput :model-value="section.code" class="font-mono w-40" aria-label="分区编码" @change="(event: Event) => updateSection(si, { code: ((event.target as HTMLInputElement).value || '').trim() || section.code })" />
        <span class="flex-1" />
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="上移分区" :disabled="si === 0" @click="moveSection(si, -1)">
          <YdIcon name="morph:arrow-up" />
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="下移分区" :disabled="si === sections.length - 1" @click="moveSection(si, 1)">
          <YdIcon name="morph:arrow-down" />
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除分区" @click="removeSection(si)">
          <YdIcon name="morph:trash-2" />
        </YdButton>
      </div>

      <div v-for="(field, fi) in section.fields" :key="field.key" class="p-2 border rounded bg-muted/20 flex flex-col gap-2">
        <div class="flex flex-wrap gap-2 items-center">
          <YdInput :model-value="field.label" class="w-32" aria-label="字段名称" @change="(event: Event) => patchField(si, fi, { label: ((event.target as HTMLInputElement).value || '').trim() || field.label })" />
          <YdInput :model-value="field.key" class="font-mono w-36" aria-label="字段键" @change="(event: Event) => renameField(si, fi, ((event.target as HTMLInputElement).value || '').trim() || field.key)" />
          <YdSelect
            class="w-28"
            :model-value="field.type"
            :options="typeOptions"
            :disabled="Boolean(field.effect)"
            aria-label="字段类型"
            @update:model-value="(value: unknown) => changeFieldType(si, fi, String(value) as StudioContentFieldType)"
          />
          <span v-if="field.effect" class="text-xs text-primary px-2 py-0.5 rounded bg-primary/10">
            功能：{{ STUDIO_EFFECT_LABELS[field.effect] }}
          </span>
          <span class="flex-1" />
          <YdButton variant="ghost" size="sm" :aria-label="`高级设置 ${field.label}`" :aria-expanded="Boolean(expanded[field.key])" @click="expanded = { ...expanded, [field.key]: !expanded[field.key] }">
            <YdIcon name="morph:settings-2" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="上移字段" :disabled="fi === 0" @click="moveField(si, fi, -1)">
            <YdIcon name="morph:arrow-up" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="下移字段" :disabled="fi === section.fields.length - 1" @click="moveField(si, fi, 1)">
            <YdIcon name="morph:arrow-down" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除字段" @click="removeField(si, fi)">
            <YdIcon name="morph:trash-2" />
          </YdButton>
        </div>

        <div v-if="expanded[field.key]" class="pl-2 border-l-2 border-border flex flex-col gap-2">
          <div class="flex flex-wrap gap-2 items-center">
            <label class="text-xs flex gap-1 items-center">
              默认值
              <YdSwitch
                v-if="defaultInputKind(field) === 'switch'"
                :model-value="Boolean(defaultValueOf(field))"
                aria-label="字段默认值"
                @update:model-value="(value: unknown) => setDefault(si, fi, Boolean(value))"
              />
              <YdSelect
                v-else-if="defaultInputKind(field) === 'select'"
                class="w-40"
                :model-value="String(defaultValueOf(field) ?? '')"
                :options="[{ label: '（无）', value: '' }, ...selectOptionsOf(field)]"
                aria-label="字段默认值"
                @update:model-value="(value: unknown) => setDefault(si, fi, value || undefined)"
              />
              <YdInput
                v-else
                class="w-52"
                :type="defaultInputKind(field) === 'number' ? 'number' : 'text'"
                :model-value="defaultValueOf(field) === undefined || defaultValueOf(field) === null ? '' : String(defaultValueOf(field))"
                aria-label="字段默认值"
                @change="(event: Event) => setDefault(si, fi, ((event.target as HTMLInputElement).value || '').trim() || undefined)"
              />
            </label>
            <YdInput :model-value="field.placeholder ?? ''" class="w-44" placeholder="占位提示" aria-label="字段占位" @change="(event: Event) => patchField(si, fi, { placeholder: ((event.target as HTMLInputElement).value || '').trim() || undefined })" />
            <YdInput :model-value="field.description ?? ''" class="flex-1 min-w-40" placeholder="字段说明" aria-label="字段说明" @change="(event: Event) => patchField(si, fi, { description: ((event.target as HTMLInputElement).value || '').trim() || undefined })" />
          </div>

          <div v-if="field.type === 'select'" class="flex flex-col gap-1">
            <div class="text-xs text-muted-foreground">
              下拉选项（1–30 个；「默认值」取选项值）
            </div>
            <div v-for="(option, oi) in field.options ?? []" :key="oi" class="flex gap-2 items-center">
              <YdInput :model-value="option.label" class="w-36" aria-label="选项名称" @change="(event: Event) => updateOption(si, fi, oi, { label: ((event.target as HTMLInputElement).value || '').trim() || option.label })" />
              <YdInput :model-value="option.value" class="font-mono w-36" aria-label="选项值" @change="(event: Event) => updateOption(si, fi, oi, { value: ((event.target as HTMLInputElement).value || '').trim() || option.value })" />
              <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除选项" @click="removeOption(si, fi, oi)">
                <YdIcon name="morph:trash-2" />
              </YdButton>
            </div>
            <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" class="self-start" @click="addOption(si, fi)">
              <YdIcon name="morph:plus" /> 添加选项
            </YdButton>
          </div>

          <div v-if="field.type === 'list'" class="flex flex-col gap-1">
            <div class="text-xs text-muted-foreground">
              列表条目字段（JSON 数组，1–12 个；嵌套列表不支持）
            </div>
            <YdCodeEditor
              :model-value="itemFieldText(field.key)"
              language="json"
              height="140px"
              :error="itemFieldErrors[field.key] || undefined"
              aria-label="条目字段 JSON"
              @update:model-value="(value: string) => itemFieldDrafts = { ...itemFieldDrafts, [field.key]: value }"
            />
            <YdButton v-auth="'platform:cms:edit'" variant="outline" size="sm" class="self-start" :disabled="Boolean(itemFieldErrors[field.key])" @click="applyItemFields(si, fi)">
              <YdIcon name="morph:check" /> 应用条目字段
            </YdButton>
          </div>
        </div>
      </div>

      <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" class="self-start" :disabled="section.fields.length >= 40" @click="addField(si)">
        <YdIcon name="morph:plus" /> 添加字段
      </YdButton>
    </div>

    <YdEmpty v-if="!sections.length" description="还没有数据模型：添加一个分区和几个字段，页面元素即可绑定这些内容">
      <YdButton v-auth="'platform:cms:edit'" variant="outline" @click="addSection">
        <YdIcon name="morph:plus" /> 添加第一个分区
      </YdButton>
    </YdEmpty>
  </section>
</template>
