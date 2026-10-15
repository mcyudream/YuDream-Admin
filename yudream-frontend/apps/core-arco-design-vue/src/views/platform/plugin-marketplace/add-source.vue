<script setup lang="ts">
import type { MarketSourceType, PluginMarketSourcePayload } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

defineOptions({ name: 'PluginMarketplaceAddSource' })

const router = useRouter()
const toast = useYdToast()

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
    <YdPageHeader title="添加市场源" class="mb-0">
      <template #description>
        订阅远程插件市场。不需要启用「插件市场源」能力；该能力只提供本机源与公开社区。
      </template>
      <YdButton variant="ghost" @click="back">
        <YdIcon name="morph:arrow-left" />
        返回插件市场
      </YdButton>
      <YdButton :loading="saving" @click="save">
        <YdIcon name="morph:save" />
        保存
      </YdButton>
    </YdPageHeader>
    <YdPageMain>
      <YdCard class="max-w-3xl">
        <div class="text-sm text-secondary-foreground/60 mb-4 px-3 py-2 border border-[var(--color-border-2)] rounded-lg bg-[var(--color-fill-1)] flex gap-2 items-start">
          <YdIcon name="morph:info" class="mt-0.5 shrink-0" />
          <span>填写远程源地址后先测试连接；添加后可在市场源管理中同步目录。</span>
        </div>
        <YdForm :model="form" layout="vertical">
          <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
            <YdFormItem label="市场源标识" required>
              <YdInput v-model="form.code" placeholder="小写字母、数字或连字符，如 company-mirror" />
            </YdFormItem>
            <YdFormItem label="名称" required>
              <YdInput v-model="form.name" placeholder="市场源显示名称" />
            </YdFormItem>
          </div>
          <YdFormItem label="源类型">
            <YdSelect v-model="form.type" :options="sourceTypeOptions" />
          </YdFormItem>
          <YdFormItem :label="form.type === 'V2_API' ? '源地址（v2 源基址）' : '源地址（index.json 完整地址）'" required>
            <div class="flex flex-wrap gap-2 sm:flex-nowrap">
              <YdInput
                v-model="form.rootUrl"
                :placeholder="form.type === 'V2_API' ? 'https://example.com/api/public/plugin-market' : 'https://example.com/market/index.json'"
                class="flex-1"
              />
              <YdButton variant="outline" :loading="testing" @click="testForm">
                测试连接
              </YdButton>
            </div>
            <div v-if="testMessage" class="mt-1 flex gap-1 items-center">
              <YdTag :variant="testOk ? 'default' : 'destructive'">
                {{ testOk ? '成功' : '失败' }}
              </YdTag>
              <span class="text-xs text-secondary-foreground/60">{{ testMessage }}</span>
            </div>
          </YdFormItem>
          <div class="gap-x-4 grid grid-cols-1 md:grid-cols-[1fr_140px]">
            <YdFormItem label="访问令牌（可选）">
              <YdInput v-model="form.token" type="password" placeholder="私有源的 Bearer 令牌" />
            </YdFormItem>
            <YdFormItem label="排序">
              <YdNumberField :model-value="form.sortOrder ?? 100" :min="0" class="w-full" @update:model-value="form.sortOrder = $event" />
            </YdFormItem>
          </div>
        </YdForm>
      </YdCard>
    </YdPageMain>
  </div>
</template>
