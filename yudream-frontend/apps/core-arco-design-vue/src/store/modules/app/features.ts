import type { FrontendFeature } from '@/api/modules/settings'
import apiSettings from '@/api/modules/settings'

function defaultFeatures(): FrontendFeature {
  return {
    apiKeyEnabled: false,
    passkeyEnabled: false,
    oauthServerEnabled: false,
    oauthClientEnabled: false,
    capabilities: {},
  }
}

export const useAppFeatureStore = defineStore('appFeature', () => {
  const features = ref<FrontendFeature>(defaultFeatures())
  const loaded = ref(false)
  const loading = ref(false)
  let pending: Promise<void> | null = null

  async function load(force = false) {
    if (loaded.value && !force) {
      return
    }
    if (pending && !force) {
      return pending
    }
    loading.value = true
    pending = apiSettings.features()
      .then((res) => {
        features.value = {
          ...defaultFeatures(),
          ...res.data,
          capabilities: res.data.capabilities || {},
        }
        loaded.value = true
      })
      .catch(() => {
        features.value = defaultFeatures()
        loaded.value = true
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }

  function capabilityEnabled(code: string) {
    return features.value.capabilities?.[code] === true
  }

  const apiKeyEnabled = computed(() => features.value.apiKeyEnabled)
  const passkeyEnabled = computed(() => features.value.passkeyEnabled)
  const oauthServerEnabled = computed(() => features.value.oauthServerEnabled)
  const oauthClientEnabled = computed(() => features.value.oauthClientEnabled)
  const cmsEnabled = computed(() => capabilityEnabled('cms'))
  const formEnabled = computed(() => capabilityEnabled('form'))

  return {
    features,
    loaded,
    loading,
    apiKeyEnabled,
    passkeyEnabled,
    oauthServerEnabled,
    oauthClientEnabled,
    cmsEnabled,
    formEnabled,
    capabilityEnabled,
    load,
  }
})
