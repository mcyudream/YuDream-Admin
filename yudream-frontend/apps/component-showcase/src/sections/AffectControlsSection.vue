<script setup lang="ts">
import type { YdComboboxGroup, YdComboboxOption, YdPromptItem, YdSegmentedOption, YdStepItem } from '@yudream/components'
import {
  useYdFilePicker,
  useYdToast,
  YdAction,
  YdAlert,
  YdAvatar,
  YdButton,
  YdCheckbox,
  YdCombobox,
  YdCopyButton,
  YdDropdown,
  YdEmpty,
  YdIcon,
  YdInput,
  YdInputOTP,
  YdKbd,
  YdKbdGroup,
  YdNumberField,
  YdPopconfirm,
  YdPopover,
  YdProgress,
  YdPrompts,
  YdRangePicker,
  YdResult,
  YdSegmented,
  YdSelect,
  YdSteps,
  YdSwitch,
  YdTag,
  YdTimeAgo,
} from '@yudream/components'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

/**
 * AffectUI 控件画廊：导航 / 输入 / 反馈三组共 18 个控件的本地交互演示。
 * 全部数据为组件内本地状态，不发起网络请求；交互控件一律来自 @yudream/components 公开 Yd* / useYd* 入口。
 * 轻提示依赖展示壳根部的全局 YdToast 组件（component-showcase 的 App.vue 已挂载）。
 */

const toast = useYdToast()

// ============================================================
// 分组导航
// ============================================================

type AffectGroup = 'all' | 'navigation' | 'inputs' | 'feedback'

const activeGroup = ref<AffectGroup>('all')
const groupTabs: YdSegmentedOption<AffectGroup>[] = [
  { label: '全部', value: 'all' },
  { label: '导航', value: 'navigation' },
  { label: '输入', value: 'inputs' },
  { label: '反馈', value: 'feedback' },
]
const groupCounts: Record<Exclude<AffectGroup, 'all'>, number> = {
  navigation: 4,
  inputs: 9,
  feedback: 5,
}

function showGroup(group: Exclude<AffectGroup, 'all'>) {
  return activeGroup.value === 'all' || activeGroup.value === group
}

// ============================================================
// 导航 1：命令菜单（键盘搜索 + 上下选择 + Enter 执行）
// ============================================================

interface CommandEntry {
  id: string
  label: string
  icon: string
  group: '导航' | '操作' | '帮助'
  shortcut?: string[]
}

const commandEntries: CommandEntry[] = [
  { id: 'cmd-workspace', label: '前往：控件画廊', icon: 'morph:house', group: '导航', shortcut: ['G', 'O'] },
  { id: 'cmd-integrations', label: '前往：集成中心', icon: 'morph:blocks', group: '导航', shortcut: ['G', 'I'] },
  { id: 'cmd-billing', label: '前往：账单周期', icon: 'morph:file-text', group: '导航', shortcut: ['G', 'B'] },
  { id: 'cmd-new-task', label: '新建任务', icon: 'morph:plus', group: '操作', shortcut: ['N'] },
  { id: 'cmd-invite', label: '邀请成员', icon: 'morph:user-plus', group: '操作', shortcut: ['Shift', 'I'] },
  { id: 'cmd-export', label: '导出当前报表', icon: 'morph:download', group: '操作', shortcut: ['Shift', 'E'] },
  { id: 'cmd-docs', label: '查看使用文档', icon: 'morph:book-open', group: '帮助', shortcut: ['?'] },
  { id: 'cmd-support', label: '联系平台支持', icon: 'morph:message-circle', group: '帮助' },
]

const commandOpen = ref(false)
const commandKeyword = ref('')
const commandActive = ref(0)

const commandGroups = computed(() => {
  const keyword = commandKeyword.value.trim().toLowerCase()
  let cursor = 0
  return (['导航', '操作', '帮助'] as const)
    .map(label => ({
      label,
      entries: commandEntries
        .filter(entry => entry.group === label && (!keyword || entry.label.toLowerCase().includes(keyword)))
        .map(entry => ({ entry, index: cursor++ })),
    }))
    .filter(group => group.entries.length > 0)
})

const commandFlat = computed(() => commandGroups.value.flatMap(group => group.entries))

const commandInput = ref<HTMLInputElement | null>(null)

function setCommandInput(instance: unknown) {
  const exposed = instance as { ref?: HTMLInputElement } | null
  commandInput.value = exposed?.ref ?? null
}

watch(commandOpen, async (open) => {
  commandActive.value = 0
  if (open) {
    await nextTick()
    commandInput.value?.focus()
  }
})

watch(commandKeyword, () => {
  commandActive.value = 0
})

function moveCommand(delta: number) {
  const total = commandFlat.value.length
  if (!total) {
    return
  }
  commandActive.value = (commandActive.value + delta + total) % total
}

function runCommand(entry: CommandEntry) {
  toast(`已执行「${entry.label}」（本地演示）`)
  commandOpen.value = false
  commandKeyword.value = ''
}

function runActiveCommand() {
  const target = commandFlat.value[commandActive.value]
  if (target) {
    runCommand(target.entry)
  }
}

function onCommandInputKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    moveCommand(1)
  }
  else if (event.key === 'ArrowUp') {
    event.preventDefault()
    moveCommand(-1)
  }
  else if (event.key === 'Enter') {
    event.preventDefault()
    runActiveCommand()
  }
  else if (event.key === 'Escape') {
    commandOpen.value = false
  }
}

function onGlobalKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    commandOpen.value = !commandOpen.value
  }
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})

// ============================================================
// 导航 2：分段控制（排版密度联动预览）
// ============================================================

type Density = 'compact' | 'standard' | 'relaxed'

const density = ref<Density>('standard')
const densityOptions: YdSegmentedOption<Density>[] = [
  { label: '紧凑', value: 'compact' },
  { label: '标准', value: 'standard' },
  { label: '宽松', value: 'relaxed' },
]
const densityMeta: Record<Density, { gap: string, size: string, lines: number, note: string }> = {
  compact: { gap: '4px', size: '12.5px', lines: 5, note: '适合表格与密集列表' },
  standard: { gap: '8px', size: '13.5px', lines: 4, note: '默认排版节奏' },
  relaxed: { gap: '14px', size: '14.5px', lines: 3, note: '适合阅读型页面' },
}
const densityCurrent = computed(() => densityMeta[density.value])
const densityLastChange = ref('当前：标准排版')

function onDensityChange(value: Density) {
  densityLastChange.value = `change 事件：切换为${densityOptions.find(option => option.value === value)?.label ?? value}排版`
}

// ============================================================
// 导航 3：集成选择器（搜索 + 分类 + 选中确认）
// ============================================================

interface PickerIntegration {
  id: string
  name: string
  description: string
  icon: string
  category: '数据' | '通信' | '存储'
  connected: boolean
}

const pickerIntegrations: PickerIntegration[] = [
  { id: 'pg-mongo', name: 'MongoDB', description: '业务主数据库连接', icon: 'morph:database', category: '数据', connected: true },
  { id: 'pg-redis', name: 'Redis', description: '缓存与会话存储', icon: 'morph:zap', category: '数据', connected: true },
  { id: 'pg-git', name: '代码仓库', description: '拉取构建与变更记录', icon: 'morph:git-branch', category: '数据', connected: false },
  { id: 'pg-smtp', name: '邮件服务', description: '通知与验证码投递', icon: 'morph:mail', category: '通信', connected: false },
  { id: 'pg-im', name: '即时消息', description: '审批与告警群推送', icon: 'morph:message-circle', category: '通信', connected: false },
  { id: 'pg-s3', name: '对象存储', description: '附件与静态资源', icon: 'morph:cloud', category: '存储', connected: false },
]

type PickerCategory = 'all' | '数据' | '通信' | '存储'

const pickerCategory = ref<PickerCategory>('all')
const pickerCategoryOptions: YdSegmentedOption<PickerCategory>[] = [
  { label: '全部', value: 'all' },
  { label: '数据', value: '数据' },
  { label: '通信', value: '通信' },
  { label: '存储', value: '存储' },
]
const pickerKeyword = ref('')
const pickerSelectedId = ref('pg-mongo')

const pickerFiltered = computed(() => {
  const keyword = pickerKeyword.value.trim().toLowerCase()
  return pickerIntegrations.filter((item) => {
    if (pickerCategory.value !== 'all' && item.category !== pickerCategory.value) {
      return false
    }
    return !keyword || `${item.name} ${item.description}`.toLowerCase().includes(keyword)
  })
})

const pickerSelected = computed(() => pickerIntegrations.find(item => item.id === pickerSelectedId.value))

function confirmPicker() {
  if (!pickerSelected.value) {
    return
  }
  toast.success(`已选择「${pickerSelected.value.name}」，进入接入向导（本地演示）`)
}

// ============================================================
// 导航 4：个人菜单（下拉 + 勾选项 + 危险动作）
// ============================================================

const profileFocus = ref(false)
const profileMute = ref(true)

const profileItems = computed(() => [
  [
    { label: '个人资料', icon: 'morph:user', handle: () => toast('打开个人资料（本地演示）') },
    { label: '偏好设置', icon: 'morph:settings', handle: () => toast('打开偏好设置（本地演示）') },
  ],
  [
    { label: '专注模式', type: 'checkbox' as const, checked: profileFocus.value, handle: (checked?: boolean) => {
      profileFocus.value = checked === true
      toast(checked ? '专注模式已开启（本地演示）' : '专注模式已关闭（本地演示）')
    } },
    { label: '通知免打扰', type: 'checkbox' as const, checked: profileMute.value, handle: (checked?: boolean) => {
      profileMute.value = checked === true
      toast(checked ? '通知免打扰已开启（本地演示）' : '通知免打扰已关闭（本地演示）')
    } },
  ],
  [
    { label: '退出登录', icon: 'morph:log-out', variant: 'destructive' as const, handle: () => toast.error('演示环境不执行退出（本地演示）') },
  ],
])

// ============================================================
// 输入 1：组合框（分组单选搜索）
// ============================================================

const comboboxValue = ref<string | number>()
const comboboxOptions: (YdComboboxOption | YdComboboxGroup)[] = [
  {
    label: '研发部',
    options: [
      { label: '林舟 · 后端', value: 'u-1001' },
      { label: '陈念 · 前端', value: 'u-1002' },
    ],
  },
  {
    label: '设计部',
    options: [
      { label: '周衡 · 视觉', value: 'u-1003' },
      { label: '苏离 · 交互（休假）', value: 'u-1004', disabled: true },
    ],
  },
]

const comboboxSelectedLabel = computed(() => {
  for (const group of comboboxOptions) {
    if ('options' in group) {
      const hit = group.options.find(option => option.value === comboboxValue.value)
      if (hit) {
        return hit.label
      }
    }
    else if (group.value === comboboxValue.value) {
      return group.label
    }
  }
  return ''
})

// ============================================================
// 输入 2：日期范围（选择后本地换算天数）
// ============================================================

const stayRange = ref<(string | number)[]>()

const stayNights = computed(() => {
  const value = stayRange.value
  if (!value || value.length !== 2) {
    return null
  }
  const start = new Date(`${String(value[0])}T00:00:00`)
  const end = new Date(`${String(value[1])}T00:00:00`)
  const diff = Math.round((end.getTime() - start.getTime()) / 86_400_000)
  return Number.isFinite(diff) && diff >= 0 ? diff : null
})

// ============================================================
// 输入 3：拖拽上传区（本地进度模拟 + 失败重试，无网络）
// ============================================================

interface DropItem {
  id: string
  name: string
  size: number
  status: 'uploading' | 'success' | 'error'
  progress: number
  error?: string
}

const DROP_MAX_SIZE = 8 * 1024 * 1024
const DROP_FAIL_SIZE = 5 * 1024 * 1024

const dropItems = ref<DropItem[]>([])
const dropDragover = ref(false)
const dropPicker = useYdFilePicker()
const dropTimers = new Map<string, number>()

function validateDropFile(file: File): string | undefined {
  if (file.size > DROP_MAX_SIZE) {
    return '超过 8 MB 演示上限'
  }
  if (/\.(?:exe|bat|sh)$/i.test(file.name)) {
    return '演示环境不接受可执行文件'
  }
  return undefined
}

function startDropSimulate(id: string, retry = false) {
  const item = dropItems.value.find(entry => entry.id === id)
  if (!item) {
    return
  }
  const pendingTimer = dropTimers.get(id)
  if (pendingTimer) {
    window.clearInterval(pendingTimer)
    dropTimers.delete(id)
  }
  // 大于 5 MB 的文件首次必失败一次，用于演示失败与重试；重试必定成功。
  const willFail = !retry && item.size > DROP_FAIL_SIZE
  item.status = 'uploading'
  item.error = undefined
  if (retry) {
    item.progress = 0
  }
  const timer = window.setInterval(() => {
    item.progress = Math.min(100, item.progress + 12 + Math.round(Math.random() * 14))
    if (willFail && item.progress >= 62) {
      window.clearInterval(timer)
      dropTimers.delete(id)
      item.status = 'error'
      item.error = '模拟网络中断，可重试'
      return
    }
    if (item.progress >= 100) {
      window.clearInterval(timer)
      dropTimers.delete(id)
      item.status = 'success'
    }
  }, 220)
  dropTimers.set(id, timer)
}

function acceptDropFiles(files: File[]) {
  for (const file of files) {
    const item: DropItem = {
      id: `drop-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      name: file.name,
      size: file.size,
      status: 'uploading',
      progress: 0,
    }
    const problem = validateDropFile(file)
    if (problem) {
      item.status = 'error'
      item.error = problem
    }
    dropItems.value.push(item)
    if (!problem) {
      startDropSimulate(item.id)
    }
  }
}

function onDropZoneDrop(event: DragEvent) {
  dropDragover.value = false
  const files = Array.from(event.dataTransfer?.files ?? [])
  if (files.length) {
    acceptDropFiles(files)
  }
}

function openDropPicker() {
  dropPicker.open({ multiple: true })
    .then((files) => {
      if (files.length) {
        acceptDropFiles(files)
      }
    })
    .catch(() => {})
}

function retryDropItem(id: string) {
  startDropSimulate(id, true)
}

function removeDropItem(id: string) {
  const timer = dropTimers.get(id)
  if (timer) {
    window.clearInterval(timer)
    dropTimers.delete(id)
  }
  dropItems.value = dropItems.value.filter(entry => entry.id !== id)
}

function formatDropSize(size: number) {
  if (size >= 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(1)} MB`
  }
  return `${Math.max(1, Math.round(size / 1024))} KB`
}

onBeforeUnmount(() => {
  dropTimers.forEach((timer) => {
    window.clearInterval(timer)
  })
  dropTimers.clear()
  dropPicker.dispose()
})

// ============================================================
// 输入 4：一次性密码（粘贴模拟 + 校验错误/成功 + 重发倒计时）
// ============================================================

const OTP_EXPECTED = '682915'
const OTP_LENGTH = 6

const otpValue = ref('')
const otpState = ref<'idle' | 'error' | 'verified'>('idle')
const otpCooldown = ref(0)
let otpCooldownTimer: number | undefined

watch(otpValue, () => {
  if (otpState.value === 'error') {
    otpState.value = 'idle'
  }
})

function verifyOtp(code = otpValue.value) {
  if (code.length < OTP_LENGTH) {
    return
  }
  if (code === OTP_EXPECTED) {
    otpState.value = 'verified'
    toast.success('验证码校验通过（本地演示）')
  }
  else {
    otpState.value = 'error'
    toast.error('验证码不正确，请重新输入（本地演示）')
  }
}

function pasteOtp() {
  otpState.value = 'idle'
  otpValue.value = OTP_EXPECTED
  verifyOtp()
}

function clearOtp() {
  otpState.value = 'idle'
  otpValue.value = ''
}

function resendOtp() {
  if (otpCooldown.value > 0) {
    return
  }
  otpCooldown.value = 30
  otpCooldownTimer = window.setInterval(() => {
    otpCooldown.value -= 1
    if (otpCooldown.value <= 0 && otpCooldownTimer) {
      window.clearInterval(otpCooldownTimer)
      otpCooldownTimer = undefined
    }
  }, 1000)
  toast(`已重新发送，演示验证码为 ${OTP_EXPECTED}（本地演示）`)
}

onBeforeUnmount(() => {
  if (otpCooldownTimer) {
    window.clearInterval(otpCooldownTimer)
  }
})

// ============================================================
// 输入 5：分步表单（逐段校验 + 错误回显 + 完成态）
// ============================================================

const stepperStep = ref(0)
const stepperSubmitting = ref(false)
const stepperDone = ref(false)
const stepperForm = reactive({
  name: '',
  email: '',
  role: 'developer',
  seats: 5,
  notice: true,
  agree: false,
})
const stepperErrors = reactive<{ name?: string, email?: string, agree?: string }>({})

const stepperRoleOptions = [
  { label: '开发者', value: 'developer' },
  { label: '产品', value: 'product' },
  { label: '运营', value: 'ops' },
]

const stepperItems = computed<YdStepItem[]>(() => [
  {
    id: 'stepper-base',
    title: '基本信息',
    description: '名称与邮箱',
    status: stepperErrors.name || stepperErrors.email ? 'error' : undefined,
  },
  { id: 'stepper-pref', title: '团队偏好', description: '角色与席位' },
  { id: 'stepper-confirm', title: '确认创建', description: '核对并提交' },
])

const stepperRoleLabel = computed(() => stepperRoleOptions.find(option => option.value === stepperForm.role)?.label ?? stepperForm.role)

function isValidEmail(value: string) {
  const at = value.indexOf('@')
  if (at <= 0 || at !== value.lastIndexOf('@') || at === value.length - 1) {
    return false
  }
  const domain = value.slice(at + 1)
  const dot = domain.lastIndexOf('.')
  return dot > 0 && dot < domain.length - 1
}

function validateStepperStep(index: number) {
  if (index === 0) {
    stepperErrors.name = stepperForm.name.trim().length >= 2 ? undefined : '请输入至少 2 个字符的名称'
    stepperErrors.email = isValidEmail(stepperForm.email.trim()) ? undefined : '邮箱格式不正确'
    return !stepperErrors.name && !stepperErrors.email
  }
  if (index === 2) {
    stepperErrors.agree = stepperForm.agree ? undefined : '请先阅读并同意协作条款'
    return !stepperErrors.agree
  }
  return true
}

function stepperPrev() {
  if (stepperStep.value > 0) {
    stepperStep.value -= 1
  }
}

function stepperNext() {
  if (!validateStepperStep(stepperStep.value)) {
    return
  }
  if (stepperStep.value < 2) {
    stepperStep.value += 1
    return
  }
  stepperSubmitting.value = true
  window.setTimeout(() => {
    stepperSubmitting.value = false
    stepperDone.value = true
    toast.success('工作区创建成功（本地演示）')
  }, 900)
}

function resetStepper() {
  stepperForm.name = ''
  stepperForm.email = ''
  stepperForm.role = 'developer'
  stepperForm.seats = 5
  stepperForm.notice = true
  stepperForm.agree = false
  stepperErrors.name = undefined
  stepperErrors.email = undefined
  stepperErrors.agree = undefined
  stepperStep.value = 0
  stepperDone.value = false
}

// ============================================================
// 输入 6：集成设置（地址校验 + 密钥留空不覆盖 + 时区 + 保存）
// ============================================================

const integrationSettings = reactive({
  endpoint: 'https://api.example-yudream.com',
  secret: '',
  timezone: 'Asia/Shanghai',
  enabled: true,
})
const integrationSecretSet = ref(true)
const integrationProbing = ref(false)
const integrationProbeState = ref<'idle' | 'ok' | 'error'>('idle')
let integrationProbeTimer: number | undefined

const timezoneOptions = [
  { label: 'UTC+8 北京', value: 'Asia/Shanghai' },
  { label: 'UTC+9 东京', value: 'Asia/Tokyo' },
  { label: 'UTC+0 伦敦', value: 'Europe/London' },
  { label: 'UTC-8 太平洋', value: 'America/Los_Angeles' },
]

const integrationEndpointInvalid = computed(() => {
  try {
    return new URL(integrationSettings.endpoint).protocol !== 'https:'
  }
  catch {
    return true
  }
})

const integrationTimezoneLabel = computed(() => timezoneOptions.find(option => option.value === integrationSettings.timezone)?.label ?? integrationSettings.timezone)

function probeIntegration() {
  if (integrationEndpointInvalid.value) {
    integrationProbeState.value = 'error'
    toast.error('地址无效或不是 https，无法探测（本地演示）')
    return
  }
  integrationProbeState.value = 'idle'
  integrationProbing.value = true
  integrationProbeTimer = window.setTimeout(() => {
    integrationProbing.value = false
    integrationProbeState.value = 'ok'
    toast.success('探测成功：服务可用（本地模拟，无真实网络）')
  }, 900)
}

function saveIntegration() {
  if (integrationEndpointInvalid.value) {
    toast.error('请先修正服务地址（https）再保存（本地演示）')
    return
  }
  const secretChanged = integrationSettings.secret.trim().length > 0
  if (secretChanged) {
    integrationSecretSet.value = true
    integrationSettings.secret = ''
  }
  toast.success(secretChanged
    ? '设置已保存，密钥已更新（本地演示）'
    : '设置已保存，密钥留空保持不变（本地演示）')
}

onBeforeUnmount(() => {
  if (integrationProbeTimer) {
    window.clearTimeout(integrationProbeTimer)
  }
})

// ============================================================
// 输入 7：提示词库（模板变量提取与组装）
// ============================================================

interface PromptTemplate {
  key: string
  label: string
  description: string
  icon: string
  value: string
}

const promptTemplates: PromptTemplate[] = [
  { key: 'release', label: '发布说明', description: '按模块整理本周变更', icon: 'morph:list-checks', value: '请根据 {{模块}} 的变更记录，起草一段面向开发者的发布说明，语气{{语气}}。' },
  { key: 'review', label: '代码评审', description: '聚焦边界与分层', icon: 'morph:file-code', value: '请评审以下改动：{{改动主题}}，重点关注{{关注点}}。' },
  { key: 'report', label: '周报摘要', description: '汇总进展与风险', icon: 'morph:pen', value: '把以下内容整理成周报：{{工作项}}，并补充{{风险}}的应对建议。' },
]

const activePromptKey = ref('release')
const activePrompt = computed(() => promptTemplates.find(template => template.key === activePromptKey.value) ?? promptTemplates[0]!)
const promptValues = reactive<Record<string, string>>({})

const promptSegments = computed(() => {
  const segments: { type: 'text' | 'var', value: string }[] = []
  const source = activePrompt.value.value
  const pattern = /\{\{([^{}]+)\}\}/g
  let last = 0
  for (const match of source.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > last) {
      segments.push({ type: 'text', value: source.slice(last, index) })
    }
    const name = match[1]?.trim()
    if (name) {
      segments.push({ type: 'var', value: name })
    }
    last = index + match[0].length
  }
  if (last < source.length) {
    segments.push({ type: 'text', value: source.slice(last) })
  }
  return segments
})

const promptVarNames = computed(() => promptSegments.value
  .filter(segment => segment.type === 'var')
  .map(segment => segment.value))

function promptFilled(name: string) {
  return (promptValues[name] ?? '').trim().length > 0
}

const promptAssembled = computed(() => promptSegments.value
  .map(segment => segment.type === 'var'
    ? (promptValues[segment.value]?.trim() || `【${segment.value}】`)
    : segment.value)
  .join(''))

const promptItems = computed<YdPromptItem[]>(() => promptTemplates.map(template => ({
  key: template.key,
  label: template.label,
  description: template.description,
  icon: template.icon,
})))

function selectPrompt(item: YdPromptItem) {
  activePromptKey.value = String(item.key)
  toast(`已载入模板「${item.label}」，补充变量后即可复制`)
}

// ============================================================
// 输入 8：筛选菜单（分组勾选 + 可关闭 chips + 本地列表过滤）
// ============================================================

interface FilterTask {
  id: string
  title: string
  owner: '林舟' | '陈念' | '周衡'
  status: '进行中' | '已完成' | '已归档'
  tag: '设计' | '研发' | '文档'
}

const filterTasks: FilterTask[] = [
  { id: 'ft-1', title: '首页视觉稿定稿', owner: '周衡', status: '进行中', tag: '设计' },
  { id: 'ft-2', title: '权限接口联调', owner: '林舟', status: '进行中', tag: '研发' },
  { id: 'ft-3', title: '插件接入指南', owner: '陈念', status: '已完成', tag: '文档' },
  { id: 'ft-4', title: '日历组件重构', owner: '林舟', status: '进行中', tag: '研发' },
  { id: 'ft-5', title: '旧版设置页归档', owner: '陈念', status: '已归档', tag: '文档' },
  { id: 'ft-6', title: '图表配色规范', owner: '周衡', status: '已完成', tag: '设计' },
]

const filterOwners = ref<string[]>([])
const filterStatuses = ref<string[]>([])
const filterTags = ref<string[]>([])
const filterMenuOpen = ref(false)

const filterOwnerOptions = ['林舟', '陈念', '周衡'] as const
const filterStatusOptions = ['进行中', '已完成', '已归档'] as const
const filterTagOptions = ['设计', '研发', '文档'] as const

function toggleInList(list: string[], value: string, checked: boolean) {
  const index = list.indexOf(value)
  if (checked && index === -1) {
    list.push(value)
  }
  else if (!checked && index !== -1) {
    list.splice(index, 1)
  }
}

const filterChips = computed(() => [
  ...filterStatuses.value.map(value => ({
    key: `status-${value}`,
    label: `状态：${value}`,
    remove: () => {
      filterStatuses.value = filterStatuses.value.filter(entry => entry !== value)
    },
  })),
  ...filterOwners.value.map(value => ({
    key: `owner-${value}`,
    label: `负责人：${value}`,
    remove: () => {
      filterOwners.value = filterOwners.value.filter(entry => entry !== value)
    },
  })),
  ...filterTags.value.map(value => ({
    key: `tag-${value}`,
    label: `标签：${value}`,
    remove: () => {
      filterTags.value = filterTags.value.filter(entry => entry !== value)
    },
  })),
])

const filteredTasks = computed(() => filterTasks.filter((task) => {
  if (filterStatuses.value.length && !filterStatuses.value.includes(task.status)) {
    return false
  }
  if (filterOwners.value.length && !filterOwners.value.includes(task.owner)) {
    return false
  }
  if (filterTags.value.length && !filterTags.value.includes(task.tag)) {
    return false
  }
  return true
}))

function clearTaskFilters() {
  filterStatuses.value = []
  filterOwners.value = []
  filterTags.value = []
}

// ============================================================
// 输入 9：账单周期（方案选择 + 月/年切换 + 分期合计）
// ============================================================

type BillingCycle = 'monthly' | 'yearly'

interface BillingPlan {
  id: string
  name: string
  monthly: number
  yearly: number
  note: string
}

const billingPlans: BillingPlan[] = [
  { id: 'starter', name: '入门版', monthly: 79, yearly: 790, note: '5 个席位' },
  { id: 'pro', name: '专业版', monthly: 199, yearly: 1990, note: '20 个席位' },
  { id: 'flagship', name: '旗舰版', monthly: 499, yearly: 4990, note: '不限席位' },
]

const billingCycle = ref<BillingCycle>('monthly')
const billingCycleOptions: YdSegmentedOption<BillingCycle>[] = [
  { label: '月付', value: 'monthly' },
  { label: '年付', value: 'yearly' },
]
const billingPlanId = ref('pro')

const billingPlan = computed(() => billingPlans.find(plan => plan.id === billingPlanId.value) ?? billingPlans[0]!)
const billingUnitPrice = computed(() => billingCycle.value === 'monthly'
  ? billingPlan.value.monthly
  : Math.round(billingPlan.value.yearly / 12))
const billingTotal = computed(() => billingCycle.value === 'monthly'
  ? billingPlan.value.monthly
  : billingPlan.value.yearly)
const billingSaving = computed(() => billingCycle.value === 'yearly'
  ? billingPlan.value.monthly * 12 - billingPlan.value.yearly
  : 0)

const billingPhases = computed(() => {
  const start = new Date('2026-11-01T00:00:00')
  const stepMonths = billingCycle.value === 'monthly' ? 1 : 12
  const amount = billingCycle.value === 'monthly' ? billingPlan.value.monthly : billingPlan.value.yearly
  return Array.from({ length: 4 }, (_, index) => {
    const date = new Date(start)
    date.setMonth(date.getMonth() + index * stepMonths)
    const month = String(date.getMonth() + 1).padStart(2, '0')
    const day = String(date.getDate()).padStart(2, '0')
    return {
      index: index + 1,
      date: `${date.getFullYear()}-${month}-${day}`,
      amount,
    }
  })
})

const billingPhasesTotal = computed(() => billingPhases.value
  .reduce((sum, phase) => sum + phase.amount, 0))

function formatBillingAmount(amount: number) {
  return `¥${amount.toLocaleString('zh-CN')}`
}

function confirmBilling() {
  toast.success(`已选择${billingPlan.value.name}·${billingCycle.value === 'monthly' ? '月付' : '年付'}（本地演示）`)
}

// ============================================================
// 反馈 1：轻提示（toast.promise + 常规变体）
// ============================================================

const toastFail = ref(false)

function deployWithToast() {
  const job = new Promise<string>((resolve, reject) => {
    window.setTimeout(() => {
      if (toastFail.value) {
        reject(new Error('演示环境刻意失败'))
        return
      }
      resolve('v2.7.0')
    }, 1400)
  })
  toast.promise(job, {
    loading: '正在发布新版本…',
    success: (version: string) => `发布完成：${version}`,
    error: (reason: unknown) => `发布失败：${reason instanceof Error ? reason.message : '未知错误'}`,
  })
}

// ============================================================
// 反馈 2：进度追踪（分阶段推进 + 总进度 + 暂停/重置）
// ============================================================

const trackerPhases = ['准备环境', '安装依赖', '构建产物', '部署上线']
const trackerCurrent = ref(0)
const trackerPercent = ref(0)
const trackerRunning = ref(false)
let trackerTimer: number | undefined

const trackerDone = computed(() => trackerCurrent.value >= trackerPhases.length)
const trackerOverall = computed(() => trackerDone.value
  ? 100
  : Math.round(((trackerCurrent.value + trackerPercent.value / 100) / trackerPhases.length) * 100))

const trackerItems = computed<YdStepItem[]>(() => trackerPhases.map((title, index) => ({
  id: `tracker-${index}`,
  title,
  description: index === trackerCurrent.value && !trackerDone.value
    ? (trackerRunning.value ? '执行中' : '已暂停')
    : undefined,
})))

function stopTracker() {
  if (trackerTimer) {
    window.clearInterval(trackerTimer)
    trackerTimer = undefined
  }
}

function startTracker() {
  stopTracker()
  trackerTimer = window.setInterval(() => {
    trackerPercent.value += 6 + Math.round(Math.random() * 8)
    if (trackerPercent.value >= 100) {
      trackerPercent.value = 0
      trackerCurrent.value += 1
      if (trackerDone.value) {
        stopTracker()
        trackerRunning.value = false
        toast.success('流水线执行完成（本地模拟）')
      }
    }
  }, 240)
}

function toggleTracker() {
  if (trackerDone.value) {
    return
  }
  trackerRunning.value = !trackerRunning.value
  if (trackerRunning.value) {
    startTracker()
  }
  else {
    stopTracker()
  }
}

function resetTracker() {
  stopTracker()
  trackerRunning.value = false
  trackerCurrent.value = 0
  trackerPercent.value = 0
}

onBeforeUnmount(stopTracker)

// ============================================================
// 反馈 3：配置向导（完成清单 + 进度 + 完成态）
// ============================================================

const guideItems = ref([
  { id: 'guide-1', label: '创建第一个工作区', hint: '填写名称并选择外观预设', done: true },
  { id: 'guide-2', label: '邀请团队成员', hint: '至少邀请 1 位协作者', done: true },
  { id: 'guide-3', label: '接入第一个数据源', hint: '连接数据库或上传文件', done: false },
  { id: 'guide-4', label: '发布第一个页面', hint: '从模板创建并上线', done: false },
])

const guideDoneCount = computed(() => guideItems.value.filter(item => item.done).length)
const guidePercent = computed(() => Math.round((guideDoneCount.value / guideItems.value.length) * 100))
const guideCurrentId = computed(() => guideItems.value.find(item => !item.done)?.id)
const guideAllDone = computed(() => guideDoneCount.value === guideItems.value.length)

function resetGuide() {
  guideItems.value.forEach((item) => {
    item.done = false
  })
}

// ============================================================
// 反馈 4：决策收件箱（同意 / 确认后拒绝 + 已处理归档）
// ============================================================

interface DecisionItem {
  id: string
  title: string
  requester: string
  summary: string
  time: string
  answered: boolean
  result?: 'approved' | 'rejected'
}

const decisions = ref<DecisionItem[]>([
  { id: 'dc-1', title: '申请发布权限', requester: '陈念', summary: '需要在演示环境执行一次构建发布。', time: '10 分钟前', answered: false },
  { id: 'dc-2', title: '新增计费方案字段', requester: '林舟', summary: '账单表增加「续费周期」字段。', time: '2 小时前', answered: false },
  { id: 'dc-3', title: '外部数据源接入', requester: '周衡', summary: '接入第三方汇率数据源。', time: '昨天', answered: false },
])

const pendingDecisions = computed(() => decisions.value.filter(item => !item.answered))
const answeredDecisions = computed(() => decisions.value.filter(item => item.answered))

function approveDecision(item: DecisionItem) {
  item.answered = true
  item.result = 'approved'
  toast.success(`已同意「${item.title}」（本地演示）`)
}

function rejectDecision(item: DecisionItem) {
  item.answered = true
  item.result = 'rejected'
  toast(`已拒绝「${item.title}」（本地演示）`)
}

function resetDecisions() {
  decisions.value.forEach((item) => {
    item.answered = false
    item.result = undefined
  })
}

// ============================================================
// 反馈 5：通知堆栈（本地推送 / 已读 / 逐条关闭 / 清空）
// ============================================================

type NoticeKind = 'info' | 'success' | 'warning'

interface StackNotice {
  id: string
  kind: NoticeKind
  title: string
  body: string
  date: Date
  read: boolean
}

const noticePool: { kind: NoticeKind, title: string, body: string }[] = [
  { kind: 'success', title: '构建成功', body: 'main 分支构建完成，产物已就绪。' },
  { kind: 'info', title: '成员加入', body: '陈念 接受了你的邀请并加入工作区。' },
  { kind: 'warning', title: '存储用量告警', body: '本月存储用量已达到 82%。' },
  { kind: 'info', title: '周报已生成', body: '上周变更摘要已生成，点击查看。' },
]

let noticeCursor = 0
const notices = ref<StackNotice[]>([
  { id: 'notice-1', kind: 'info', title: '欢迎来到控件画廊', body: '这是本地通知堆栈演示，可推送、已读与关闭。', date: new Date(Date.now() - 5 * 60_000), read: false },
])

const unreadCount = computed(() => notices.value.filter(item => !item.read).length)

function noticeIcon(kind: NoticeKind) {
  const map: Record<NoticeKind, string> = {
    info: 'morph:circle-info',
    success: 'morph:circle-check',
    warning: 'morph:triangle-alert',
  }
  return map[kind]
}

function pushNotice() {
  const preset = noticePool[noticeCursor % noticePool.length]!
  noticeCursor += 1
  notices.value.unshift({
    id: `notice-${Date.now()}`,
    kind: preset.kind,
    title: preset.title,
    body: preset.body,
    date: new Date(),
    read: false,
  })
}

function markNoticeRead(id: string) {
  const notice = notices.value.find(item => item.id === id)
  if (notice) {
    notice.read = true
  }
}

function dismissNotice(id: string) {
  notices.value = notices.value.filter(item => item.id !== id)
}

function markAllNoticesRead() {
  notices.value.forEach((item) => {
    item.read = true
  })
}

function clearNotices() {
  notices.value = []
}
</script>

<template>
  <div class="afc">
    <!-- 画廊头部：分组切换 -->
    <section class="afc-head">
      <div class="afc-head__text">
        <h2>AffectUI 控件</h2>
        <p>导航、输入与反馈三组控件的可交互演示：全部为本地状态与本地模拟，不发起网络请求。</p>
      </div>
      <YdSegmented v-model="activeGroup" :options="groupTabs" aria-label="切换控件分组" />
    </section>

    <!-- ====================== 导航 ====================== -->
    <div v-show="showGroup('navigation')" class="afc-group">
      <p class="afc-group__caption">
        导航（{{ groupCounts.navigation }}）
      </p>

      <!-- 命令菜单 -->
      <DemoCard title="Command Menu 命令菜单" description="Ctrl / Cmd + K 呼出，输入过滤，方向键移动、Enter 执行、Esc 关闭。">
        <div class="afc-command">
          <YdAction
            type="button"
            class="afc-command__trigger yd-focus"
            :aria-expanded="commandOpen"
            @click="commandOpen = !commandOpen"
          >
            <YdIcon name="morph:search" />
            <span class="afc-command__placeholder">搜索或输入命令…</span>
            <YdKbdGroup>
              <YdKbd size="sm">
                Ctrl
              </YdKbd>
              <YdKbd size="sm">
                K
              </YdKbd>
            </YdKbdGroup>
          </YdAction>

          <div v-if="commandOpen" class="afc-command__panel">
            <YdInput
              :ref="setCommandInput"
              v-model="commandKeyword"
              clearable
              placeholder="筛选命令，如：导出、邀请"
              aria-label="筛选命令"
              @keydown="onCommandInputKeydown"
            >
              <template #start>
                <YdIcon name="morph:search" />
              </template>
            </YdInput>

            <div class="afc-command__list">
              <div v-for="group in commandGroups" :key="group.label" class="afc-command__group">
                <p class="afc-caption">
                  {{ group.label }}
                </p>
                <YdAction
                  v-for="item in group.entries"
                  :key="item.entry.id"
                  type="button"
                  class="afc-command__item yd-focus"
                  :class="{ 'is-active': item.index === commandActive }"
                  :aria-selected="item.index === commandActive"
                  @click="runCommand(item.entry)"
                  @mousemove="commandActive = item.index"
                >
                  <YdIcon :name="item.entry.icon" />
                  <span class="afc-command__label">{{ item.entry.label }}</span>
                  <YdKbdGroup v-if="item.entry.shortcut" class="afc-command__keys">
                    <YdKbd v-for="key in item.entry.shortcut" :key="key" size="sm">
                      {{ key }}
                    </YdKbd>
                  </YdKbdGroup>
                  <YdIcon v-else name="morph:corner-down-left" class="afc-command__enter" />
                </YdAction>
              </div>
              <p v-if="!commandFlat.length" class="afc-hint">
                没有匹配的命令
              </p>
            </div>

            <p class="afc-command__foot">
              <span class="afc-kbd-inline">
                <YdKbd size="sm">
                  ↑
                </YdKbd>
                <YdKbd size="sm">
                  ↓
                </YdKbd>
                选择
                <YdKbd size="sm">
                  Enter
                </YdKbd>
                执行
                <YdKbd size="sm">
                  Esc
                </YdKbd>
                关闭
              </span>
            </p>
          </div>
        </div>
      </DemoCard>

      <!-- 分段控制 -->
      <DemoCard title="Segmented Control 分段控制" description="排版密度切换联动预览区；支持方向键、Home / End 与 change 事件。">
        <YdSegmented v-model="density" :options="densityOptions" aria-label="排版密度" block @change="onDensityChange" />
        <div class="afc-density" :style="{ '--afc-density-gap': densityCurrent.gap, '--afc-density-size': densityCurrent.size }">
          <p v-for="line in densityCurrent.lines" :key="line" class="afc-density__line">
            分段控制联动预览行 {{ line }}：{{ densityCurrent.note }}
          </p>
        </div>
        <p class="afc-hint">
          {{ densityLastChange }} · 行距 {{ densityCurrent.gap }} · 字号 {{ densityCurrent.size }}
        </p>
      </DemoCard>

      <!-- 集成选择器 -->
      <DemoCard title="Integration Picker 集成选择器" description="关键字搜索 + 分类分段过滤 + 单选行选择，底部确认当前选择。">
        <div class="afc-picker__tools">
          <YdInput v-model="pickerKeyword" clearable placeholder="搜索集成，如：Mongo、邮件" aria-label="搜索集成">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdSegmented v-model="pickerCategory" :options="pickerCategoryOptions" aria-label="集成分类" />
        </div>
        <div class="afc-picker__list" role="listbox" aria-label="集成列表">
          <YdAction
            v-for="item in pickerFiltered"
            :key="item.id"
            type="button"
            role="option"
            class="afc-picker__item yd-focus"
            :class="{ 'is-selected': item.id === pickerSelectedId }"
            :aria-selected="item.id === pickerSelectedId"
            @click="pickerSelectedId = item.id"
          >
            <YdIcon :name="item.icon" class="afc-picker__icon" />
            <span class="afc-picker__text">
              <span class="afc-picker__name">{{ item.name }}</span>
              <span class="afc-picker__desc">{{ item.description }} · {{ item.category }}</span>
            </span>
            <YdTag :variant="item.connected ? 'secondary' : 'outline'">
              {{ item.connected ? '已连接' : '可接入' }}
            </YdTag>
          </YdAction>
          <YdEmpty
            v-if="!pickerFiltered.length"
            icon="morph:search"
            title="没有匹配的集成"
            description="换个关键字或切换分类。"
          />
        </div>
        <div class="afc-picker__foot">
          <span class="afc-hint">
            当前选择：{{ pickerSelected ? `${pickerSelected.name} · ${pickerSelected.description}` : '未选择' }}
          </span>
          <YdButton size="sm" :disabled="!pickerSelected" @click="confirmPicker">
            下一步：配置连接
          </YdButton>
        </div>
      </DemoCard>

      <!-- 个人菜单 -->
      <DemoCard title="Profile Menu 个人菜单" description="头像触发的下拉菜单：头部信息、普通项、勾选项与危险动作分组。">
        <div class="afc-profile">
          <YdDropdown :items="profileItems" align="end">
            <YdAction type="button" class="afc-profile__trigger yd-focus" aria-label="打开账户菜单">
              <YdAvatar src="" fallback="余" />
              <span class="afc-profile__meta">
                <span class="afc-profile__name">余梦管理员</span>
                <span class="afc-profile__mail">admin@yudream.local</span>
              </span>
              <YdIcon name="morph:chevron-down" />
            </YdAction>
            <template #header>
              <div class="afc-profile__head">
                <YdAvatar src="" fallback="余" />
                <div>
                  <p class="afc-profile__name">
                    余梦管理员
                  </p>
                  <p class="afc-profile__mail">
                    admin@yudream.local
                  </p>
                </div>
              </div>
            </template>
          </YdDropdown>
          <p class="afc-hint">
            专注模式：{{ profileFocus ? '已开启' : '已关闭' }} · 通知免打扰：{{ profileMute ? '已开启' : '已关闭' }}（勾选项为本地状态）
          </p>
        </div>
      </DemoCard>
    </div>

    <!-- ====================== 输入 ====================== -->
    <div v-show="showGroup('inputs')" class="afc-group">
      <p class="afc-group__caption">
        输入（{{ groupCounts.inputs }}）
      </p>

      <!-- 组合框 -->
      <DemoCard title="Combobox 组合框" description="按部门分组的搜索单选：支持过滤、禁用项与清除；模型为单个字符串值。">
        <YdCombobox v-model="comboboxValue" :options="comboboxOptions" clearable placeholder="搜索并选择负责人" aria-label="选择负责人" />
        <p class="afc-hint">
          当前值：{{ comboboxValue ?? '未选择' }}<template v-if="comboboxSelectedLabel">
            · {{ comboboxSelectedLabel }}
          </template>
        </p>
      </DemoCard>

      <!-- 日期范围 -->
      <DemoCard title="Date Range Picker 日期范围" description="选择起止日期后本地换算间隔天数，支持清除；模型为字符串数组。">
        <YdRangePicker v-model="stayRange" value-format="YYYY-MM-DD" :placeholder="['开始日期', '结束日期']" aria-label="选择日期范围" />
        <p class="afc-hint">
          <template v-if="stayRange && stayNights !== null">
            已选 {{ stayRange[0] }} 至 {{ stayRange[1] }}，共 {{ stayNights }} 天{{ stayNights > 0 ? `（${stayNights} 晚）` : '' }}。
          </template>
          <template v-else>
            尚未选择日期范围。
          </template>
        </p>
      </DemoCard>

      <!-- 拖拽上传区 -->
      <DemoCard title="Dropzone 拖拽上传区" description="拖入或点击选择文件后进行本地规则校验与进度模拟：超过 5 MB 首次必失败一次，可重试；全程无网络。">
        <YdAlert
          icon="morph:circle-info"
          title="本地模拟"
          description="上传进度为组件内定时器模拟，文件仅读取名称与大小，不发送任何数据。"
        />
        <div
          class="afc-drop"
          :class="{ 'is-dragover': dropDragover }"
          @dragover.prevent="dropDragover = true"
          @dragleave="dropDragover = false"
          @drop.prevent="onDropZoneDrop"
        >
          <YdIcon name="morph:cloud-upload" class="afc-drop__icon" />
          <p class="afc-drop__title">
            拖拽文件到此处
          </p>
          <p class="afc-drop__hint">
            单文件 ≤ 8 MB；可执行文件会被拒绝；大于 5 MB 的文件首次上传会模拟失败。
          </p>
          <YdButton variant="outline" size="sm" @click="openDropPicker">
            <YdIcon name="morph:folder" />
            选择本地文件
          </YdButton>
        </div>

        <ul v-if="dropItems.length" class="afc-drop__list">
          <li v-for="item in dropItems" :key="item.id" class="afc-drop__item" :data-status="item.status">
            <YdIcon :name="item.status === 'error' ? 'morph:circle-alert' : 'morph:file-text'" class="afc-drop__file-icon" />
            <div class="afc-drop__meta">
              <span class="afc-drop__name">{{ item.name }}</span>
              <span class="afc-drop__size">{{ formatDropSize(item.size) }}</span>
            </div>
            <div class="afc-drop__state">
              <YdProgress v-if="item.status === 'uploading'" :model-value="item.progress" class="afc-drop__progress" />
              <span v-if="item.status === 'uploading'" class="afc-drop__percent">{{ item.progress }}%</span>
              <YdTag v-else-if="item.status === 'success'" variant="secondary">
                已完成
              </YdTag>
              <template v-else>
                <span class="afc-drop__error">{{ item.error }}</span>
                <YdButton size="icon-sm" variant="ghost" title="重试上传" @click="retryDropItem(item.id)">
                  <YdIcon name="morph:rotate-ccw" />
                </YdButton>
              </template>
              <YdButton size="icon-sm" variant="ghost" :title="`移除 ${item.name}`" @click="removeDropItem(item.id)">
                <YdIcon name="morph:x" />
              </YdButton>
            </div>
          </li>
        </ul>
        <p v-else class="afc-hint">
          还没有文件，拖入或选择本地文件试试。
        </p>
      </DemoCard>

      <!-- 一次性密码 -->
      <DemoCard title="OTP Input 一次性密码" description="6 位数字输入：粘贴 / 手输，填满自动校验；错误可重填，支持重发倒计时。">
        <p class="afc-hint">
          本演示验证码：<code class="afc-code">{{ OTP_EXPECTED }}</code>（可点击「模拟粘贴」或手动输入）
        </p>
        <YdInputOTP v-model="otpValue" :length="OTP_LENGTH" :separator="[3]" pattern="only-digits" />
        <p class="afc-hint" :data-state="otpState">
          <template v-if="otpState === 'verified'">
            校验通过，身份已确认（本地演示）。
          </template>
          <template v-else-if="otpState === 'error'">
            验证码不正确，请重新输入。
          </template>
          <template v-else>
            已输入 {{ otpValue.length }} / {{ OTP_LENGTH }} 位。
          </template>
        </p>
        <div class="afc-row">
          <YdButton size="sm" variant="outline" :disabled="otpCooldown > 0" @click="resendOtp">
            {{ otpCooldown > 0 ? `重新发送（${otpCooldown}s）` : '重新发送验证码' }}
          </YdButton>
          <YdButton size="sm" variant="ghost" @click="pasteOtp">
            <YdIcon name="morph:copy" />
            模拟粘贴
          </YdButton>
          <YdButton size="sm" variant="ghost" @click="clearOtp">
            清空
          </YdButton>
        </div>
      </DemoCard>

      <!-- 分步表单 -->
      <DemoCard title="Stepper Form 分步表单" description="三步创建工作区：每步独立校验、错误回显到步骤状态，完成后可重新开始。">
        <YdSteps :steps="stepperItems" :model="stepperStep" :status="stepperDone ? 'finish' : 'process'" />
        <template v-if="!stepperDone">
          <div v-if="stepperStep === 0" class="afc-form">
            <label class="afc-field">
              <span class="afc-field__label">工作区名称</span>
              <YdInput v-model="stepperForm.name" placeholder="如：余梦设计中心" aria-label="工作区名称" />
              <span v-if="stepperErrors.name" class="afc-field__error">{{ stepperErrors.name }}</span>
            </label>
            <label class="afc-field">
              <span class="afc-field__label">管理员邮箱</span>
              <YdInput v-model="stepperForm.email" type="email" placeholder="name@example.com" aria-label="管理员邮箱" />
              <span v-if="stepperErrors.email" class="afc-field__error">{{ stepperErrors.email }}</span>
            </label>
          </div>

          <div v-else-if="stepperStep === 1" class="afc-form">
            <label class="afc-field">
              <span class="afc-field__label">团队角色</span>
              <YdSelect v-model="stepperForm.role" :options="stepperRoleOptions" aria-label="团队角色" />
            </label>
            <label class="afc-field">
              <span class="afc-field__label">初始席位（1 - 50）</span>
              <YdNumberField v-model="stepperForm.seats" :min="1" :max="50" aria-label="初始席位数" />
            </label>
            <div class="afc-inline">
              <YdSwitch v-model="stepperForm.notice" aria-label="新成员欢迎通知" />
              <span>邀请成员时发送欢迎通知</span>
            </div>
          </div>

          <div v-else class="afc-form">
            <dl class="afc-summary">
              <div>
                <dt>名称</dt>
                <dd>{{ stepperForm.name }}</dd>
              </div>
              <div>
                <dt>邮箱</dt>
                <dd>{{ stepperForm.email }}</dd>
              </div>
              <div>
                <dt>角色 / 席位</dt>
                <dd>{{ stepperRoleLabel }} · {{ stepperForm.seats }} 席</dd>
              </div>
              <div>
                <dt>欢迎通知</dt>
                <dd>{{ stepperForm.notice ? '开启' : '关闭' }}</dd>
              </div>
            </dl>
            <YdCheckbox v-model="stepperForm.agree">
              我已阅读并同意《协作条款》（本地演示）
            </YdCheckbox>
            <span v-if="stepperErrors.agree" class="afc-field__error">{{ stepperErrors.agree }}</span>
          </div>

          <div class="afc-row afc-row--end">
            <YdButton variant="outline" :disabled="stepperStep === 0 || stepperSubmitting" @click="stepperPrev">
              上一步
            </YdButton>
            <YdButton :loading="stepperSubmitting" @click="stepperNext">
              {{ stepperStep === 2 ? '创建工作区' : '下一步' }}
            </YdButton>
          </div>
        </template>
        <YdResult v-else status="success" title="工作区已创建" description="这是本地演示结果，刷新或点击下方按钮可重新体验。">
          <YdButton variant="outline" @click="resetStepper">
            再创建一份
          </YdButton>
        </YdResult>
      </DemoCard>

      <!-- 集成设置 -->
      <DemoCard title="Integration Settings 集成设置" description="服务地址 https 校验、密钥留空不覆盖（回显已保存标志）、时区选择与本地探测、保存。">
        <div class="afc-form">
          <label class="afc-field">
            <span class="afc-field__label">服务地址</span>
            <YdInput v-model="integrationSettings.endpoint" type="url" placeholder="https://…" aria-label="集成服务地址" />
            <span v-if="integrationEndpointInvalid" class="afc-field__error">地址无效或不是 https。</span>
          </label>
          <label class="afc-field">
            <span class="afc-field__label">API 密钥</span>
            <YdInput
              v-model="integrationSettings.secret"
              type="password"
              :placeholder="integrationSecretSet ? '已保存密钥：留空保持不变' : '尚未设置密钥'"
              aria-label="API 密钥"
            />
            <span class="afc-field__help">{{ integrationSecretSet ? '已保存：sk-••••（密钥不回传 UI）' : '保存后将记录“密钥已设置”标志。' }}</span>
          </label>
          <label class="afc-field">
            <span class="afc-field__label">时区</span>
            <YdSelect v-model="integrationSettings.timezone" :options="timezoneOptions" aria-label="时区" />
          </label>
          <div class="afc-inline">
            <YdSwitch v-model="integrationSettings.enabled" aria-label="启用集成" />
            <span>启用该集成（关闭后暂停同步）</span>
          </div>
        </div>
        <div class="afc-row afc-row--end">
          <YdButton variant="outline" :loading="integrationProbing" @click="probeIntegration">
            <YdIcon name="morph:locate-fixed" />
            测试连接
          </YdButton>
          <YdButton @click="saveIntegration">
            <YdIcon name="morph:save" />
            保存设置
          </YdButton>
        </div>
        <p class="afc-hint">
          时区：{{ integrationTimezoneLabel }} · 探测结果：{{ integrationProbeState === 'ok' ? '成功' : integrationProbeState === 'error' ? '失败' : '未探测' }} · 集成状态：{{ integrationSettings.enabled ? '已启用' : '已停用' }}
        </p>
      </DemoCard>

      <!-- 提示词库 -->
      <DemoCard title="Prompt Library 提示词库" description="选择模板后自动提取 {{变量}} 并生成输入项，实时组装提示词，可复制结果。">
        <YdPrompts :items="promptItems" title="选择模板" vertical @select="selectPrompt" />
        <div class="afc-prompt">
          <p class="afc-caption">
            模板变量（{{ activePrompt.label }}）
          </p>
          <label v-for="name in promptVarNames" :key="name" class="afc-field">
            <span class="afc-field__label">{{ name }}</span>
            <YdInput v-model="promptValues[name]" :placeholder="`填写「${name}」`" :aria-label="`模板变量 ${name}`" />
          </label>
          <p class="afc-caption">
            组装结果
          </p>
          <p class="afc-prompt__preview">
            <template v-for="(segment, index) in promptSegments" :key="index">
              <span v-if="segment.type === 'text'">{{ segment.value }}</span>
              <mark v-else class="afc-prompt__var" :data-filled="promptFilled(segment.value)">{{ promptValues[segment.value]?.trim() || `【${segment.value}】` }}</mark>
            </template>
          </p>
          <div class="afc-row afc-row--end">
            <YdCopyButton :text="promptAssembled" label="复制组装结果" />
          </div>
        </div>
      </DemoCard>

      <!-- 筛选菜单 -->
      <DemoCard title="Filter Menu 筛选菜单" description="浮层内按负责人 / 状态 / 标签分组勾选，已选条件以可关闭 chips 回显并过滤本地列表。">
        <div class="afc-filter__bar">
          <YdPopover v-model:open="filterMenuOpen" align="start">
            <YdButton variant="outline" size="sm">
              <YdIcon name="morph:list-filter" />
              筛选
              <YdTag v-if="filterChips.length" variant="secondary">
                {{ filterChips.length }}
              </YdTag>
            </YdButton>
            <template #panel>
              <div class="afc-filter__panel">
                <div class="afc-filter__group">
                  <p class="afc-caption">
                    状态
                  </p>
                  <YdCheckbox
                    v-for="option in filterStatusOptions"
                    :key="option"
                    :model-value="filterStatuses.includes(option)"
                    @update:model-value="(value: unknown) => toggleInList(filterStatuses, option, value === true)"
                  >
                    {{ option }}
                  </YdCheckbox>
                </div>
                <div class="afc-filter__group">
                  <p class="afc-caption">
                    负责人
                  </p>
                  <YdCheckbox
                    v-for="option in filterOwnerOptions"
                    :key="option"
                    :model-value="filterOwners.includes(option)"
                    @update:model-value="(value: unknown) => toggleInList(filterOwners, option, value === true)"
                  >
                    {{ option }}
                  </YdCheckbox>
                </div>
                <div class="afc-filter__group">
                  <p class="afc-caption">
                    标签
                  </p>
                  <YdCheckbox
                    v-for="option in filterTagOptions"
                    :key="option"
                    :model-value="filterTags.includes(option)"
                    @update:model-value="(value: unknown) => toggleInList(filterTags, option, value === true)"
                  >
                    {{ option }}
                  </YdCheckbox>
                </div>
                <YdButton variant="ghost" size="sm" :disabled="!filterChips.length" @click="clearTaskFilters">
                  清空条件
                </YdButton>
              </div>
            </template>
          </YdPopover>
          <div v-if="filterChips.length" class="afc-filter__chips">
            <YdTag v-for="chip in filterChips" :key="chip.key" closable variant="outline" @close="chip.remove()">
              {{ chip.label }}
            </YdTag>
            <YdButton variant="link" size="sm" @click="clearTaskFilters">
              清除全部
            </YdButton>
          </div>
          <span class="afc-hint">
            命中 {{ filteredTasks.length }} / {{ filterTasks.length }} 条
          </span>
        </div>
        <ul class="afc-filter__list">
          <li v-for="task in filteredTasks" :key="task.id" class="afc-filter__row">
            <span class="afc-filter__title">{{ task.title }}</span>
            <span class="afc-filter__meta">{{ task.owner }} · {{ task.status }}</span>
            <YdTag variant="outline">
              {{ task.tag }}
            </YdTag>
          </li>
        </ul>
        <YdEmpty
          v-if="!filteredTasks.length"
          icon="morph:list-filter"
          title="没有匹配的任务"
          description="调整或清除筛选条件后重试。"
        />
      </DemoCard>

      <!-- 账单周期 -->
      <DemoCard title="Billing Schedule 账单周期" description="方案选择 + 月付 / 年付切换：单价、合计、年付节省与未来四期账单本地推算。">
        <div class="afc-billing__plans">
          <YdAction
            v-for="plan in billingPlans"
            :key="plan.id"
            type="button"
            class="afc-billing__plan yd-focus"
            :class="{ 'is-selected': plan.id === billingPlanId }"
            :aria-pressed="plan.id === billingPlanId"
            @click="billingPlanId = plan.id"
          >
            <span class="afc-billing__plan-name">{{ plan.name }}</span>
            <span class="afc-billing__plan-note">{{ plan.note }}</span>
            <span class="afc-billing__plan-price">{{ formatBillingAmount(plan.monthly) }} / 月</span>
          </YdAction>
        </div>
        <div class="afc-billing__bar">
          <YdSegmented v-model="billingCycle" :options="billingCycleOptions" aria-label="计费周期" />
          <span v-if="billingSaving > 0" class="afc-billing__save">
            年付节省 {{ formatBillingAmount(billingSaving) }}
          </span>
        </div>
        <dl class="afc-summary">
          <div>
            <dt>当前方案</dt>
            <dd>{{ billingPlan.name }}</dd>
          </div>
          <div>
            <dt>折合单价</dt>
            <dd>{{ formatBillingAmount(billingUnitPrice) }} / 月</dd>
          </div>
          <div>
            <dt>本期应付</dt>
            <dd>{{ formatBillingAmount(billingTotal) }}</dd>
          </div>
        </dl>
        <table class="afc-billing__table">
          <thead>
            <tr>
              <th>期数</th>
              <th>扣费日</th>
              <th class="afc-billing__amount">
                金额
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="phase in billingPhases" :key="phase.index">
              <td>第 {{ phase.index }} 期</td>
              <td>{{ phase.date }}</td>
              <td class="afc-billing__amount">
                {{ formatBillingAmount(phase.amount) }}
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="2">
                四期合计
              </td>
              <td class="afc-billing__amount">
                {{ formatBillingAmount(billingPhasesTotal) }}
              </td>
            </tr>
          </tfoot>
        </table>
        <div class="afc-row afc-row--end">
          <YdButton size="sm" @click="confirmBilling">
            确认方案
          </YdButton>
        </div>
      </DemoCard>
    </div>

    <!-- ====================== 反馈 ====================== -->
    <div v-show="showGroup('feedback')" class="afc-group">
      <p class="afc-group__caption">
        反馈（{{ groupCounts.feedback }}）
      </p>

      <!-- 轻提示 -->
      <DemoCard title="Toaster 轻提示" description="toast.promise 展示加载 / 成功 / 失败流转，另有常规变体；提示由展示壳根部 YdToast 承载。">
        <YdCheckbox v-model="toastFail">
          本次发布模拟失败
        </YdCheckbox>
        <div class="afc-row">
          <YdButton @click="deployWithToast">
            <YdIcon name="morph:upload" />
            发布新版本
          </YdButton>
          <YdButton variant="outline" @click="toast.success('配置已同步（本地演示）')">
            成功提示
          </YdButton>
          <YdButton variant="outline" @click="toast.error('连接超时（本地模拟）')">
            错误提示
          </YdButton>
          <YdButton variant="ghost" @click="toast('草稿已保存（本地演示）')">
            普通提示
          </YdButton>
        </div>
        <p class="afc-hint">
          发布流程为本地 Promise 定时模拟：勾选「模拟失败」后可看到失败分支文案。
        </p>
      </DemoCard>

      <!-- 进度追踪 -->
      <DemoCard title="Progress Tracker 进度追踪" description="四阶段流水线本地推进：总进度条 + 阶段步骤联动，支持暂停与重置。">
        <YdProgress :model-value="trackerOverall" />
        <p class="afc-hint">
          总进度 {{ trackerOverall }}% · 阶段 {{ Math.min(trackerCurrent + 1, trackerPhases.length) }} / {{ trackerPhases.length }}{{ trackerDone ? ' · 已全部完成' : trackerRunning ? ' · 运行中' : ' · 已暂停' }}
        </p>
        <YdSteps :steps="trackerItems" :model="Math.min(trackerCurrent, trackerPhases.length - 1)" :status="trackerDone ? 'finish' : 'process'" vertical />
        <div class="afc-row">
          <YdButton size="sm" :disabled="trackerDone" @click="toggleTracker">
            <YdIcon :name="trackerRunning ? 'morph:circle-info' : 'morph:play'" />
            {{ trackerRunning ? '暂停' : trackerCurrent === 0 && trackerPercent === 0 ? '开始执行' : '继续' }}
          </YdButton>
          <YdButton size="sm" variant="outline" @click="resetTracker">
            <YdIcon name="morph:rotate-ccw" />
            重置
          </YdButton>
        </div>
      </DemoCard>

      <!-- 配置向导 -->
      <DemoCard title="Setup Guide 配置向导" description="引导清单勾选联动进度；首个未完成项标记「当前」，全部完成后进入完成态。">
        <YdProgress :model-value="guidePercent" />
        <p class="afc-hint">
          已完成 {{ guideDoneCount }} / {{ guideItems.length }} 项（{{ guidePercent }}%）
        </p>
        <ul class="afc-guide">
          <li v-for="item in guideItems" :key="item.id" class="afc-guide__item" :data-done="item.done">
            <YdCheckbox :model-value="item.done" @update:model-value="(value: unknown) => item.done = value === true">
              <span class="afc-guide__label">{{ item.label }}</span>
              <span class="afc-guide__hint">{{ item.hint }}</span>
            </YdCheckbox>
            <YdTag v-if="item.id === guideCurrentId" variant="default">
              当前
            </YdTag>
            <YdIcon v-else-if="item.done" name="morph:circle-check" class="afc-guide__done" />
          </li>
        </ul>
        <YdResult v-if="guideAllDone" status="success" title="配置完成" description="工作区已就绪，这份演示为本地状态。">
          <YdButton variant="outline" @click="resetGuide">
            重新开始
          </YdButton>
        </YdResult>
      </DemoCard>

      <!-- 决策收件箱 -->
      <DemoCard title="Decision Inbox 决策收件箱" description="待决策列表：直接同意或经确认弹层拒绝，处理后进入已归档并统计数量。">
        <div class="afc-decision__stats">
          <YdTag variant="destructive">
            待决策 {{ pendingDecisions.length }}
          </YdTag>
          <YdTag variant="secondary">
            已处理 {{ answeredDecisions.length }}
          </YdTag>
        </div>
        <ul v-if="pendingDecisions.length" class="afc-decision__list">
          <li v-for="item in pendingDecisions" :key="item.id" class="afc-decision__item">
            <div class="afc-decision__body">
              <p class="afc-decision__title">
                {{ item.title }}
              </p>
              <p class="afc-decision__desc">
                {{ item.requester }} · {{ item.summary }}
              </p>
              <p class="afc-decision__time">
                {{ item.time }}
              </p>
            </div>
            <div class="afc-decision__actions">
              <YdButton size="sm" @click="approveDecision(item)">
                同意
              </YdButton>
              <YdPopconfirm title="确认拒绝该申请？" description="拒绝后申请将进入已归档。" confirm-text="确认拒绝" @confirm="rejectDecision(item)">
                <YdButton size="sm" variant="outline">
                  拒绝
                </YdButton>
              </YdPopconfirm>
            </div>
          </li>
        </ul>
        <YdEmpty v-else icon="morph:circle-check" title="待办已清空" description="全部决策已处理。">
          <YdButton variant="outline" size="sm" @click="resetDecisions">
            恢复演示数据
          </YdButton>
        </YdEmpty>
        <ul v-if="answeredDecisions.length" class="afc-decision__archive">
          <li v-for="item in answeredDecisions" :key="item.id" class="afc-decision__row">
            <span class="afc-decision__title">{{ item.title }}</span>
            <YdTag :variant="item.result === 'approved' ? 'secondary' : 'destructive'">
              {{ item.result === 'approved' ? '已同意' : '已拒绝' }}
            </YdTag>
          </li>
        </ul>
      </DemoCard>

      <!-- 通知堆栈 -->
      <DemoCard title="Notification Stack 通知堆栈" description="本地推送模拟通知：未读统计、单条已读 / 关闭、全部已读与清空，列表带过渡动画。">
        <div class="afc-notice__bar">
          <YdTag v-if="unreadCount" variant="destructive">
            未读 {{ unreadCount }}
          </YdTag>
          <span v-else class="afc-hint">
            没有未读通知
          </span>
          <div class="afc-row">
            <YdButton size="sm" variant="outline" @click="pushNotice">
              <YdIcon name="morph:plus" />
              模拟新通知
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="!unreadCount" @click="markAllNoticesRead">
              全部已读
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="!notices.length" @click="clearNotices">
              清空
            </YdButton>
          </div>
        </div>
        <TransitionGroup v-if="notices.length" name="afc-stack" tag="ul" class="afc-notice__list">
          <li v-for="notice in notices" :key="notice.id" class="afc-notice" :data-kind="notice.kind" :data-read="notice.read">
            <YdIcon :name="noticeIcon(notice.kind)" class="afc-notice__icon" />
            <div class="afc-notice__body">
              <p class="afc-notice__title">
                {{ notice.title }}
                <YdTag v-if="!notice.read" variant="default">
                  未读
                </YdTag>
              </p>
              <p class="afc-notice__desc">
                {{ notice.body }}
              </p>
              <p class="afc-notice__time">
                <YdTimeAgo :date="notice.date" :update-interval="15_000" />
              </p>
            </div>
            <div class="afc-notice__actions">
              <YdButton v-if="!notice.read" size="icon-sm" variant="ghost" title="标记已读" @click="markNoticeRead(notice.id)">
                <YdIcon name="morph:check" />
              </YdButton>
              <YdButton size="icon-sm" variant="ghost" title="关闭通知" @click="dismissNotice(notice.id)">
                <YdIcon name="morph:x" />
              </YdButton>
            </div>
          </li>
        </TransitionGroup>
        <YdEmpty v-else icon="morph:bell" title="通知已清空" description="点击「模拟新通知」再次体验。" />
      </DemoCard>
    </div>
  </div>
</template>

<style scoped>
.afc {
  display: grid;
  gap: var(--yd-content-gap, 16px);
}

/* 画廊头部 */
.afc-head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
}

.afc-head__text h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: var(--color-text-1);
}

.afc-head__text p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.afc-group {
  display: grid;
  gap: var(--yd-content-gap, 16px);
}

.afc-group__caption {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
}

.afc-caption {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
}

.afc-hint {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.afc-code {
  padding: 1px 6px;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  background: var(--color-fill-2);
  border-radius: 6px;
}

.afc-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.afc-inline {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-1);
}

.afc-row--end {
  justify-content: flex-end;
}

.afc-kbd-inline {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 命令菜单 */
.afc-command__trigger {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 40px;
  padding: 8px 12px;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-3);
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
  transition: border-color var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

.afc-command__trigger:hover {
  border-color: var(--color-border-1);
}

.afc-command__placeholder {
  flex: 1;
}

.afc-command__panel {
  display: grid;
  gap: 10px;
  margin-top: 10px;
  padding: 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
}

.afc-command__list {
  display: grid;
  max-height: 264px;
  overflow-y: auto;
}

.afc-command__group {
  display: grid;
  gap: 2px;
  padding: 4px 0;
}

.afc-command__item {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 34px;
  padding: 6px 10px;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.afc-command__item:hover,
.afc-command__item.is-active {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.afc-command__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.afc-command__keys {
  flex-shrink: 0;
}

.afc-command__enter {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-4);
}

.afc-command__foot {
  display: flex;
  justify-content: flex-end;
  margin: 0;
  padding-top: 8px;
  border-top: 1px solid var(--color-border-2);
}

/* 分段控制联动预览 */
.afc-density {
  display: grid;
  gap: var(--afc-density-gap, 8px);
  padding: 14px;
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
}

.afc-density__line {
  margin: 0;
  font-size: var(--afc-density-size, 13.5px);
  color: var(--color-text-2);
  transition: font-size var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

/* 集成选择器 */
.afc-picker__tools {
  display: grid;
  gap: 10px;
}

.afc-picker__list {
  display: grid;
  gap: 6px;
}

.afc-picker__item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
  transition: border-color var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease), background var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

.afc-picker__item:hover {
  background: var(--color-fill-1);
}

.afc-picker__item.is-selected {
  background: var(--color-fill-2);
  border-color: var(--color-text-4);
}

.afc-picker__icon {
  flex-shrink: 0;
  font-size: 18px;
  color: var(--color-text-2);
}

.afc-picker__text {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.afc-picker__name {
  font-size: 13.5px;
  font-weight: 550;
  color: var(--color-text-1);
}

.afc-picker__desc {
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.afc-picker__foot {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

/* 个人菜单 */
.afc-profile {
  display: grid;
  gap: 10px;
  justify-items: start;
}

.afc-profile__trigger {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 6px 10px;
  font: inherit;
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.afc-profile__trigger:hover {
  background: var(--color-fill-1);
}

.afc-profile__meta {
  display: grid;
  text-align: left;
}

.afc-profile__name {
  margin: 0;
  font-size: 13px;
  font-weight: 550;
  color: var(--color-text-1);
}

.afc-profile__mail {
  margin: 0;
  font-size: 11.5px;
  color: var(--color-text-3);
}

.afc-profile__head {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 2px 4px 8px;
  border-bottom: 1px solid var(--color-border-2);
}

/* 组合框 / 日期范围提示行 */
.afc-form {
  display: grid;
  gap: 14px;
}

.afc-field {
  display: grid;
  gap: 6px;
}

.afc-field__label {
  font-size: 12.5px;
  font-weight: 550;
  color: var(--color-text-2);
}

.afc-field__help {
  font-size: 12px;
  color: var(--color-text-3);
}

.afc-field__error {
  font-size: 12px;
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-left: 3px solid var(--color-text-1);
  padding: 2px 8px;
  border-radius: 4px;
}

.afc-summary {
  display: grid;
  gap: 6px;
  margin: 0;
}

.afc-summary > div {
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.afc-summary dt {
  font-size: 12.5px;
  color: var(--color-text-3);
}

.afc-summary dd {
  margin: 0;
  font-size: 12.5px;
  font-weight: 550;
  color: var(--color-text-1);
  text-align: right;
}

/* 拖拽上传区 */
.afc-drop {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: 22px 16px;
  text-align: center;
  background: var(--color-bg-2);
  border: 1.5px dashed var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
  transition: border-color var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease), background var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

.afc-drop.is-dragover {
  background: var(--color-fill-2);
  border-color: var(--color-text-4);
}

.afc-drop__icon {
  font-size: 26px;
  color: var(--color-text-3);
}

.afc-drop__title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 550;
  color: var(--color-text-1);
}

.afc-drop__hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.afc-drop__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.afc-drop__item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.afc-drop__item[data-status='error'] {
  border-style: dashed;
}

.afc-drop__file-icon {
  font-size: 16px;
  color: var(--color-text-2);
}

.afc-drop__meta {
  display: grid;
  min-width: 0;
}

.afc-drop__name {
  overflow: hidden;
  font-size: 13px;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.afc-drop__size {
  font-size: 11.5px;
  color: var(--color-text-3);
}

.afc-drop__state {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.afc-drop__progress {
  width: 120px;
}

.afc-drop__percent {
  min-width: 34px;
  font-size: 11.5px;
  color: var(--color-text-3);
  text-align: right;
}

.afc-drop__error {
  font-size: 11.5px;
  color: var(--color-text-1);
}

/* 提示词库 */
.afc-prompt {
  display: grid;
  gap: 10px;
}

.afc-prompt__preview {
  margin: 0;
  padding: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-1);
  background: var(--color-bg-2);
  border-radius: 8px;
}

.afc-prompt__var {
  padding: 0 4px;
  background: var(--color-fill-2);
  border-radius: 4px;
}

.afc-prompt__var[data-filled='true'] {
  font-weight: 600;
}

/* 筛选菜单 */
.afc-filter__bar {
  display: grid;
  gap: 10px;
}

.afc-filter__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.afc-filter__panel {
  display: grid;
  gap: 12px;
  max-height: 320px;
  padding: 4px;
  overflow-y: auto;
}

.afc-filter__group {
  display: grid;
  gap: 2px;
}

.afc-filter__list {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.afc-filter__row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.afc-filter__title {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.afc-filter__meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 账单周期 */
.afc-billing__plans {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.afc-billing__plan {
  display: grid;
  gap: 2px;
  padding: 12px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 8px);
  transition: border-color var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease), background var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

.afc-billing__plan:hover {
  background: var(--color-fill-1);
}

.afc-billing__plan.is-selected {
  background: var(--color-fill-2);
  border-color: var(--color-text-4);
}

.afc-billing__plan-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
}

.afc-billing__plan-note {
  font-size: 11.5px;
  color: var(--color-text-3);
}

.afc-billing__plan-price {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--color-text-2);
}

.afc-billing__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.afc-billing__save {
  font-size: 12.5px;
  color: var(--color-text-1);
  background: var(--color-fill-2);
  padding: 2px 10px;
  border-radius: 999px;
}

.afc-billing__table {
  width: 100%;
  font-size: 12.5px;
  color: var(--color-text-2);
  border-collapse: collapse;
}

.afc-billing__table th,
.afc-billing__table td {
  padding: 7px 8px;
  text-align: left;
  border-bottom: 1px solid var(--color-border-2);
}

.afc-billing__table th {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--color-text-3);
}

.afc-billing__table tfoot td {
  font-weight: 600;
  color: var(--color-text-1);
}

.afc-billing__amount {
  text-align: right !important;
}

/* 决策收件箱 */
.afc-decision__stats {
  display: flex;
  gap: 8px;
}

.afc-decision__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.afc-decision__item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.afc-decision__body {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.afc-decision__title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 550;
  color: var(--color-text-1);
}

.afc-decision__desc {
  margin: 0;
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.afc-decision__time {
  margin: 0;
  font-size: 11.5px;
  color: var(--color-text-4);
}

.afc-decision__actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.afc-decision__archive {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 10px 0 0;
  list-style: none;
  border-top: 1px dashed var(--color-border-2);
}

.afc-decision__row {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.afc-decision__row .afc-decision__title {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--color-text-3);
}

/* 通知堆栈 */
.afc-notice__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.afc-notice__list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.afc-notice {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.afc-notice[data-read='true'] {
  opacity: 0.72;
}

.afc-notice__icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 16px;
  color: var(--color-text-2);
}

.afc-notice[data-kind='success'] .afc-notice__icon {
  color: var(--color-text-1);
}

.afc-notice__body {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.afc-notice__title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 13px;
  font-weight: 550;
  color: var(--color-text-1);
}

.afc-notice__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.afc-notice__time {
  margin: 0;
  font-size: 11.5px;
  color: var(--color-text-4);
}

.afc-notice__actions {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
}

/* 通知列表过渡 */
.afc-stack-enter-active,
.afc-stack-leave-active,
.afc-stack-move {
  transition: opacity var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease), transform var(--yd-motion-duration-fast, 0.15s) var(--yd-motion-ease, ease);
}

.afc-stack-enter-from,
.afc-stack-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.afc-stack-leave-active {
  position: absolute;
  width: 100%;
}

/* 引导清单 */
.afc-guide {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.afc-guide__item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 6px 8px;
  border-radius: 8px;
}

.afc-guide__item[data-done='true'] {
  background: var(--color-fill-1);
}

.afc-guide__label {
  display: block;
  font-size: 13px;
  color: var(--color-text-1);
}

.afc-guide__hint {
  display: block;
  font-size: 11.5px;
  color: var(--color-text-3);
}

.afc-guide__done {
  flex-shrink: 0;
  margin-top: 8px;
  font-size: 15px;
  color: var(--color-text-2);
}

/* 窄屏 */
@media (width <= 768px) {
  .afc-billing__plans {
    grid-template-columns: 1fr;
  }

  .afc-drop__item {
    grid-template-columns: auto 1fr;
  }

  .afc-drop__state {
    grid-column: 1 / -1;
    justify-content: flex-start;
  }

  .afc-decision__item {
    flex-direction: column;
    align-items: stretch;
  }

  .afc-decision__actions {
    justify-content: flex-end;
  }

  .afc-picker__foot {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
