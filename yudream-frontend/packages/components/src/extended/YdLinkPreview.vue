<script setup lang="ts">
import { computed } from 'vue'
import FaHoverCard from '../basic/hover-card/index.vue'
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

const props = defineProps<{
  url: string
  title?: string
  description?: string
  image?: string
  target?: '_blank' | '_self'
}>()
const safeUrl = computed(() => {
  try {
    const url = new URL(props.url, 'https://localhost')
    return ['https:', 'http:'].includes(url.protocol) ? props.url : undefined
  }
  catch {
    return undefined
  }
})
const hostname = computed(() => {
  try {
    return new URL(props.url).hostname
  }
  catch {
    return props.url
  }
})
</script>

<template>
  <FaHoverCard>
    <a :href="safeUrl" :target="target || '_blank'" rel="noopener noreferrer" class="yd-link yd-focus"><slot>{{ title || hostname }}</slot><FaIcon name="morph:arrow-up-right" /></a><template #card>
      <slot name="preview">
        <div class="yd-link-preview">
          <img v-if="image" :src="image" alt="" loading="lazy" referrerpolicy="no-referrer"><strong>{{ title || hostname }}</strong><p v-if="description" class="yd-muted">
            {{ description }}
          </p><small class="yd-muted">{{ hostname }}</small>
        </div>
      </slot>
    </template>
  </FaHoverCard>
</template>

<style scoped>
.yd-link {
  display: inline-flex;
  gap: 0.3em;
  align-items: center;
  color: var(--yd-emphasis);
  text-decoration: underline;
  text-underline-offset: 4px;
}

.yd-link-preview {
  display: grid;
  gap: 0.5rem;
  width: min(18rem, 80vw);
}

.yd-link-preview img {
  width: 100%;
  max-height: 10rem;
  object-fit: cover;
  border-radius: var(--yd-control-radius);
}

.yd-link-preview p {
  margin: 0;
  font-size: 0.85rem;
}
</style>
