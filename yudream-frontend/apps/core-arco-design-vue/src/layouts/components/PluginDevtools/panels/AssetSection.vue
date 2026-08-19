<script setup lang="ts">
const props = withDefaults(defineProps<{
  title: string
  icon?: string
  count?: number
  defaultOpen?: boolean
}>(), {
  icon: '',
  count: undefined,
  defaultOpen: false,
})

const open = ref(props.defaultOpen)
</script>

<template>
  <FaCollapsible v-model="open" class="asset-section">
    <template #trigger>
      <div class="asset-section__trigger">
        <FaIcon v-if="icon" :name="icon" class="size-4" />
        <span class="asset-section__title">{{ title }}</span>
        <FaTag variant="secondary" class="text-xs">
          {{ count ?? 0 }}
        </FaTag>
        <FaIcon :name="open ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="size-4" />
      </div>
    </template>
    <div class="asset-section__body">
      <slot />
    </div>
  </FaCollapsible>
</template>

<style scoped>
.asset-section {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.asset-section__trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 12px;
  color: var(--color-text-2);
  cursor: pointer;
  user-select: none;
}

.asset-section__title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
}

.asset-section__body {
  padding: 4px 12px 10px;
}
</style>
