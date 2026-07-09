<script setup lang="ts">
import type { FileObject } from '@/api/modules/files'
import type { DynamicForm } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'
import { toBackendAssetUrl } from '@/utils/backend-url'

interface FormCreateApi {
  submit: (success: (data: Record<string, unknown>) => void, fail?: () => void) => Promise<unknown>
  resetFields?: () => void
}

interface UploadFileItem extends Record<string, unknown> {
  file?: File
  name?: string
  response?: unknown
  url?: string
  value?: string
}

interface UploadRequestOption {
  fileItem: UploadFileItem
  data?: Record<string, unknown> | ((fileItem: UploadFileItem) => Record<string, unknown>)
  onError?: (response?: unknown) => void
  onProgress?: (percent: number, event?: ProgressEvent) => void
  onSuccess?: (response?: unknown) => void
}

const props = withDefaults(defineProps<{
  form: DynamicForm
  modelValue?: Record<string, unknown>
  submitted?: boolean
  submitting?: boolean
  readonly?: boolean
  submitText?: string
}>(), {
  submitted: false,
  submitting: false,
  readonly: false,
  submitText: '提交',
})

const emit = defineEmits<{
  invalid: []
  submit: [data: Record<string, unknown>]
}>()

const formApi = shallowRef<FormCreateApi>()
const formModel = ref<Record<string, unknown>>({})

const uploadFields = computed(() => collectUploadFields(parseJsonArray(props.form.schemaJson)))
const rules = computed(() => normalizeRules(parseJsonArray(props.form.schemaJson), props.readonly))
const options = computed(() => {
  const option = parseJsonObject(props.form.optionJson)
  const formOption = option.form && isRecord(option.form) ? option.form : {}
  return {
    ...option,
    form: {
      ...formOption,
      layout: 'vertical',
    },
    resetBtn: false,
    submitBtn: false,
  }
})

watch(() => props.form.id, resetModel, { immediate: true })
watch(() => props.modelValue, resetModel, { deep: true })

defineExpose({
  resetFields: () => {
    formApi.value?.resetFields?.()
    resetModel()
  },
})

async function submit() {
  if (props.readonly || !formApi.value) {
    return
  }
  await formApi.value.submit(
    data => emit('submit', data),
    () => emit('invalid'),
  )
}

function parseJsonArray(value?: string) {
  try {
    const parsed = value ? JSON.parse(value) : []
    return Array.isArray(parsed) ? parsed : []
  }
  catch {
    return []
  }
}

function resetModel() {
  formModel.value = normalizeModelValue(props.modelValue || {})
}

function normalizeModelValue(value: Record<string, unknown>) {
  const target = { ...value }
  if (!props.readonly) {
    return target
  }
  uploadFields.value.forEach((field) => {
    if (field in target) {
      target[field] = normalizeReadonlyUploadValue(target[field])
    }
  })
  return target
}

function normalizeReadonlyUploadValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(item => normalizeReadonlyUploadValue(item))
  }
  return typeof value === 'string' ? toBackendAssetUrl(value) : value
}

function parseJsonObject(value?: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    return isRecord(parsed) ? parsed : {}
  }
  catch {
    return {}
  }
}

function normalizeRules(source: unknown[], readonlyMode: boolean) {
  return source.map(rule => normalizeNode(rule, readonlyMode))
}

function normalizeNode(value: unknown, readonlyMode: boolean): unknown {
  if (Array.isArray(value)) {
    return value.map(item => normalizeNode(item, readonlyMode))
  }
  if (!isRecord(value)) {
    return value
  }

  const rule: Record<string, unknown> = {}
  Object.entries(value).forEach(([key, child]) => {
    rule[key] = normalizeNode(child, readonlyMode)
  })

  const type = typeof rule.type === 'string' ? rule.type.toLowerCase() : ''
  if (isUploadType(type)) {
    rule.props = uploadRuntimeProps(rule.props, readonlyMode)
  }
  else if (readonlyMode && type && supportsDisabled(type)) {
    rule.props = disabledProps(rule.props)
  }
  return rule
}

function uploadRuntimeProps(value: unknown, readonlyMode: boolean) {
  const base = isRecord(value) ? value : {}
  return {
    ...base,
    action: '',
    customRequest: uploadPublicFile,
    data: isRecord(base.data) || typeof base.data === 'function' ? base.data : {},
    disabled: readonlyMode || Boolean(base.disabled),
    modalTitle: base.modalTitle || '预览',
    name: 'file',
    onSuccess: normalizeUploadSuccess,
    responseUrlKey: uploadResponseUrl,
  }
}

function disabledProps(value: unknown) {
  return {
    ...(isRecord(value) ? value : {}),
    disabled: true,
  }
}

function isUploadType(type: string) {
  return type === 'upload' || type === 'fcupload'
}

function collectUploadFields(source: unknown[]) {
  const fields = new Set<string>()
  source.forEach(item => collectUploadField(item, fields))
  return fields
}

function collectUploadField(value: unknown, fields: Set<string>) {
  if (Array.isArray(value)) {
    value.forEach(item => collectUploadField(item, fields))
    return
  }
  if (!isRecord(value)) {
    return
  }
  const type = typeof value.type === 'string' ? value.type.toLowerCase() : ''
  if (isUploadType(type) && typeof value.field === 'string') {
    fields.add(value.field)
  }
  Object.values(value).forEach(item => collectUploadField(item, fields))
}

function supportsDisabled(type: string) {
  return !new Set(['alert', 'button', 'col', 'divider', 'row', 'space', 'span', 'tab', 'tabpane']).has(type)
}

function uploadPublicFile(option: UploadRequestOption) {
  let aborted = false
  void (async () => {
    const file = option.fileItem?.file
    if (!file) {
      option.onError?.(new Error('未选择文件'))
      return
    }

    try {
      option.onProgress?.(0.05)
      const formData = new FormData()
      appendExtraData(formData, option)
      formData.append('file', file)
      const res = await apiForm.uploadPublicFile(props.form.code, formData)
      if (aborted) {
        return
      }
      option.onProgress?.(1)
      option.onSuccess?.({ code: 200, data: res.data, message: '操作成功' })
    }
    catch (error) {
      if (!aborted) {
        option.onError?.(error)
      }
    }
  })()

  return {
    abort() {
      aborted = true
    },
  }
}

function appendExtraData(formData: FormData, option: UploadRequestOption) {
  const data = typeof option.data === 'function' ? option.data(option.fileItem) : option.data
  if (!isRecord(data)) {
    return
  }
  Object.entries(data).forEach(([key, value]) => {
    if (key === 'file' || value == null) {
      return
    }
    formData.append(key, value instanceof Blob ? value : String(value))
  })
}

function uploadResponseUrl(fileItem: UploadFileItem) {
  const uploaded = extractUploadedFile(fileItem.response)
  return toBackendAssetUrl(uploaded?.url)
}

function normalizeUploadSuccess(fileItem: UploadFileItem) {
  const uploaded = extractUploadedFile(fileItem.response)
  if (!uploaded?.url) {
    return
  }
  fileItem.name = uploaded.originalName || fileItem.name
  fileItem.url = toBackendAssetUrl(uploaded.url)
  fileItem.value = uploaded.url
}

function extractUploadedFile(response: unknown): FileObject | undefined {
  if (!isRecord(response)) {
    return undefined
  }
  if (isFileObject(response.data)) {
    return response.data
  }
  if (isFileObject(response)) {
    return response
  }
  return undefined
}

function isFileObject(value: unknown): value is FileObject {
  return isRecord(value) && typeof value.url === 'string'
}

function isRecord(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}
</script>

<template>
  <section class="dynamic-form-renderer">
    <div class="form-title">
      <h1>{{ form.name }}</h1>
      <p v-if="form.description">{{ form.description }}</p>
    </div>

    <div v-if="submitted" class="success-banner">
      已收到你的提交。
    </div>

    <form-create v-model="formModel" v-model:api="formApi" :rule="rules" :option="options" />

    <div v-if="!readonly" class="submit-row">
      <FaButton size="lg" :loading="submitting" @click="submit">
        {{ submitText }}
      </FaButton>
    </div>
  </section>
</template>

<style scoped>
.dynamic-form-renderer {
  display: grid;
  gap: 20px;
}

.form-title {
  display: grid;
  gap: 8px;
  padding-bottom: 12px;
  border-bottom: 1px solid #edf0f5;
}

.form-title h1 {
  margin: 0;
  color: var(--color-text-1, #172033);
  font-size: 26px;
}

.form-title p {
  margin: 0;
  color: var(--color-text-3, #667085);
}

.success-banner {
  padding: 10px 12px;
  border: 1px solid rgba(0, 180, 42, 0.2);
  border-radius: 6px;
  background: rgba(0, 180, 42, 0.08);
  color: #147a2e;
}

.submit-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}
</style>
