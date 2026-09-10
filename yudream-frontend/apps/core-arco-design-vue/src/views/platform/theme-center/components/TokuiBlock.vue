<script setup lang="ts">
import { TokUI } from '@jboltai/tokui'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import '@jboltai/tokui/css'

const props = defineProps<{
  dsl: string
}>()

// tokui 直接操作 DOM，需要一个独占容器，避免与 Vue 的虚拟 DOM 冲突。
const el = ref<HTMLElement>()
let ui: InstanceType<typeof TokUI> | null = null

function render(dsl: string) {
  if (!el.value) {
    return
  }
  if (!ui) {
    ui = new TokUI({ container: el.value, streaming: false, locale: 'zh-CN' })
  }
  ui.render(dsl || '')
}

onMounted(() => render(props.dsl))

watch(() => props.dsl, dsl => render(dsl))

onBeforeUnmount(() => {
  ui?.disconnect()
  ui = null
})
</script>

<template>
  <div ref="el" class="tokui-block" />
</template>

<style scoped>
.tokui-block {
  width: 100%;
}
</style>
