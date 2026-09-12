<script setup lang="ts">
import type { MarketSourceType, PluginMarketSourcePayload } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

defineOptions({ name: 'PluginMarketplaceAddSource' })

const router = useRouter()
const toast = useFaToast()

const saving = ref(false)
const testing = ref(false)
const testMessage = ref('')
const testOk = ref(false)
const form = reactive<PluginMarketSourcePayload>({
  code: '',
  name: '',
  type: 'V2_API',
  rootUrl: '',
  token: '',
  sortOrder: 100,
})

const sourceTypeOptions: { label: string, value: MarketSourceType }[] = [
  { label: 'v2 协议源', value: 'V2_API' },
  { label: '静态索引（legacy）', value: 'STATIC_INDEX' },
]

function back() {
  router.push('/platform/plugin-marketplace')
}

async function testForm() {
  const rootUrl = form.rootUrl?.trim()
  if (!rootUrl) {
    toast.error('请先填写市场源地址')
    return
  }
  testing.value = true
  testMessage.value = ''
  try {
    const res = await apiMarketSource.test({
      rootUrl,
      token: form.token || undefined,
      type: form.type === 'LOCAL' ? undefined : form.type,
    })
    testOk.value = res.data.ok
    testMessage.value = res.data.message || (res.data.ok ? '连接成功' : '连接失败')
  }
  catch {
    testOk.value = false
    testMessage.value = '测试请求失败，请检查地址与网络'
  }
  finally {
    testing.value = false
  }
}

async function save() {
  if (!form.code?.trim()) {
    toast.error('请填写市场源标识')
    return
  }
  if (!form.rootUrl?.trim()) {
    toast.error('请填写市场源地址')
    return
  }
  saving.value = true
  try {
    await apiMarketSource.create({ ...form })
    toast.success('市场源已添加，可在市场源管理中同步目录')
    back()
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <FaPageHeader title="添加市场源" class="mb-0">
      <template #description>
        订阅远程插件市场。不需要启用「插件市场源」能力；该能力只提供本机源与公开社区。
      </template>
      <FaButton variant="outline" @click="back">
        <FaIcon name="i-ri:arrow-left-line" />
        返回插件市场
      </FaButton>
      <FaButton :loading="saving" @click="save">
        <FaIcon name="i-ri:save-line" />
        保存
      </FaButton>
    </FaPageHeader>
    <FaPageMain>
      <FaCard class="max-w-3xl">
        <a-form :model="form" layout="vertical">
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
            <a-form-item label="市场源标识" required>
              <FaInput v-model="form.code" placeholder="小写字母、数字或连字符，如 company-mirror" />
            </a-form-item>
            <a-form-item label="名称" required>
              <FaInput v-model="form.name" placeholder="市场源显示名称" />
            </a-form-item>
          </div>
          <a-form-item label="源类型">
            <FaSelect v-model="form.type" :options="sourceTypeOptions" />
          </a-form-item>
          <a-form-item :label="form.type === 'V2_API' ? '源地址（v2 源基址）' : '源地址（index.json 完整地址）'" required>
            <div class="flex gap-2">
              <FaInput
                v-model="form.rootUrl"
                :placeholder="form.type === 'V2_API' ? 'https://example.com/api/public/plugin-market' : 'https://example.com/market/index.json'"
                class="flex-1"
              />
              <FaButton variant="outline" :loading="testing" @click="testForm">
                测试连接
              </FaButton>
            </div>
            <div v-if="testMessage" class="mt-1 flex items-center gap-1">
              <FaTag :variant="testOk ? 'default' : 'destructive'">
                {{ testOk ? '成功' : '失败' }}
              </FaTag>
              <span class="text-xs text-secondary-foreground/60">{{ testMessage }}</span>
            </div>
          </a-form-item>
          <div class="grid grid-cols-1 gap-x-4 md:grid-cols-[1fr_140px]">
            <a-form-item label="访问令牌（可选）">
              <FaInput v-model="form.token" type="password" placeholder="私有源的 Bearer 令牌" />
            </a-form-item>
            <a-form-item label="排序">
              <a-input-number v-model="form.sortOrder" :min="0" class="w-full" />
            </a-form-item>
          </div>
        </a-form>
      </FaCard>
    </FaPageMain>
  </div>
</template>
