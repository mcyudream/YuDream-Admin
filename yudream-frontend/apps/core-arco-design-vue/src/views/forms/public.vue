<script setup lang="ts">
import type { DynamicForm } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'
import { useAppFeatureStore } from '@/store/modules/app/features'
import DynamicFormRenderer from '@/views/platform/form/components/DynamicFormRenderer.vue'

interface DynamicFormRendererExpose {
  resetFields?: () => void
}

const route = useRoute()
const appFeatureStore = useAppFeatureStore()
const toast = useFaToast()

const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const form = ref<DynamicForm | null>(null)
const rendererRef = ref<DynamicFormRendererExpose>()

const code = computed(() => String(route.params.code || ''))
const brandHref = computed(() => appFeatureStore.cmsEnabled ? '/site' : '/login')

onMounted(async () => {
  await appFeatureStore.load()
  await load()
})

watch(code, load)

async function load() {
  if (!code.value) {
    return
  }
  loading.value = true
  submitted.value = false
  try {
    const res = await apiForm.publicForm(code.value)
    form.value = res.data
  }
  finally {
    loading.value = false
  }
}

async function submit(data: Record<string, unknown>) {
  if (!form.value) {
    return
  }
  submitting.value = true
  try {
    await apiForm.submitPublic(form.value.code, data)
    submitted.value = true
    toast.success('提交成功')
    rendererRef.value?.resetFields?.()
  }
  finally {
    submitting.value = false
  }
}

function invalid() {
  toast.error('请检查表单必填项')
}
</script>

<template>
  <main class="public-form-page">
    <section class="public-form-shell">
      <header class="public-form-header">
        <a :href="brandHref" class="brand-link">YuDream</a>
        <span>动态表单</span>
      </header>

      <section class="public-form-panel">
        <div v-if="loading" class="public-state">
          正在加载表单...
        </div>
        <template v-else-if="form">
          <DynamicFormRenderer
            ref="rendererRef"
            :form="form"
            :submitted="submitted"
            :submitting="submitting"
            @invalid="invalid"
            @submit="submit"
          />
        </template>
        <div v-else class="public-state">
          表单不存在或尚未发布。
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.public-form-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(22, 93, 255, 0.12), transparent 30%),
    linear-gradient(180deg, #f7f8fb 0%, #eef2f7 100%);
  color: #172033;
}

.public-form-shell {
  width: min(920px, calc(100% - 32px));
  margin: 0 auto;
  padding: 24px 0 56px;
}

.public-form-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  color: #566176;
}

.brand-link {
  color: #172033;
  font-weight: 800;
  text-decoration: none;
}

.public-form-panel {
  display: grid;
  gap: 20px;
  margin-top: 28px;
  padding: 28px;
  border: 1px solid rgba(42, 53, 71, 0.08);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 24px 70px rgba(21, 31, 48, 0.1);
}

.public-state {
  display: grid;
  min-height: 260px;
  place-items: center;
  color: #667085;
}

@media (max-width: 640px) {
  .public-form-panel {
    padding: 18px;
  }
}
</style>
