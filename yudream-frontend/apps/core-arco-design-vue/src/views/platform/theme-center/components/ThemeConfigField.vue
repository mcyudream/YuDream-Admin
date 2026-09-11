<script setup lang="ts">
import type { ThemeConfigField } from '@/api/modules/platform-theme'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'

defineOptions({ name: 'ThemeConfigField' })

const props = defineProps<{
  field: ThemeConfigField
  modelValue: any
  /** 该 secret 字段是否已配置（管理端值已脱敏为空串） */
  secretConfigured?: boolean
  /** 主题归属插件编码，用于 image 字段提示主题资产路径 */
  pluginCode?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: any]
}>()

const listItems = computed<Record<string, any>[]>({
  get: () => Array.isArray(props.modelValue) ? props.modelValue : [],
  set: value => emit('update:modelValue', value),
})

function update(value: any) {
  emit('update:modelValue', value)
}

function updateNumber(raw: any) {
  if (raw === '' || raw === null || raw === undefined) {
    update(undefined)
    return
  }
  const num = Number(raw)
  update(Number.isNaN(num) ? undefined : num)
}

function updateColor(event: Event) {
  update((event.target as HTMLInputElement).value)
}

function createItem() {
  const item: Record<string, any> = {}
  props.field.itemFields?.forEach((sub) => {
    if (sub.defaultValue !== undefined) {
      item[sub.key] = sub.defaultValue
    }
  })
  listItems.value = [...listItems.value, item]
}

function removeItem(index: number) {
  listItems.value = listItems.value.filter((_, i) => i !== index)
}

function moveItem(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= listItems.value.length) {
    return
  }
  const next = [...listItems.value]
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item)
  listItems.value = next
}

function updateItemField(index: number, key: string, value: any) {
  const next = listItems.value.map((item, i) => i === index ? { ...item, [key]: value } : item)
  listItems.value = next
}

const imageUrls = computed({
  get: () => typeof props.modelValue === 'string' && props.modelValue ? [toBackendAssetUrl(props.modelValue)] : [],
  set: (urls) => {
    const raw = urls[0] || ''
    update(stripDisplayPrefix(raw))
  },
})

const toast = useFaToast()

function stripDisplayPrefix(url: string) {
  if (url.startsWith('/proxy/api/')) {
    return url.slice('/proxy'.length)
  }
  return url
}

async function uploadThemeImage({ file, onProgress }: { file: File, onProgress: (percent: number) => void }) {
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    throw new Error('请选择图片文件')
  }
  onProgress(20)
  const data = new FormData()
  data.append('file', file)
  data.append('module', props.pluginCode ? `theme-${props.pluginCode}` : 'theme-config')
  data.append('publicAccess', 'true')
  const res = await apiFiles.upload(data)
  onProgress(100)
  const url = res.data.url
  if (!url) {
    throw new Error('图片上传后未返回访问地址')
  }
  return { url }
}
</script>

<template>
  <div class="theme-config-field">
    <div class="theme-config-field__head">
      <span class="theme-config-field__label">{{ field.label }}</span>
      <FaTag v-if="field.secret" variant="secondary">
        敏感
      </FaTag>
    </div>
    <p v-if="field.description" class="theme-config-field__desc">
      {{ field.description }}
    </p>

    <FaInput
      v-if="field.type === 'text'"
      :model-value="modelValue ?? ''"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="update"
    />
    <FaTextarea
      v-else-if="field.type === 'textarea'"
      :model-value="modelValue ?? ''"
      :placeholder="field.placeholder"
      :rows="3"
      class="w-full"
      @update:model-value="update"
    />
    <FaInput
      v-else-if="field.type === 'number'"
      :model-value="modelValue ?? ''"
      type="number"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="updateNumber"
    />
    <FaSwitch
      v-else-if="field.type === 'switch'"
      :model-value="!!modelValue"
      @update:model-value="update"
    />
    <FaSelect
      v-else-if="field.type === 'select'"
      :model-value="modelValue ?? ''"
      :options="field.options ?? []"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="update"
    />
    <div v-else-if="field.type === 'color'" class="theme-config-field__color">
      <input
        type="color"
        class="theme-config-field__color-swatch"
        :value="typeof modelValue === 'string' && /^#[0-9a-fA-F]{6}$/.test(modelValue) ? modelValue : '#ffffff'"
        @input="updateColor"
      >
      <FaInput
        :model-value="modelValue ?? ''"
        :placeholder="field.placeholder || '#ffffff'"
        class="w-full"
        @update:model-value="update"
      />
    </div>
    <template v-else-if="field.type === 'image'">
      <FaImageUpload
        v-model="imageUrls"
        :max="1"
        :width="160"
        :height="96"
        :http-request="uploadThemeImage"
        :after-upload="response => response.url"
      />
      <p class="theme-config-field__hint">
        点击上传图片，保存后公开站即时生效；也可继续使用主题自带资产路径。
      </p>
    </template>
    <FaInput
      v-else-if="field.secret"
      :model-value="modelValue ?? ''"
      type="password"
      :placeholder="secretConfigured ? '已配置，留空表示不修改' : (field.placeholder || '请输入')"
      class="w-full"
      @update:model-value="update"
    />
    <div v-else-if="field.type === 'list'" class="theme-config-field__list">
      <div v-for="(item, index) in listItems" :key="index" class="theme-config-field__list-item">
        <div class="theme-config-field__list-item-head">
          <span>#{{ index + 1 }}</span>
          <div class="theme-config-field__list-item-actions">
            <FaButton size="sm" variant="ghost" :disabled="index === 0" @click="moveItem(index, -1)">
              <FaIcon name="i-ri:arrow-up-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" :disabled="index === listItems.length - 1" @click="moveItem(index, 1)">
              <FaIcon name="i-ri:arrow-down-line" />
            </FaButton>
            <FaButton size="sm" variant="ghost" @click="removeItem(index)">
              <FaIcon name="i-ri:delete-bin-line" />
            </FaButton>
          </div>
        </div>
        <div class="theme-config-field__list-item-body">
          <ThemeConfigField
            v-for="sub in field.itemFields ?? []"
            :key="sub.key"
            :field="sub"
            :model-value="item[sub.key]"
            :plugin-code="pluginCode"
            @update:model-value="updateItemField(index, sub.key, $event)"
          />
        </div>
      </div>
      <FaButton variant="outline" size="sm" @click="createItem">
        <FaIcon name="i-ri:add-line" />
        添加一项
      </FaButton>
    </div>
    <FaInput
      v-else
      :model-value="modelValue ?? ''"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="update"
    />
  </div>
</template>

<style scoped>
.theme-config-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.theme-config-field__head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-config-field__label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}

.theme-config-field__desc,
.theme-config-field__hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.theme-config-field__hint code {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--color-fill-2);
  font-size: 11px;
}

.theme-config-field__color {
  display: flex;
  align-items: center;
  gap: 8px;
}

.theme-config-field__color-swatch {
  width: 36px;
  height: 32px;
  padding: 0;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
}

.theme-config-field__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: flex-start;
}

.theme-config-field__list-item {
  width: 100%;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}

.theme-config-field__list-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-bottom: 1px solid var(--color-border-1);
  font-size: 12px;
  color: var(--color-text-3);
}

.theme-config-field__list-item-actions {
  display: flex;
  gap: 4px;
}

.theme-config-field__list-item-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
}
</style>
