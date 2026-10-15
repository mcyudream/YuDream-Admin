<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

export interface YdBreadcrumbItem {
  label: string
  to?: RouteLocationRaw
  disabled?: boolean
}
const props = withDefaults(defineProps<{
  items: YdBreadcrumbItem[]
  separator?: string
  ariaLabel?: string
}>(), { separator: '/', ariaLabel: '面包屑导航' })
const visibleItems = computed(() => props.items)
</script>

<template>
  <nav :aria-label="ariaLabel" class="yd-breadcrumb">
    <ol class="yd-breadcrumb__list">
      <li v-for="(item, index) in visibleItems" :key="index" class="yd-breadcrumb__item">
        <RouterLink v-if="item.to && index < visibleItems.length - 1 && !item.disabled" :to="item.to" class="yd-breadcrumb__link">
          <slot name="item" :item="item">
            {{ item.label }}
          </slot>
        </RouterLink>
        <span v-else :aria-current="index === visibleItems.length - 1 ? 'page' : undefined" :aria-disabled="item.disabled || undefined" class="yd-breadcrumb__current">
          <slot name="item" :item="item">{{ item.label }}</slot>
        </span>
        <span v-if="index < visibleItems.length - 1" aria-hidden="true" class="yd-breadcrumb__separator">{{ separator }}</span>
      </li>
    </ol>
  </nav>
</template>

<style scoped>
.yd-breadcrumb__list { display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem; margin: 0; padding: 0; list-style: none; }
.yd-breadcrumb__item { display: inline-flex; align-items: center; gap: 0.5rem; min-width: 0; }
.yd-breadcrumb__link { color: oklch(var(--muted-foreground)); text-decoration: none; }
.yd-breadcrumb__link:hover { color: oklch(var(--foreground)); text-decoration: underline; }
.yd-breadcrumb__current { overflow: hidden; color: oklch(var(--foreground)); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.yd-breadcrumb__separator { color: oklch(var(--muted-foreground)); }
</style>
