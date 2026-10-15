<script setup lang="ts">
import type { YdStateIllustrationName } from './YdStateIllustration.vue'
import { computed } from 'vue'
import FaIcon from '../basic/icon/index.vue'
import YdStateIllustration from './YdStateIllustration.vue'
import './extended.css'

const props = withDefaults(defineProps<{
  icon?: string
  title?: string
  description?: string
  illustration?: YdStateIllustrationName | false
  illustrationSize?: 'sm' | 'md' | 'lg'
}>(), { icon: 'morph:inbox', title: '暂无内容', illustration: false, illustrationSize: 'md' })

const illustrationName = computed(() => props.illustration === false ? undefined : props.illustration)
</script>

<template>
  <div class="yd-empty yd-pad" :class="{ 'yd-empty--illustration': illustrationName || $slots.illustration }">
    <template v-if="$slots.illustration || illustrationName">
      <slot v-if="$slots.illustration" name="illustration" />
      <YdStateIllustration v-else-if="illustrationName" :name="illustrationName" :size="illustrationSize" />
    </template>
    <div v-else class="yd-empty-icon">
      <slot name="icon">
        <FaIcon :name="icon" />
      </slot>
    </div>
    <strong><slot name="title">{{ title }}</slot></strong>
    <p v-if="description || $slots.description" class="yd-muted">
      <slot name="description">
        {{ description }}
      </slot>
    </p>
    <slot name="action">
      <slot />
    </slot>
  </div>
</template>

<style scoped>
.yd-empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.yd-empty--illustration {
  gap: 16px;
}

.yd-empty-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  font-size: 24px;
  color: oklch(var(--muted-foreground));
  background: oklch(var(--muted));
  border: 1px dashed oklch(var(--border));
  border-radius: var(--yd-surface-radius);
}

p {
  max-width: 36ch;
  margin: 0;
  font-size: 0.875rem;
}
</style>
