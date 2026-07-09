<script setup lang="ts">
import type { DynamicForm, DynamicFormPayload, DynamicFormStatus } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'
import FormSubmissionPanel from './components/FormSubmissionPanel.vue'

interface DesignerRef {
  getRule: () => unknown[]
  getOption: () => Record<string, unknown>
  setRule: (rules: unknown[]) => void
  setOption: (option: Record<string, unknown>) => void
}

const route = useRoute()
const router = useRouter()
const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const submissionVisible = ref(false)
const designerRef = ref<DesignerRef>()
const selected = ref<DynamicForm | null>(null)

const form = reactive<DynamicFormPayload>({
  name: '',
  code: '',
  description: '',
  schemaJson: '',
  optionJson: '',
  allowAnonymous: true,
  status: 'DRAFT',
})

const pageTitle = computed(() => selected.value ? '编辑动态表单' : '新建动态表单')
const statusOptions: { label: string, value: DynamicFormStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]

watch(() => route.query.id, () => {
  init()
}, { immediate: true })

async function init() {
  const id = routeId()
  if (!id) {
    selected.value = null
    resetForm()
    await nextTick()
    applyDesignerState()
    return
  }

  loading.value = true
  try {
    const res = await apiForm.detail(id)
    selected.value = res.data
    assignForm(res.data)
    await nextTick()
    applyDesignerState()
  }
  finally {
    loading.value = false
  }
}

function routeId() {
  const value = Array.isArray(route.query.id) ? route.query.id[0] : route.query.id
  if (!value) {
    return null
  }
  const id = String(value).trim()
  if (!/^[1-9]\d*$/.test(id)) {
    toast.error('表单 ID 不正确')
    router.replace('/platform/form/designer')
    return null
  }
  return id
}

function resetForm() {
  Object.assign(form, {
    name: '新的动态表单',
    code: `form-${Date.now().toString().slice(-6)}`,
    description: '',
    schemaJson: JSON.stringify(defaultRules(), null, 2),
    optionJson: JSON.stringify(defaultOptions(), null, 2),
    allowAnonymous: true,
    status: 'DRAFT' as DynamicFormStatus,
  })
}

function assignForm(row: DynamicForm) {
  Object.assign(form, {
    name: row.name,
    code: row.code,
    description: row.description || '',
    schemaJson: row.schemaJson || JSON.stringify(defaultRules(), null, 2),
    optionJson: row.optionJson || JSON.stringify(defaultOptions(), null, 2),
    allowAnonymous: row.allowAnonymous !== false,
    status: row.status || 'DRAFT',
  })
}

function applyDesignerState() {
  const designer = designerRef.value
  if (!designer) {
    return
  }
  designer.setRule(parseJsonArray(form.schemaJson, defaultRules()))
  designer.setOption(parseJsonObject(form.optionJson, defaultOptions()))
}

async function saveDesigner() {
  const designer = designerRef.value
  if (!designer) {
    toast.error('表单设计器尚未就绪')
    return
  }
  if (!form.name.trim() || !form.code.trim()) {
    toast.error('请填写表单名称和编码')
    return
  }
  saving.value = true
  try {
    const payload = normalizePayload(designer)
    const res = selected.value
      ? await apiForm.update(selected.value.id, payload)
      : await apiForm.create(payload)
    selected.value = res.data
    Object.assign(form, {
      ...payload,
      status: res.data.status,
    })
    toast.success('表单设计已保存')
    if (!route.query.id) {
      await router.replace({ path: '/platform/form/designer', query: { id: String(res.data.id) } })
    }
  }
  finally {
    saving.value = false
  }
}

function normalizePayload(designer: DesignerRef): DynamicFormPayload {
  return {
    name: form.name.trim(),
    code: form.code.trim(),
    description: form.description?.trim() || undefined,
    schemaJson: JSON.stringify(designer.getRule() || []),
    optionJson: JSON.stringify(designer.getOption() || {}),
    allowAnonymous: form.allowAnonymous,
    status: form.status,
  }
}

function returnToList() {
  router.push('/platform/form')
}

function openSubmissions() {
  if (!selected.value) {
    toast.warning('请先保存表单')
    return
  }
  submissionVisible.value = true
}

function parseJsonArray(value: string | undefined, fallback: unknown[]) {
  try {
    const parsed = value ? JSON.parse(value) : fallback
    return Array.isArray(parsed) ? parsed : fallback
  }
  catch {
    return fallback
  }
}

function parseJsonObject(value: string | undefined, fallback: Record<string, unknown>) {
  try {
    const parsed = value ? JSON.parse(value) : fallback
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback
  }
  catch {
    return fallback
  }
}

function defaultRules() {
  return [
    {
      type: 'input',
      field: 'name',
      title: '姓名',
      props: { placeholder: '请输入姓名' },
      validate: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    },
    {
      type: 'input',
      field: 'phone',
      title: '联系电话',
      props: { placeholder: '请输入联系电话' },
    },
    {
      type: 'textarea',
      field: 'message',
      title: '留言内容',
      props: { placeholder: '请输入内容', maxLength: 500, showWordLimit: true },
    },
  ]
}

function defaultOptions() {
  return {
    form: { layout: 'vertical' },
    submitBtn: false,
    resetBtn: false,
  }
}
</script>

<template>
  <div>
    <FaPageHeader :title="pageTitle" class="mb-0">
      <FaButton variant="outline" @click="returnToList">
        <FaIcon name="i-ri:arrow-left-line" />
        返回列表
      </FaButton>
      <FaButton v-if="selected" v-auth="'platform:form:submission:view'" variant="outline" @click="openSubmissions">
        <FaIcon name="i-ri:inbox-archive-line" />
        提交结果
      </FaButton>
      <FaButton v-auth="'platform:form:edit'" :loading="saving" @click="saveDesigner">
        <FaIcon name="i-ri:save-3-line" />
        保存设计
      </FaButton>
    </FaPageHeader>

    <FaPageMain v-loading="loading">
      <section class="designer-page">
        <a-form :model="form" layout="vertical" class="meta-grid">
          <a-form-item label="表单名称" required>
            <FaInput v-model="form.name" />
          </a-form-item>
          <a-form-item label="表单编码" required>
            <FaInput v-model="form.code" placeholder="form-contact" :disabled="!!selected" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="form.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="公开填写">
            <FaSwitch v-model="form.allowAnonymous" />
          </a-form-item>
          <a-form-item label="描述" class="meta-full">
            <FaTextarea v-model="form.description" rows="3" />
          </a-form-item>
        </a-form>

        <div class="designer-toolbar">
          <span>{{ selected ? `正在编辑：${selected.name}` : '正在创建新表单' }}</span>
          <span>字段与布局在下方设计器中调整，保存后即可用于公开填写和结果收集。</span>
        </div>

        <div class="designer-shell">
          <fc-designer ref="designerRef" height="720px" />
        </div>
      </section>
    </FaPageMain>

    <FaModal v-model="submissionVisible" :title="selected ? `${selected.name} / 提交结果` : '提交结果'" class="sm:max-w-6xl">
      <FormSubmissionPanel :form="selected" :form-id="selected?.id" :form-name="selected?.name" embedded />
    </FaModal>
  </div>
</template>

<style scoped>
.designer-page {
  display: grid;
  gap: 14px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.meta-full {
  grid-column: 1 / -1;
}

.designer-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-2);
}

.designer-toolbar span {
  min-width: 0;
}

.designer-shell {
  min-height: 720px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

@media (max-width: 900px) {
  .meta-grid {
    grid-template-columns: 1fr;
  }

  .designer-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
