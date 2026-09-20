<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import * as z from 'zod'
import { FormControl, FormField, FormItem, FormMessage } from '@/ui/shadcn/ui/form'
import apiInstaller, { installerErrorMessage } from '@/api/modules/installer'
import type { InstallerStatusData, MiddlewareProbeData } from '@/api/modules/installer'
import apiSetup from '@/api/modules/setup'
import { resetSetupStatus } from '@/router/guards'

const router = useRouter()

// ==================== 安装向导（安装器模式） ====================

type WizardStep = 'middleware' | 'applying' | 'restarting' | 'integrations' | 'admin'

const wizardStep = ref<WizardStep>('admin')
const installerStatus = ref<InstallerStatusData | null>(null)

const mongoUri = ref('')
const redisHost = ref('')
const redisPort = ref('6379')
const redisPassword = ref('')
const redisDatabase = ref('0')
const setupToken = ref('')

const discoverLoading = ref(false)
const discovery = ref<{ mongo: MiddlewareProbeData[], redis: MiddlewareProbeData[] } | null>(null)
const mongoProbeState = ref<{ loading: boolean, ok: boolean, text: string } | null>(null)
const redisProbeState = ref<{ loading: boolean, ok: boolean, text: string } | null>(null)
const applyError = ref('')
const restartTimedOut = ref(false)

// 可选集成配置（重启进入正常模式后，落库到系统配置）
const setupMail = reactive({ host: '', port: 465, username: '', password: '', from: '', ssl: true, starttls: false })
const setupMailTestTo = ref('')
const setupMailResult = ref<{ ok: boolean, message: string } | null>(null)
const testingSetupMail = ref(false)
const setupStorage = reactive({ endpoint: '', accessKey: '', secretKey: '', bucket: 'yudream-admin', region: 'us-east-1', pathStyle: true })
const setupStorageResult = ref<{ ok: boolean, message: string } | null>(null)
const testingSetupStorage = ref(false)
const savingIntegrations = ref(false)

const mongoReady = computed(() => mongoProbeState.value?.ok === true)
const redisReady = computed(() => redisProbeState.value?.ok === true)
const canApply = computed(() =>
  mongoUri.value.trim().length > 0
  && redisHost.value.trim().length > 0
  && mongoReady.value
  && redisReady.value
  && (!installerStatus.value?.setupTokenRequired || setupToken.value.trim().length > 0))

onMounted(async () => {
  try {
    const status = await apiInstaller.detectStatus()
    if (status) {
      installerStatus.value = status
      wizardStep.value = 'middleware'
      // 安装未完成，清掉开发环境可能残留的已完成标记，避免守卫误判
      localStorage.removeItem('setupCompleted')
      resetSetupStatus()
      void runDiscover()
      return
    }
    // 非安装器模式（如环境变量预置了数据库）：未完成初始化的全新部署同样先进入可选集成配置
    const res = await apiSetup.status()
    if (res.data.setupCompleted === false) {
      localStorage.removeItem('setupCompleted')
      resetSetupStatus()
      wizardStep.value = 'integrations'
    }
  }
  catch {
    // 探测失败按正常模式处理，直接进入原初始化表单
  }
})

async function runDiscover() {
  discoverLoading.value = true
  try {
    discovery.value = await apiInstaller.discover()
    prefillFromDiscovery()
  }
  catch (error: any) {
    useFaToast().error('中间件自动发现失败', { description: installerErrorMessage(error) })
  }
  finally {
    discoverLoading.value = false
  }
}

function prefillFromDiscovery() {
  const mongo = discovery.value?.mongo?.find(p => p.reachable && !p.authRequired && !p.authFailed)
    ?? discovery.value?.mongo?.find(p => p.reachable)
  if (mongo && !mongoUri.value) {
    // 发现目标不含库名，补默认库；带认证提示时由用户自行补账号
    mongoUri.value = `${mongo.target}/yudream`
  }
  const redis = discovery.value?.redis?.find(p => p.reachable && !p.authFailed)
  if (redis && !redisHost.value) {
    const [host, port] = redis.target.split(':')
    redisHost.value = host || 'localhost'
    redisPort.value = port || '6379'
  }
}

async function testMongo() {
  if (!mongoUri.value.trim()) {
    mongoProbeState.value = { loading: false, ok: false, text: '请先填写 MongoDB 连接串' }
    return
  }
  mongoProbeState.value = { loading: true, ok: false, text: '' }
  try {
    const probe = await apiInstaller.probeMongo(mongoUri.value.trim())
    if (probe.reachable && !probe.authFailed) {
      const version = probe.version ? `（${probe.version}）` : ''
      const auth = probe.authRequired ? '，服务器要求认证' : ''
      mongoProbeState.value = { loading: false, ok: !probe.authRequired, text: `连接成功${version}${auth}` }
    }
    else {
      mongoProbeState.value = { loading: false, ok: false, text: probe.message }
    }
  }
  catch (error: any) {
    mongoProbeState.value = { loading: false, ok: false, text: installerErrorMessage(error) }
  }
}

async function testRedis() {
  if (!redisHost.value.trim()) {
    redisProbeState.value = { loading: false, ok: false, text: '请先填写 Redis 地址' }
    return
  }
  redisProbeState.value = { loading: true, ok: false, text: '' }
  try {
    const probe = await apiInstaller.probeRedis({
      host: redisHost.value.trim(),
      port: Number(redisPort.value) || 6379,
      password: redisPassword.value.trim() || undefined,
      database: Number(redisDatabase.value) || 0,
    })
    if (probe.reachable && !probe.authFailed) {
      const version = probe.version ? `（${probe.version}）` : ''
      const auth = probe.authRequired ? '，需要填写密码' : ''
      redisProbeState.value = { loading: false, ok: !probe.authRequired, text: `连接成功${version}${auth}` }
    }
    else {
      redisProbeState.value = { loading: false, ok: false, text: probe.message }
    }
  }
  catch (error: any) {
    redisProbeState.value = { loading: false, ok: false, text: installerErrorMessage(error) }
  }
}

async function applyInstall() {
  applyError.value = ''
  wizardStep.value = 'applying'
  try {
    await apiInstaller.apply({
      mongoUri: mongoUri.value.trim(),
      redisHost: redisHost.value.trim(),
      redisPort: Number(redisPort.value) || 6379,
      redisPassword: redisPassword.value.trim() || undefined,
      redisDatabase: Number(redisDatabase.value) || 0,
      setupToken: installerStatus.value?.setupTokenRequired ? setupToken.value.trim() : undefined,
    })
    wizardStep.value = 'restarting'
    void pollAfterRestart()
  }
  catch (error: any) {
    applyError.value = installerErrorMessage(error)
    wizardStep.value = 'middleware'
  }
}

const RESTART_TIMEOUT_MS = 180000

function sleep(ms: number) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function pollAfterRestart() {
  restartTimedOut.value = false
  const deadline = Date.now() + RESTART_TIMEOUT_MS
  // 阶段一：等待安装器模式消失（保存配置后进程自动退出、容器拉起）
  while (Date.now() < deadline) {
    await sleep(2000)
    try {
      const detect = await apiInstaller.detectStatus()
      if (detect === null) {
        break
      }
    }
    catch {
      // 后端下线中，继续等待
    }
  }
  // 阶段二：等待正常模式就绪并确认初始化未完成，回到站点/管理员步骤
  while (Date.now() < deadline) {
    await sleep(2000)
    try {
      const res = await apiSetup.status()
      if (res.data.setupCompleted === false) {
        localStorage.removeItem('setupCompleted')
        resetSetupStatus()
        wizardStep.value = 'integrations'
        return
      }
      // 已完成初始化（异常路径）直接引导登录
      goLogin()
      return
    }
    catch {
      // 正常模式尚未就绪，继续等待
    }
  }
  restartTimedOut.value = true
}

const wizardSteps = computed(() => [
  { key: 'env', label: '部署环境' },
  { key: 'middleware', label: '中间件' },
  { key: 'integrations', label: '集成配置' },
  { key: 'admin', label: '站点与管理员' },
])
const wizardActiveIndex = computed(() => {
  if (wizardStep.value === 'middleware' || wizardStep.value === 'applying' || wizardStep.value === 'restarting') {
    return 1
  }
  if (wizardStep.value === 'integrations') {
    return 2
  }
  return 3
})

async function testSetupMail() {
  if (!setupMail.host) {
    toastSetup('请先填写 SMTP 主机')
    return
  }
  if (!setupMailTestTo.value) {
    toastSetup('请填写测试收件地址')
    return
  }
  testingSetupMail.value = true
  setupMailResult.value = null
  try {
    const res = await apiSetup.testMail({
      host: setupMail.host,
      port: setupMail.port,
      username: setupMail.username,
      password: setupMail.password || undefined,
      from: setupMail.from,
      ssl: setupMail.ssl,
      starttls: setupMail.starttls,
      to: setupMailTestTo.value,
    })
    setupMailResult.value = { ok: res.data.ok, message: res.data.message }
  }
  catch {
    setupMailResult.value = { ok: false, message: '测试失败，请检查配置' }
  }
  finally {
    testingSetupMail.value = false
  }
}

async function testSetupStorage() {
  if (!setupStorage.endpoint) {
    toastSetup('请先填写对象存储 Endpoint')
    return
  }
  testingSetupStorage.value = true
  setupStorageResult.value = null
  try {
    const res = await apiSetup.testStorage({
      endpoint: setupStorage.endpoint,
      accessKey: setupStorage.accessKey,
      secretKey: setupStorage.secretKey || undefined,
      bucket: setupStorage.bucket,
      region: setupStorage.region,
      pathStyle: setupStorage.pathStyle,
      autoCreate: true,
    })
    setupStorageResult.value = { ok: res.data.ok, message: res.data.message }
  }
  catch {
    setupStorageResult.value = { ok: false, message: '测试失败，请检查配置' }
  }
  finally {
    testingSetupStorage.value = false
  }
}

async function finishIntegrations() {
  // 两项都未填写视为跳过，直接进入管理员步骤
  if (!setupMail.host && !setupStorage.endpoint) {
    wizardStep.value = 'admin'
    return
  }
  savingIntegrations.value = true
  try {
    await apiSetup.saveIntegrations({
      mail: setupMail.host
        ? {
            host: setupMail.host,
            port: setupMail.port,
            username: setupMail.username,
            password: setupMail.password || undefined,
            from: setupMail.from,
            ssl: setupMail.ssl,
            starttls: setupMail.starttls,
          }
        : null,
      storage: setupStorage.endpoint
        ? {
            endpoint: setupStorage.endpoint,
            accessKey: setupStorage.accessKey,
            secretKey: setupStorage.secretKey || undefined,
            bucket: setupStorage.bucket,
            region: setupStorage.region,
            pathStyle: setupStorage.pathStyle,
          }
        : null,
    })
    wizardStep.value = 'admin'
  }
  catch {
    // 错误已由拦截器 toast，停留当前步骤
  }
  finally {
    savingIntegrations.value = false
  }
}

function toastSetup(description: string) {
  useFaToast().error('请补全配置', { description })
}

// ==================== 站点与管理员（原有初始化表单） ====================

const loading = ref(false)

const form = useForm({
  validationSchema: toTypedSchema(
    z.object({
      siteName: z.string().min(1, '请输入站点名称'),
      adminUsername: z.string().min(1, '请输入管理员用户名').min(3, '管理员用户名至少3位'),
      adminEmail: z.string().min(1, '请输入管理员邮箱').email('邮箱格式不正确'),
      adminNickname: z.string().optional(),
      adminPassword: z.string().min(1, '请输入管理员密码').min(6, '密码长度为6到18位').max(18, '密码长度为6到18位'),
      adminConfirmPassword: z.string().min(1, '请再次输入密码'),
    }).refine(data => data.adminPassword === data.adminConfirmPassword, {
      message: '两次输入的密码不一致',
      path: ['adminConfirmPassword'],
    }),
  ),
  initialValues: {
    siteName: '',
    adminUsername: '',
    adminEmail: '',
    adminNickname: '',
    adminPassword: '',
    adminConfirmPassword: '',
  },
})

const goLogin = () => {
  localStorage.setItem('setupCompleted', 'true')
  // 守卫内的初始化状态是缓存值，初始化完成后必须失效，否则会被弹回 setup 页
  resetSetupStatus()
  router.push({ name: 'login' })
}

const onSubmit = form.handleSubmit((values) => {
  loading.value = true
  apiSetup.init({
    siteName: values.siteName,
    adminUsername: values.adminUsername,
    adminEmail: values.adminEmail,
    adminNickname: values.adminNickname,
    adminPassword: values.adminPassword,
    adminConfirmPassword: values.adminConfirmPassword,
  }).then(() => {
    goLogin()
  }).catch((error: any) => {
    // 后端提示已初始化（如重复提交、缓存过期）时直接引导登录，不再留在初始化页
    const message = error?.response?.data?.message || error?.message || ''
    if (message.includes('系统已初始化')) {
      goLogin()
    }
  }).finally(() => {
    loading.value = false
  })
})
</script>

<template>
  <div class="bg-banner" />
  <div class="setup-box">
    <div class="setup-banner">
      <img src="@/assets/images/logo.png" class="rounded h-8 inset-s-4 inset-t-4 absolute">
      <img src="@/assets/images/login-banner.png" class="banner">
    </div>
    <div class="setup-form flex-col-center">
      <div class="w-full p-12 flex-col-stretch-center min-h-500px">
        <div class="mb-8 space-y-2">
          <h3 class="text-4xl font-bold">
            {{ wizardStep === 'admin' ? '系统初始化 🚀' : '安装向导 🚀' }}
          </h3>
          <p class="text-sm text-muted-foreground lg:text-base">
            {{ wizardStep === 'admin' ? '配置站点信息并创建超级管理员账号' : wizardStep === 'integrations' ? '可选：配置邮件与对象存储，可稍后在系统设置中修改' : '检测到全新部署，先配置数据库与 Redis' }}
          </p>
        </div>

        <!-- 安装向导步骤指示（仅安装器模式显示） -->
        <div v-if="installerStatus" class="wizard-steps mb-6">
          <div
            v-for="(step, index) in wizardSteps"
            :key="step.key"
            class="wizard-step"
            :class="{ 'wizard-step-active': index === wizardActiveIndex, 'wizard-step-done': index < wizardActiveIndex }"
          >
            <FaIcon :name="index < wizardActiveIndex ? 'i-lucide:circle-check-big' : index === wizardActiveIndex ? 'i-lucide:circle-dot' : 'i-lucide:circle'" />
            <span>{{ step.label }}</span>
          </div>
        </div>

        <!-- 步骤一：中间件配置（安装器模式） -->
        <div v-if="wizardStep === 'middleware'" class="w-full space-y-4">
          <div class="bg-muted/50 rounded p-3 text-xs text-muted-foreground space-y-1">
            <div>Java {{ installerStatus?.javaVersion }} · {{ installerStatus?.dockerDeployment ? '容器部署' : '主机部署' }}</div>
            <div class="break-all">引导配置：{{ installerStatus?.bootstrapFileLocation }}</div>
          </div>

          <div v-if="discovery" class="space-y-1 text-xs text-muted-foreground">
            <div v-for="probe in discovery.mongo.filter(p => p.reachable)" :key="`m-${probe.target}`" class="flex items-center gap-1">
              <FaIcon name="i-lucide:database" class="text-emerald-600 dark:text-emerald-400" />
              <span>发现 MongoDB {{ probe.target }}{{ probe.version ? `（${probe.version}）` : '' }}{{ probe.authRequired ? '，需要认证' : '' }}</span>
            </div>
            <div v-for="probe in discovery.redis.filter(p => p.reachable)" :key="`r-${probe.target}`" class="flex items-center gap-1">
              <FaIcon name="i-lucide:zap" class="text-emerald-600 dark:text-emerald-400" />
              <span>发现 Redis {{ probe.target }}{{ probe.version ? `（${probe.version}）` : '' }}{{ probe.authRequired ? '，需要密码' : '' }}</span>
            </div>
          </div>

          <div class="space-y-1">
            <div class="text-sm font-medium">MongoDB 连接串</div>
            <FaInput v-model="mongoUri" type="text" placeholder="mongodb://账号:密码@主机:27017/yudream" class="w-full" @change="mongoProbeState = null">
              <template #start>
                <FaIcon name="i-lucide:database" />
              </template>
            </FaInput>
            <div v-if="mongoProbeState && !mongoProbeState.loading" class="text-xs" :class="mongoProbeState.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
              {{ mongoProbeState.text }}
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <div class="text-sm font-medium">Redis 地址</div>
              <FaInput v-model="redisHost" type="text" placeholder="localhost" class="w-full" @change="redisProbeState = null">
                <template #start>
                  <FaIcon name="i-lucide:server" />
                </template>
              </FaInput>
            </div>
            <div class="space-y-1">
              <div class="text-sm font-medium">端口</div>
              <FaInput v-model="redisPort" type="number" placeholder="6379" class="w-full" @change="redisProbeState = null" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <div class="text-sm font-medium">Redis 密码<span class="text-muted-foreground">（选填）</span></div>
              <FaInput v-model="redisPassword" type="password" placeholder="无密码留空" class="w-full" @change="redisProbeState = null" />
            </div>
            <div class="space-y-1">
              <div class="text-sm font-medium">数据库编号</div>
              <FaInput v-model="redisDatabase" type="number" placeholder="0" class="w-full" @change="redisProbeState = null" />
            </div>
          </div>

          <FaInput v-if="installerStatus?.setupTokenRequired" v-model="setupToken" type="password" placeholder="安装令牌（部署侧已启用 YUDREAM_SETUP_TOKEN）" class="w-full">
            <template #start>
              <FaIcon name="i-lucide:key-round" />
            </template>
          </FaInput>

          <div v-if="redisProbeState && !redisProbeState.loading" class="text-xs" :class="redisProbeState.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
            {{ redisProbeState.text }}
          </div>
          <div v-if="applyError" class="text-xs text-destructive">
            {{ applyError }}
          </div>

          <div class="grid grid-cols-2 gap-3 pt-2">
            <FaButton variant="outline" :loading="discoverLoading" @click="runDiscover">
              <FaIcon name="i-lucide:radar" /> 重新检测
            </FaButton>
            <FaButton variant="outline" :disabled="!mongoUri.trim()" :loading="mongoProbeState?.loading === true" @click="testMongo">
              <FaIcon name="i-lucide:plug-zap" /> 测试 MongoDB
            </FaButton>
          </div>
          <FaButton variant="outline" class="w-full" :disabled="!redisHost.trim()" :loading="redisProbeState?.loading === true" @click="testRedis">
            <FaIcon name="i-lucide:plug-zap" /> 测试 Redis
          </FaButton>
          <FaButton size="lg" class="w-full" :disabled="!canApply" @click="applyInstall">
            保存并安装
          </FaButton>
        </div>

        <!-- 步骤过渡：落盘中 -->
        <div v-else-if="wizardStep === 'applying'" class="w-full flex-col-center gap-3 py-16 text-muted-foreground">
          <FaIcon name="i-lucide:loader-circle" class="size-8 animate-spin" />
          <span class="text-sm">正在写入引导配置…</span>
        </div>

        <!-- 步骤过渡：等待重启 -->
        <div v-else-if="wizardStep === 'restarting'" class="w-full flex-col-center gap-3 py-16 text-muted-foreground">
          <FaIcon name="i-lucide:loader-circle" class="size-8 animate-spin" />
          <span class="text-sm">安装配置已写入，服务正在重启，请稍候…</span>
          <span class="text-xs">容器部署会自动拉起；本地开发需手动重启一次后端</span>
          <template v-if="restartTimedOut">
            <span class="text-xs text-destructive">等待超时，请确认后端已重启</span>
            <FaButton variant="outline" size="sm" @click="pollAfterRestart">
              重新检查
            </FaButton>
          </template>
        </div>

        <!-- 步骤三：可选集成配置（邮件/对象存储） -->
        <div v-else-if="wizardStep === 'integrations'" class="w-full space-y-4">
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <div class="text-sm font-medium">SMTP 主机<span class="text-muted-foreground">（选填）</span></div>
              <FaInput v-model="setupMail.host" type="text" placeholder="smtp.example.com" class="w-full">
                <template #start>
                  <FaIcon name="i-lucide:mail" />
                </template>
              </FaInput>
            </div>
            <div class="space-y-1">
              <div class="text-sm font-medium">端口</div>
              <FaInput v-model="setupMail.port" type="number" class="w-full" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <div class="text-sm font-medium">邮箱账号</div>
              <FaInput v-model="setupMail.username" type="text" placeholder="发件邮箱账号" class="w-full" />
            </div>
            <div class="space-y-1">
              <div class="text-sm font-medium">密码/授权码</div>
              <FaInput v-model="setupMail.password" type="password" placeholder="SMTP 授权码" class="w-full" />
            </div>
          </div>
          <div class="flex items-end gap-2">
            <div class="flex-1 space-y-1">
              <div class="text-sm font-medium">测试收件地址</div>
              <FaInput v-model="setupMailTestTo" type="text" placeholder="接收测试邮件" class="w-full" />
            </div>
            <FaButton variant="outline" :loading="testingSetupMail" @click="testSetupMail">
              测试邮件
            </FaButton>
          </div>
          <p v-if="setupMailResult" class="text-xs" :class="setupMailResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
            {{ setupMailResult.message }}
          </p>

          <div class="space-y-1">
            <div class="text-sm font-medium">对象存储 Endpoint<span class="text-muted-foreground">（选填，S3 兼容）</span></div>
            <FaInput v-model="setupStorage.endpoint" type="text" placeholder="http://localhost:9000" class="w-full">
              <template #start>
                <FaIcon name="i-lucide:hard-drive" />
              </template>
            </FaInput>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div class="space-y-1">
              <div class="text-sm font-medium">AccessKey</div>
              <FaInput v-model="setupStorage.accessKey" type="text" placeholder="对象存储 AccessKey" class="w-full" />
            </div>
            <div class="space-y-1">
              <div class="text-sm font-medium">SecretKey</div>
              <FaInput v-model="setupStorage.secretKey" type="password" placeholder="对象存储 SecretKey" class="w-full" />
            </div>
          </div>
          <div class="flex items-end gap-2">
            <div class="flex-1 space-y-1">
              <div class="text-sm font-medium">Bucket</div>
              <FaInput v-model="setupStorage.bucket" type="text" class="w-full" />
            </div>
            <FaButton variant="outline" :loading="testingSetupStorage" @click="testSetupStorage">
              测试连接
            </FaButton>
          </div>
          <p v-if="setupStorageResult" class="text-xs" :class="setupStorageResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
            {{ setupStorageResult.message }}
          </p>

          <div class="grid grid-cols-2 gap-3 pt-2">
            <FaButton variant="outline" :disabled="savingIntegrations" @click="wizardStep = 'admin'">
              稍后再配置
            </FaButton>
            <FaButton :loading="savingIntegrations" @click="finishIntegrations">
              保存并继续
            </FaButton>
          </div>
        </div>

        <!-- 步骤二：站点与管理员（原有表单，正常模式直接进入） -->
        <form v-else @submit="onSubmit">
          <FormField v-slot="{ componentField, errors }" name="siteName">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="站点名称" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:globe" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminUsername">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员用户名" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:user" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminEmail">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员邮箱" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:mail" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminNickname">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="text" placeholder="管理员昵称（选填）" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:smile" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminPassword">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="password" placeholder="管理员密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:lock" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FormField v-slot="{ componentField, errors }" name="adminConfirmPassword">
            <FormItem class="pb-6 relative space-y-0">
              <FormControl>
                <FaInput type="password" placeholder="确认密码" class="w-full" :class="{ 'border-destructive': errors.length }" v-bind="componentField">
                  <template #start>
                    <FaIcon name="i-lucide:lock" />
                  </template>
                </FaInput>
              </FormControl>
              <Transition enter-active-class="transition-opacity" enter-from-class="opacity-0" leave-active-class="transition-opacity" leave-to-class="opacity-0">
                <FormMessage class="text-xs m-0 bottom-1 absolute" />
              </Transition>
            </FormItem>
          </FormField>
          <FaButton :loading="loading" size="lg" class="mt-4 w-full" type="submit">
            完成初始化
          </FaButton>
        </form>
      </div>
    </div>
  </div>
  <AppCopyright class="copyright" />
</template>

<style scoped>
.bg-banner {
  position: fixed;
  z-index: 0;
  width: 100%;
  height: 100%;
  background:
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat,
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat;
  background-position: 100% 100%, 0% 0%;
  background-size: 200vw 200vh;
  filter: blur(100px);
}

.setup-box {
  position: absolute;
  top: 50%;
  left: 50%;
  display: flex;
  overflow: hidden;
  background-color: oklch(var(--background));
  transform: translateX(-50%) translateY(-50%);
  --uno: shadow-md rounded-md;
}

.setup-banner {
  --uno: bg-muted dark:bg-muted/30;
  position: relative;
  width: 450px;
  overflow: hidden;
}

.setup-banner::before {
  position: absolute;
  width: 100%;
  height: 100%;
  content: "";
  background:
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat,
    radial-gradient(closest-side, oklch(var(--border) / 10%) 30%, oklch(var(--primary) / 20%) 30%, oklch(var(--border) / 30%) 50%) no-repeat;
  background-position: 100% 100%, 0% 0%;
  background-size: 200vw 200vh;
  filter: blur(100px);
}

.setup-banner .banner {
  position: absolute;
  top: 50%;
  width: 100%;
  transform: translateY(-50%);
}

.setup-form {
  width: 500px;
}

.wizard-steps {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wizard-step {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: oklch(var(--muted-foreground));
  font-size: 12px;
}

.wizard-step-active {
  color: oklch(var(--foreground));
  font-weight: 500;
}

.wizard-step-done {
  color: oklch(var(--primary));
}

.copyright {
  position: absolute;
  bottom: 0;
  width: 100%;
  padding: 20px;
  margin: 0;
}

[data-mode="mobile"] {
  .setup-box {
    position: relative;
    flex-direction: column;
    justify-content: start;
    width: 100%;
    transform: none;
    top: auto;
    left: auto;
  }

  .setup-banner {
    width: 100%;
    padding: 20px 0;
  }

  .setup-banner .banner {
    position: relative;
    top: inherit;
    right: inherit;
    display: inherit;
    width: 100%;
    max-width: 375px;
    margin: 0 auto;
    transform: translateY(0);
  }

  .setup-form {
    width: 100%;
  }

  .copyright {
    position: relative;
  }
}
</style>
