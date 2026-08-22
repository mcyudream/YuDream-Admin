<script setup lang="ts">
/**
 * 将真实交互组件延后到客户端挂载，避免它们的浏览器依赖参与 SSR。
 * 演示代码请继续通过 Demo 的 source 属性以纯文本展示。
 */
</script>

<template>
  <ClientOnly>
    <slot />
    <template #fallback>
      <slot name="fallback" />
    </template>
  </ClientOnly>
</template>
