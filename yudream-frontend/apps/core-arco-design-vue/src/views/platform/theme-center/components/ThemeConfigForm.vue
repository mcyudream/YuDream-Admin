<script setup lang="ts">
import type { ThemeConfigSchema } from '@/api/modules/platform-theme'
import ThemeConfigField from './ThemeConfigField.vue'

const props = defineProps<{
  schema: ThemeConfigSchema
  values: Record<string, any>
  secretConfigured?: Record<string, boolean>
  pluginCode?: string
}>()

const emit = defineEmits<{
  'update:values': [values: Record<string, any>]
}>()

const activeSection = ref('')

function updateField(key: string, value: any) {
  emit('update:values', { ...props.values, [key]: value })
}

function scrollToSection(code: string) {
  activeSection.value = code
  document.getElementById(`theme-config-section-${code}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(() => {
  activeSection.value = props.schema.sections?.[0]?.code ?? ''
})
</script>

<template>
  <div class="theme-config-form">
    <aside class="theme-config-form__nav">
      <button
        v-for="section in schema.sections"
        :key="section.code"
        type="button"
        class="theme-config-form__nav-item"
        :class="{ active: activeSection === section.code }"
        @click="scrollToSection(section.code)"
      >
        {{ section.title }}
      </button>
    </aside>
    <div class="theme-config-form__sections">
      <section
        v-for="section in schema.sections"
        :id="`theme-config-section-${section.code}`"
        :key="section.code"
        class="theme-config-form__section"
      >
        <header class="theme-config-form__section-head">
          <h3>{{ section.title }}</h3>
          <p v-if="section.description">
            {{ section.description }}
          </p>
        </header>
        <div class="theme-config-form__section-body">
          <ThemeConfigField
            v-for="field in section.fields"
            :key="field.key"
            :field="field"
            :model-value="values[field.key]"
            :secret-configured="field.secret ? !!secretConfigured?.[field.key] : false"
            :plugin-code="pluginCode"
            @update:model-value="updateField(field.key, $event)"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.theme-config-form {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}

.theme-config-form__nav {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 160px;
  padding: 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.theme-config-form__nav-item {
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-2);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.theme-config-form__nav-item:hover {
  background: var(--color-fill-2);
}

.theme-config-form__nav-item.active {
  background: var(--color-fill-3);
  color: var(--color-text-1);
  font-weight: 500;
}

.theme-config-form__sections {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.theme-config-form__section {
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  scroll-margin-top: 16px;
}

.theme-config-form__section-head {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border-1);
}

.theme-config-form__section-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.theme-config-form__section-head p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.theme-config-form__section-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
}

@media (max-width: 768px) {
  .theme-config-form {
    flex-direction: column;
  }

  .theme-config-form__nav {
    position: static;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
  }
}
</style>
