<script setup lang="ts">
import { Toaster } from './sonner'
import 'vue-sonner/style.css'

defineOptions({
  name: 'BuiltInToast',
})
</script>

<template>
  <Toaster
    position="top-center" rich-colors :toast-options="{
      classes: {
        toast: 'group toast group-[.toaster]:text-foreground',
        description: 'group-[.toast]:text-muted-foreground',
        actionButton: 'group-[.toast]:bg-primary group-[.toast]:text-primary-foreground',
        cancelButton: 'group-[.toast]:bg-muted group-[.toast]:text-muted-foreground',
        icon: 'yd-toast-icon',
      },
    }"
  />
</template>
