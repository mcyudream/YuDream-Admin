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
  /** CMS 内容值允许显式留空以关闭效果；主题插件配置沿用清空后回默认值的提示。 */
  emptyMeansDefault?: boolean
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

function updateColor(value: string | number | undefined) {
  if (value === undefined) {
    return
  }
  update(String(value))
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

const toast = useYdToast()

const imageUrls = ref<string[]>(toDisplayUrls(props.modelValue))

watch(() => props.modelValue, (value) => {
  const next = toDisplayUrls(value)
  if (sameUrls(next, imageUrls.value)) {
    return
  }
  imageUrls.value = next
})

watch(imageUrls, (urls) => {
  const stored = stripDisplayPrefix(urls[0] || '')
  const current = typeof props.modelValue === 'string' ? props.modelValue : ''
  if (stored !== current) {
    update(stored)
  }
}, { deep: true })

function toDisplayUrls(value: unknown) {
  return typeof value === 'string' && value ? [toBackendAssetUrl(value)] : []
}

function sameUrls(left: string[], right: string[]) {
  return left.length === right.length && left.every((item, index) => item === right[index])
}

function stripDisplayPrefix(url: string) {
  if (url.startsWith('/proxy/api/')) {
    return url.slice('/proxy'.length)
  }
  return url
}

// ===== audio：上传或填写音频地址（站内文件 URL / 主题资产路径），可试听；清空存空串 =====
const audioUploading = ref(false)
const audioSrc = computed(() => typeof props.modelValue === 'string' && props.modelValue ? toBackendAssetUrl(props.modelValue) : '')
async function uploadThemeAudio() {
  const [file] = await useYdFilePicker().open({ accept: 'audio/mpeg,audio/ogg,audio/wav,audio/webm', multiple: false })
  if (!file) {
    return
  }
  if (!file.type.startsWith('audio/')) {
    toast.error('请选择 MP3/OGG/WAV/WebM 音频文件')
    return
  }
  audioUploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('module', props.pluginCode ? `theme-${props.pluginCode}` : 'theme-config')
    data.append('publicAccess', 'true')
    const url = (await apiFiles.upload(data)).data.url
    if (!url) {
      throw new Error('音频上传后未返回访问地址')
    }
    update(url)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '音频上传失败')
  }
  finally {
    audioUploading.value = false
  }
}

// ===== video：上传或填写视频地址（站内文件 URL / https），可预览；清空存空串 =====
const VIDEO_TYPES = ['video/mp4', 'video/webm', 'video/ogg']
const videoUploading = ref(false)
const videoSrc = computed(() => typeof props.modelValue === 'string' && props.modelValue ? toBackendAssetUrl(props.modelValue) : '')
async function uploadThemeVideo() {
  const [file] = await useYdFilePicker().open({ accept: VIDEO_TYPES.join(','), multiple: false })
  if (!file) {
    return
  }
  if (!VIDEO_TYPES.includes(file.type)) {
    toast.error('请选择 MP4/WebM/OGG 视频文件（浏览器可直接播放的格式）')
    return
  }
  videoUploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('module', props.pluginCode ? `theme-${props.pluginCode}` : 'theme-config')
    data.append('publicAccess', 'true')
    const url = (await apiFiles.upload(data)).data.url
    if (!url) {
      throw new Error('视频上传后未返回访问地址')
    }
    update(url)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '视频上传失败')
  }
  finally {
    videoUploading.value = false
  }
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
  return { url: toBackendAssetUrl(url) }
}
</script>

<template>
  <div class="theme-config-field">
    <div class="theme-config-field__head">
      <span class="theme-config-field__label">{{ field.label }}</span>
      <YdTag v-if="field.secret" variant="secondary">
        敏感
      </YdTag>
    </div>
    <p v-if="field.description" class="theme-config-field__desc">
      {{ field.description }}
    </p>

    <YdInput
      v-if="field.type === 'text'"
      :model-value="modelValue ?? ''"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="update"
    />
    <YdTextarea
      v-else-if="field.type === 'textarea'"
      :model-value="modelValue ?? ''"
      :placeholder="field.placeholder"
      :rows="3"
      class="w-full"
      @update:model-value="update"
    />
    <YdInput
      v-else-if="field.type === 'number'"
      :model-value="modelValue ?? ''"
      type="number"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="updateNumber"
    />
    <YdSwitch
      v-else-if="field.type === 'switch'"
      :model-value="!!modelValue"
      @update:model-value="update"
    />
    <YdSelect
      v-else-if="field.type === 'select'"
      :model-value="modelValue ?? ''"
      :options="field.options ?? []"
      :placeholder="field.placeholder"
      class="w-full"
      @update:model-value="update"
    />
    <div v-else-if="field.type === 'color'" class="theme-config-field__color">
      <YdInput
        type="color"
        class="theme-config-field__color-swatch border-0 rounded-none bg-transparent shadow-none"
        input-class="theme-config-field__color-control"
        aria-label="选择主题颜色"
        :model-value="typeof modelValue === 'string' && /^#[0-9a-fA-F]{6}$/.test(modelValue) ? modelValue : '#ffffff'"
        @update:model-value="updateColor"
      />
      <YdInput
        :model-value="modelValue ?? ''"
        :placeholder="field.placeholder || '#ffffff'"
        class="w-full"
        @update:model-value="update"
      />
    </div>
    <template v-else-if="field.type === 'image'">
      <YdImageUpload
        v-model="imageUrls"
        :max="1"
        :width="160"
        :height="96"
        :http-request="uploadThemeImage"
        :after-upload="response => response.url"
      />
      <p class="theme-config-field__hint">
        {{ emptyMeansDefault === false ? '留空会隐藏该图片；可用“恢复默认”撤销覆盖。' : '点击上传或删除图片后记得保存；删除会去掉该图，不再自动填回主题自带图。' }}
      </p>
    </template>
    <template v-else-if="field.type === 'audio'">
      <div class="theme-config-field__audio">
        <YdInput
          :model-value="modelValue ?? ''"
          :placeholder="field.placeholder || '留空使用主题默认音频'"
          class="flex-1 min-w-0"
          @update:model-value="update"
        />
        <YdButton variant="outline" :loading="audioUploading" @click="uploadThemeAudio">
          <YdIcon name="morph:upload" /> 上传
        </YdButton>
        <YdButton v-if="modelValue" variant="ghost" aria-label="清空音频" @click="update('')">
          <YdIcon name="morph:x" />
        </YdButton>
      </div>
      <audio v-if="audioSrc" :src="audioSrc" controls preload="none" class="theme-config-field__audio-player" />
      <p class="theme-config-field__hint">
        {{ emptyMeansDefault === false ? '支持 MP3/OGG/WAV/WebM；留空会关闭这项音效，可用“恢复默认”撤销覆盖。' : '支持 MP3/OGG/WAV/WebM；清空后保存即恢复主题定义的默认行为。' }}
      </p>
    </template>
    <template v-else-if="field.type === 'video'">
      <div class="theme-config-field__audio">
        <YdInput
          :model-value="modelValue ?? ''"
          :placeholder="field.placeholder || '站内视频地址或 https 地址'"
          class="flex-1 min-w-0"
          @update:model-value="update"
        />
        <YdButton variant="outline" :loading="videoUploading" @click="uploadThemeVideo">
          <YdIcon name="morph:upload" /> 上传
        </YdButton>
        <YdButton v-if="modelValue" variant="ghost" aria-label="清空视频" @click="update('')">
          <YdIcon name="morph:x" />
        </YdButton>
      </div>
      <video v-if="videoSrc" :key="videoSrc" :src="videoSrc" controls preload="metadata" playsinline class="theme-config-field__video-player" />
      <p class="theme-config-field__hint">
        支持 MP4（H.264）/WebM/OGG；上传进度取决于文件大小，较大的视频建议先压缩。{{ emptyMeansDefault === false ? '留空会隐藏视频。' : '清空后保存即恢复主题默认。' }}
      </p>
    </template>
    <YdInput
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
            <YdButton size="sm" variant="ghost" :disabled="index === 0" @click="moveItem(index, -1)">
              <YdIcon name="morph:arrow-up" />
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="index === listItems.length - 1" @click="moveItem(index, 1)">
              <YdIcon name="morph:arrow-down" />
            </YdButton>
            <YdButton size="sm" variant="ghost" @click="removeItem(index)">
              <YdIcon name="morph:trash-2" />
            </YdButton>
          </div>
        </div>
        <div class="theme-config-field__list-item-body">
          <ThemeConfigField
            v-for="sub in field.itemFields ?? []"
            :key="sub.key"
            :field="sub"
            :model-value="item[sub.key]"
            :plugin-code="pluginCode"
            :empty-means-default="emptyMeansDefault"
            @update:model-value="updateItemField(index, sub.key, $event)"
          />
        </div>
      </div>
      <YdButton variant="outline" size="sm" @click="createItem">
        <YdIcon name="morph:plus" />
        添加一项
      </YdButton>
    </div>
    <YdInput
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
.theme-config-field__audio {
  display: flex;
  gap: 8px;
  align-items: center;
}

.theme-config-field__video-player {
  display: block;
  width: 100%;
  max-width: 420px;
  max-height: 240px;
  margin-top: 8px;
  background: #000;
  border-radius: 6px;
}

.theme-config-field__audio-player {
  width: 100%;
  height: 36px;
  margin-top: 6px;
}

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
}

.theme-config-field__color-control {
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
