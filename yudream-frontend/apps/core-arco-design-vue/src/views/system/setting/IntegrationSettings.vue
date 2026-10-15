<script setup lang="ts">
import type { IntegrationConfigSetting } from '@/api/modules/settings'
import apiSettings from '@/api/modules/settings'

const toast = useYdToast()

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

/** YdSelect 取值为字符串，SSL/STARTTLS 与布尔字段互相映射 */
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
/** 保存由设置页页头统一触发 */
defineExpose({ save })
</script>

<template>
  <YdLoadingOverlay :loading="loading">
    <YdSettingsSection
      title="集成配置"
      description="邮件与对象存储运行时配置：保存后立即生效，密码与密钥加密存储且永不回显。"
    >
      <div class="flex flex-col gap-6">
        <div>
          <div class="font-medium mb-2 flex items-center">
            邮件服务（SMTP）
            <YdTag :variant="mail.source === 'custom' ? 'default' : 'secondary'" class="ms-2">
              {{ mail.source === 'custom' ? '已入库' : '环境变量兜底' }}
            </YdTag>
          </div>
          <YdForm :model="mail" layout="vertical" class="space-y-0">
            <div class="flex flex-col gap-4 max-w-xl">
              <YdFormItem label="SMTP 主机" required>
                <YdInput v-model="mail.host" class="w-full" placeholder="smtp.example.com" />
              </YdFormItem>
              <YdFormItem label="端口">
                <YdInput v-model="mail.port" type="number" class="w-full" placeholder="465" />
              </YdFormItem>
              <YdFormItem label="账号">
                <YdInput v-model="mail.username" class="w-full" placeholder="发件邮箱账号" />
              </YdFormItem>
              <YdFormItem label="密码" :extra="mail.passwordSet ? '已设置；留空保持不变' : '未设置'">
                <YdInput v-model="mail.password" type="password" class="w-full" placeholder="留空保持不变" />
              </YdFormItem>
              <YdFormItem label="发件人">
                <YdInput v-model="mail.from" class="w-full" placeholder="留空默认使用账号" />
              </YdFormItem>
              <YdFormItem label="加密方式">
                <YdSelect
                  v-model="mailEncryptionOption"
                  :options="[{ label: 'SSL（465 常用）', value: 'ssl' }, { label: 'STARTTLS（587 常用）', value: 'starttls' }]"
                  class="w-full"
                />
              </YdFormItem>
            </div>
            <YdFormItem label="测试收件地址">
              <div class="flex gap-2 w-full items-center">
                <YdInput v-model="mailTestTo" class="flex-1 min-w-0" placeholder="接收测试邮件的地址" />
                <YdButton variant="outline" :loading="testingMail" @click="testMail">
                  <YdIcon name="morph:send" />
                  发送测试邮件
                </YdButton>
              </div>
            </YdFormItem>
            <p v-if="mailTestResult" class="text-xs" :class="mailTestResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
              {{ mailTestResult.message }}
            </p>
          </YdForm>
        </div>

        <div>
          <div class="font-medium mb-2 flex items-center">
            对象存储（S3 兼容）
            <YdTag :variant="storage.source === 'custom' ? 'default' : 'secondary'" class="ms-2">
              {{ storage.source === 'custom' ? '已入库' : '环境变量兜底' }}
            </YdTag>
          </div>
          <YdForm :model="storage" layout="vertical" class="space-y-0">
            <div class="flex flex-col gap-4 max-w-xl">
              <YdFormItem label="Endpoint" required>
                <YdInput v-model="storage.endpoint" class="w-full" placeholder="http://localhost:9000" />
              </YdFormItem>
              <YdFormItem label="AccessKey">
                <YdInput v-model="storage.accessKey" class="w-full" />
              </YdFormItem>
              <YdFormItem label="SecretKey" :extra="storage.secretKeySet ? '已设置；留空保持不变' : '未设置'">
                <YdInput v-model="storage.secretKey" type="password" class="w-full" placeholder="留空保持不变" />
              </YdFormItem>
              <YdFormItem label="Bucket">
                <YdInput v-model="storage.bucket" class="w-full" placeholder="yudream-admin" />
              </YdFormItem>
              <YdFormItem label="Region">
                <YdInput v-model="storage.region" class="w-full" placeholder="us-east-1" />
              </YdFormItem>
            </div>
            <YdFormItem label="Path-Style 访问">
              <div class="flex gap-2 w-full items-center justify-between">
                <YdSwitch v-model="storage.pathStyle" />
                <YdButton variant="outline" :loading="testingStorage" @click="testStorage">
                  <YdIcon name="morph:plug" />
                  测试连接
                </YdButton>
              </div>
            </YdFormItem>
            <p v-if="storageTestResult" class="text-xs" :class="storageTestResult.ok ? 'text-emerald-600 dark:text-emerald-400' : 'text-destructive'">
              {{ storageTestResult.message }}
            </p>
          </YdForm>
        </div>
      </div>
    </YdSettingsSection>
  </YdLoadingOverlay>
</template>
