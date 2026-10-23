<script setup lang="ts">
import type { MessagingBindingTarget } from '@/api/modules/profile'
import { YdButton, YdIcon, YdTag } from '@yudream/components'
import { bindingProtocolLabel, bindingTargetLabel } from '@/composables/use-messaging-binding'

defineProps<{
  target: MessagingBindingTarget
  loading: boolean
  disabled: boolean
}>()

defineEmits<{ issue: [target: MessagingBindingTarget] }>()
</script>

<template>
  <article class="binding-target" :aria-label="`${bindingTargetLabel(target)} · ${target.name}`">
    <div class="min-w-0">
      <strong class="text-sm block break-words">{{ bindingTargetLabel(target) }}</strong>
      <p class="text-xs text-muted-foreground mt-1 break-words">
        {{ bindingProtocolLabel(target.protocol) }}
        <template v-if="target.botName && target.name !== target.botName">
          · {{ target.name }}
        </template>
      </p>
    </div>
    <div class="flex flex-wrap gap-2 items-center justify-between">
      <YdTag :variant="target.bound ? 'default' : 'secondary'">
        {{ target.bound ? '已绑定' : '未绑定' }}
      </YdTag>
      <YdButton size="sm" :variant="target.bound ? 'outline' : 'default'" :loading="loading" :disabled="disabled" :aria-label="`为${target.name || bindingTargetLabel(target)}生成绑定码`" @click="$emit('issue', target)">
        <YdIcon name="morph:key-round" />生成绑定码
      </YdButton>
    </div>
  </article>
</template>

<style scoped>
.binding-target {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 12px 0;
  color: var(--color-text-1);
  border-bottom: 1px solid var(--color-border-2);
}
</style>
