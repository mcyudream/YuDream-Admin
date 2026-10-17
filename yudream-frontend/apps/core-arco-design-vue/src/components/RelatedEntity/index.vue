<script setup lang="ts">
import type { RelatedEntityRef } from './registry'
import { computed } from 'vue'
import { resolveRelatedEntity } from './registry'

/**
 * 关联实体入口：按 entityType 解析已注册的预览卡提供者。
 * 未注册的类型（插件尚未提供实现）降级为纯文本，不阻断列表渲染。
 */
const props = defineProps<RelatedEntityRef>()

const emit = defineEmits<{ changed: [] }>()

const provider = computed(() => resolveRelatedEntity(props.entityType))
</script>

<template>
  <component
    :is="provider"
    v-if="provider"
    :entity-type="entityType"
    :entity-id="entityId"
    :label="label"
    :secondary="secondary"
    :avatar="avatar"
    :avatar-only="avatarOnly"
    @changed="emit('changed')"
  />
  <span v-else>{{ label || entityId }}</span>
</template>
