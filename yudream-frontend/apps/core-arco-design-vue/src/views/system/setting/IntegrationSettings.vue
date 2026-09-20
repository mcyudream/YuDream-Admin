<script setup lang="ts">
import type { IntegrationConfigSetting } from '@/api/modules/settings'
import apiSettings from '@/api/modules/settings'

const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const testingMail = ref(false)
const testingStorage = ref(false)

const mail = reactive({
  host: '',
  port: 465,
  username: '',
  password: '',
  from: '',
  ssl: true,
  starttls: false,
  passwordSet: false,
  source: 'environment',
})

const storage = reactive({
  endpoint: '',
  accessKey: '',
  secretKey: '',
  bucket: '',
  region: 'us-east-1',
  pathStyle: true,
  secretKeySet: false,
  source: 'environment',
})

const mailTestTo = ref('')
const mailTestResult = ref<{ ok: boolean, message: string } | null>(null)
const storageTestResult = ref<{ ok: boolean, message: string } | null>(null)

/** FaSelect 取值为字符串，SSL/STARTTLS 与布尔字段互相映射 */
const mailEncryptionOption = computed({
  get: () => mail.ssl ? 'ssl' : 'starttls',
  set: (value: string) => {
    mail.ssl = value === 'ssl'
    mail.starttls = value === 'starttls'
  },
})

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiSettings.integrations()
    assignForm(res.data)
  }
  finally {
    loading.value = false
  }
}

function assignForm(data: IntegrationConfigSetting) {
  Object.assign(mail, {
    host: data.mail.host || '',
    port: data.mail.port || 465,
    username: data.mail.username || '',
    password: '',
    from: data.mail.from || '',
    ssl: data.mail.ssl,
    starttls: data.mail.starttls,
    passwordSet: data.mail.passwordSet,
    source: data.mail.source,
  })
  Object.assign(storage, {
    endpoint: data.storage.endpoint || '',
    accessKey: data.storage.accessKey || '',
    secretKey: '',
    bucket: data.storage.bucket || '',
    region: data.storage.region || 'us-east-1',
    pathStyle: data.storage.pathStyle,
    secretKeySet: data.storage.secretKeySet,
    source: data.storage.source,
  })
}

async function testMail() {
  if (!mail.host) {
    toast.error('请先填写 SMTP 主机')
    return
  }
  if (!mailTestTo.value) {
    toast.error('请填写测试收件地址')
    return
  }
  testingMail.value = true
  mailTestResult.value = null
  try {
    const res = await apiSettings.testMail({
      host: mail.host,
      port: mail.port,
      username: mail.username,
      password: mail.password || undefined,
      from: mail.from,
      ssl: mail.ssl,
      starttls: mail.starttls,
      to: mailTestTo.value,
    })
    mailTestResult.value = { ok: res.data.ok, message: res.data.message }
  }
  catch (error: any) {
    mailTestResult.value = { ok: false, message: error?.message || '测试失败' }
  }
  finally {
    testingMail.value = false
  }
}

async function testStorage() {
  if (!storage.endpoint) {
    toast.error('请先填写 Endpoint')
    return
  }
  testingStorage.value = true
  storageTestResult.value = null
  try {
    const res = await apiSettings.testStorage({
      endpoint: storage.endpoint,
      accessKey: storage.accessKey,
      secretKey: storage.secretKey || undefined,
      bucket: storage.bucket,
      region: storage.region,
      pathStyle: storage.pathStyle,
      autoCreate: true,
    })
    storageTestResult.value = { ok: res.data.ok, message: res.data.message }
  }
  catch (error: any) {
    storageTestResult.value = { ok: false, message: error?.message || '测试失败' }
  }
  finally {
    testingStorage.value = false
  }
}

async function save() {
  if (!mail.host) {
    toast.error('SMTP 主机不能为空')
    return
  }
  if (!storage.endpoint) {
    toast.error('对象存储 Endpoint 不能为空')
    return
  }
  saving.value = true
  try {
    const res = await apiSettings.updateIntegrations({
      mail: {
        host: mail.host,
        port: mail.port,
        username: mail.username,
        password: mail.password || undefined,
        from: mail.from,
        ssl: mail.ssl,
        starttls: mail.starttls,
      },
      storage: {
        endpoint: storage.endpoint,
        accessKey: storage.accessKey,
        secretKey: storage.secretKey || undefined,
        bucket: storage.bucket,
        region: storage.region,
        pathStyle: storage.pathStyle,
      },
    })
    assignForm(res.data)
    mailTestResult.value = null
    storageTestResult.value = null
    toast.success('集成配置已保存，立即生效')
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="mt-6 space-y-6">
    <div>
      <FaDivider>
        集成配置
      </FaDivider>
      <p class="text-xs text-muted-foreground">
        邮件与对象存储运行时配置：保存后立即生效，密码与密钥加密存储且永不回显。
      </p>
    </div>

    <div class="grid grid-cols-1 gap-5 xl:grid-cols-2">
      <div>
        <div class="mb-2 flex items-center font-medium">
          邮件服务（SMTP）
          <FaTag :variant="mail.source === 'custom' ? 'default' : 'secondary'" class="ms-2">
            {{ mail.source === 'custom' ? '已入库' : '环境变量兜底' }}
          </FaTag>
        </div>
        <a-form :model="mail" layout="vertical" class="space-y-0">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="SMTP 主机" required>
            <FaInput v-model="mail.host" class="w-full" placeholder="smtp.example.com" />
          </a-form-item>
          <a-form-item label="端口">
            <FaInput v-model="mail.port" type="number" class="w-full" placeholder="465" />
          </a-form-item>
          <a-form-item label="账号">
            <FaInput v-model="mail.username" class="w-full" placeholder="发件邮箱账号" />
          </a-form-item>
          <a-form-item label="密码" :extra="mail.passwordSet ? '已设置；留空保持不变' : '未设置'">
            <FaInput v-model="mail.password" type="password" class="w-full" placeholder="留空保持不变" />
          </a-form-item>
          <a-form-item label="发件人">
            <FaInput v-model="mail.from" class="w-full" placeholder="留空默认使用账号" />
          </a-form-item>
          <a-form-item label="加密方式">
            <FaSelect
              v-model="mailEncryptionOption"
              :options="[{ label: 'SSL（465 常用）', value: 'ssl' }, { label: 'STARTTLS（587 常用）', value: 'starttls' }]"
              class="w-full"
            />
          </a-form-item>
        </div>
        <div class="flex items-end gap-2">
          <a-form-item label="测试收件地址" class="flex-1">
            <FaInput v-model="mailTestTo" class="w-full" placeholder="接收测试邮件的地址" />
          </a-form-item>
          <FaButton variant="outline" :loading="testingMail" @click="testMail">
            <FaIcon name="i-ri:send-plane-line" />
            发送测试邮件
          </FaButton>
        </div>
        <p v-if="mailTestResult" class="text-xs" :class="mailTestResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
          {{ mailTestResult.message }}
        </p>
        </a-form>
      </div>

      <div>
        <div class="mb-2 flex items-center font-medium">
          对象存储（S3 兼容）
          <FaTag :variant="storage.source === 'custom' ? 'default' : 'secondary'" class="ms-2">
            {{ storage.source === 'custom' ? '已入库' : '环境变量兜底' }}
          </FaTag>
        </div>
        <a-form :model="storage" layout="vertical" class="space-y-0">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="Endpoint" required class="md:col-span-2">
            <FaInput v-model="storage.endpoint" class="w-full" placeholder="http://localhost:9000" />
          </a-form-item>
          <a-form-item label="AccessKey">
            <FaInput v-model="storage.accessKey" class="w-full" />
          </a-form-item>
          <a-form-item label="SecretKey" :extra="storage.secretKeySet ? '已设置；留空保持不变' : '未设置'">
            <FaInput v-model="storage.secretKey" type="password" class="w-full" placeholder="留空保持不变" />
          </a-form-item>
          <a-form-item label="Bucket">
            <FaInput v-model="storage.bucket" class="w-full" placeholder="yudream-admin" />
          </a-form-item>
          <a-form-item label="Region">
            <FaInput v-model="storage.region" class="w-full" placeholder="us-east-1" />
          </a-form-item>
        </div>
        <div class="flex items-center justify-between">
          <a-form-item label="Path-Style 访问">
            <FaSwitch v-model="storage.pathStyle" />
          </a-form-item>
          <FaButton variant="outline" :loading="testingStorage" @click="testStorage">
            <FaIcon name="i-ri:plug-line" />
            测试连接
          </FaButton>
        </div>
        <p v-if="storageTestResult" class="text-xs" :class="storageTestResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
          {{ storageTestResult.message }}
        </p>
        </a-form>
      </div>
    </div>

    <div class="flex justify-end">
      <FaButton v-auth="'system:setting:edit'" :loading="saving" @click="save">
        <FaIcon name="i-ri:save-3-line" />
        保存集成配置
      </FaButton>
    </div>
  </div>
</template>
