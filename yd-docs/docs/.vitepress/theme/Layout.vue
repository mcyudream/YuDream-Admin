<script setup lang="ts">
import DefaultTheme from 'vitepress/theme'
import YdThemeController from './components/YdThemeController.vue'
import YdThemeProvider from './components/YdThemeProvider.vue'
</script>

<template>
  <YdThemeProvider>
    <DefaultTheme.Layout />
    <YdThemeController />
  </YdThemeProvider>
</template>
