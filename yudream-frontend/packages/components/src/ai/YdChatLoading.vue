<script setup lang="ts">
withDefaults(defineProps<{
  /** 提示文案，传空字符串只显示三点动画 */
  text?: string
}>(), {
  text: '正在思考…',
})
</script>

<template>
  <span class="yd-chat-loading">
    <span class="yd-chat-loading__dot" /><span class="yd-chat-loading__dot" /><span class="yd-chat-loading__dot" />
    <span v-if="text" class="yd-chat-loading__text">{{ text }}</span>
  </span>
</template>

<style scoped>
.yd-chat-loading {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 13px;
}

.yd-chat-loading__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgb(var(--primary-6));
  animation: yd-chat-loading-blink 1.2s infinite;
}

.yd-chat-loading__dot:nth-child(2) {
  animation-delay: 0.2s;
}

.yd-chat-loading__dot:nth-child(3) {
  animation-delay: 0.4s;
}

.yd-chat-loading__text {
  margin-left: 4px;
}

@keyframes yd-chat-loading-blink {
  0%, 60%, 100% { opacity: 0.25; }
  30% { opacity: 1; }
}
</style>
