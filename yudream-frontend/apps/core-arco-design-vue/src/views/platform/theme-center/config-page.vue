<script setup lang="ts">
import type { ThemeConfig } from '@/api/modules/platform-theme'
import apiTheme from '@/api/modules/platform-theme'
import ThemeConfigForm from './components/ThemeConfigForm.vue'

defineOptions({ name: 'ThemeCenterConfigPage' })

const route = useRoute()
const router = useRouter()
const toast = useFaToast()

const themeCode = computed(() => String(route.params.theme ?? ''))
const loading = ref(false)
const saving = ref(false)
const config = ref<ThemeConfig | null>(null)
const values = ref<Record<string, any>>({})
const themeName = ref('')

async function load() {
  if (!themeCode.value) {
    return
  }
  loading.value = true
  try {
    const res = await apiTheme.config(themeCode.value)
    config.value = res.data
    values.value = { ...(res.data.values ?? {}) }
    apiTheme.overview().then((overview) => {
      themeName.value = overview.data.themes.find(item => item.code === themeCode.value)?.name ?? ''
    }).catch(() => {})
  }
  catch {
    toast.error('主题配置加载失败')
  }
  finally {
    loading.value = false
  }
}

async function save() {
  if (!themeCode.value || saving.value) {
    return
  }
  saving.value = true
  try {
    const res = await apiTheme.saveConfig(themeCode.value, values.value)
    config.value = res.data
    values.value = { ...(res.data.values ?? {}) }
    toast.success('配置已保存，公开站即时生效')
  }
  catch {
    toast.error('保存失败，请检查配置项')
  }
  finally {
    saving.value = false
  }
}

function back() {
  router.push('/platform/theme-center')
}

onMounted(load)
</script>

<template>
  <div>
    <FaPageHeader :title="themeName ? `主题配置：${themeName}` : '主题配置'" class="theme-config-header">
      <FaButton variant="outline" @click="back">
        <FaIcon name="i-ri:arrow-left-line" />
        返回主题中心
      </FaButton>
      <FaButton v-auth="'platform:theme-center:config'" :loading="saving" :disabled="loading || !config" @click="save">
        <FaIcon name="i-ri:save-line" />
        保存配置
      </FaButton>
    </FaPageHeader>
    <FaPageMain>
      <div v-loading="loading" class="theme-config-page">
        <ThemeConfigForm
          v-if="config && config.schema.sections?.length"
          v-model:values="values"
          :schema="config.schema"
          :secret-configured="config.secretConfigured"
          :plugin-code="config.themeCode"
        />
        <div v-else-if="!loading" class="theme-config-page__empty">
          该主题未声明可配置项。
        </div>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.theme-config-page {
  min-height: 200px;
}

.theme-config-page__empty {
  padding: 48px 16px;
  text-align: center;
  color: var(--color-text-3);
  font-size: 14px;
}
</style>
