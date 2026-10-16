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
const toast = useYdToast()

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
const pageDescription = computed(() => selected.value
  ? `正在编辑「${selected.value.name}」的字段与布局，保存后立即影响公开填写与提交收集。`
  : '填写表单信息后，在设计器中编排字段与布局；保存即可生成可发布的动态表单。')
const statusOptions: { label: string, value: DynamicFormStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]
const statusLabel = computed(() => statusOptions.find(option => option.value === form.status)?.label || form.status)
const codeHint = computed(() => selected.value
  ? `编码已固定为 /forms/${selected.value.code}，公开链接与接口按编码寻址，创建后不可修改。`
  : '用于公开链接 /forms/{编码}，创建保存后不可修改，请使用小写字母、数字和连字符。')

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
    <YdPageHeader :title="pageTitle" :description="pageDescription" class="mb-0">
      <YdButton variant="outline" @click="returnToList">
        <YdIcon name="morph:arrow-left" />
        返回列表
      </YdButton>
      <YdButton v-if="selected" v-auth="'platform:form:submission:view'" variant="outline" @click="openSubmissions">
        <YdIcon name="morph:archive" />
        提交结果
      </YdButton>
      <YdButton v-auth="'platform:form:edit'" :loading="saving" @click="saveDesigner">
        <YdIcon name="morph:save" />
        保存设计
      </YdButton>
    </YdPageHeader>

    <YdLoadingOverlay :loading="loading">
      <YdPageMain>
        <section class="designer-page">
          <div class="designer-meta">
            <div class="designer-meta__head">
              <div class="designer-meta__intro">
                <h2>表单信息</h2>
                <p>名称与编码用于识别和公开寻址，状态决定公开链接能否填写。</p>
              </div>
              <div class="designer-meta__identity">
                <YdTag>{{ statusLabel }}</YdTag>
                <code v-if="selected">{{ selected.code }}</code>
              </div>
            </div>

            <YdForm :model="form" layout="vertical" class="meta-grid">
              <YdFormItem label="表单名称" field="name" required>
                <YdInput v-model="form.name" placeholder="面向填写者展示的名称" />
              </YdFormItem>
              <YdFormItem label="表单编码" field="code" required :extra="codeHint">
                <YdInput v-model="form.code" placeholder="form-contact" :disabled="!!selected" />
              </YdFormItem>
              <YdFormItem label="状态" field="status" extra="草稿仅在设计器中可见；发布后公开链接才能接收提交。">
                <YdSelect v-model="form.status" :options="statusOptions" />
              </YdFormItem>
              <YdFormItem label="公开填写" field="allowAnonymous" extra="开启后未登录访客也可以提交。">
                <YdSwitch v-model="form.allowAnonymous" />
              </YdFormItem>
              <YdFormItem label="描述" field="description" class="meta-full" extra="选填，用于说明表单用途或填写须知。">
                <YdTextarea v-model="form.description" rows="3" placeholder="简要说明表单用途、面向人群或填写须知" />
              </YdFormItem>
            </YdForm>
          </div>

          <div class="designer-canvas">
            <div class="designer-canvas__head">
              <div>
                <h2>表单设计器</h2>
                <p>从左侧拖入字段组件，在画布中调整顺序与布局，右侧面板修改选中组件的属性。</p>
              </div>
              <span class="designer-canvas__hint">
                {{ selected ? `正在编辑：${selected.name}` : '正在创建新表单，保存后生成公开链接' }}
              </span>
            </div>
            <div class="designer-shell">
              <fc-designer ref="designerRef" height="100%" />
            </div>
          </div>
        </section>
      </YdPageMain>
    </YdLoadingOverlay>

    <YdModal v-model="submissionVisible" :title="selected ? `${selected.name} / 提交结果` : '提交结果'" class="sm:max-w-6xl">
      <FormSubmissionPanel :form="selected" :form-id="selected?.id" :form-name="selected?.name" embedded />
    </YdModal>
  </div>
</template>

<style scoped>
.designer-page {
  display: grid;
  gap: var(--yd-content-gap, 16px);
}

/* 元信息只用一个平面容器，靠标题、分组与说明文本建立层级，不再套多层卡片。 */
.designer-meta {
  display: grid;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-bg-2);
}

.designer-meta__head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.designer-meta__intro h2,
.designer-canvas__head h2 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 16px;
  font-weight: 600;
}

.designer-meta__intro p,
.designer-canvas__head p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.designer-meta__identity {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.designer-meta__identity code {
  padding: 3px 8px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px 16px;
}

.meta-full {
  grid-column: 1 / -1;
}

.designer-canvas {
  display: grid;
  gap: 12px;
}

.designer-canvas__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.designer-canvas__hint {
  color: var(--color-text-3);
  font-size: 12px;
  overflow-wrap: anywhere;
}

/* 画布按视口给足高度，短屏仍保留可用设计区，长屏不再限制在固定 720px。 */
.designer-shell {
  height: clamp(640px, calc(100dvh - 360px), 1080px);
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-bg-2);
}

@media (max-width: 900px) {
  .meta-grid {
    grid-template-columns: 1fr;
  }

  .designer-shell {
    height: clamp(560px, calc(100dvh - 320px), 900px);
  }
}
</style>
