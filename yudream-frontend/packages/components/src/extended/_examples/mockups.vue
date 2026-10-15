<script setup lang="ts">
import YdMockupBrowser from '../YdMockupBrowser.vue'
import YdMockupWindow from '../YdMockupWindow.vue'
</script>

<template>
  <YdMockupBrowser url="https://example.com"><div>Browser preview</div></YdMockupBrowser>
  <YdMockupWindow><div>Desktop window content</div></YdMockupWindow>
</template>
