<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicButton from '../.vitepress/theme/components/demos/InteractiveBasicButton.vue'

const srcBasic = `<script setup>
import { FaButton } from '@yudream/components'
import { ref } from 'vue'

const saving = ref(false)
async function save() {
  saving.value = true
  try {
    await api.submit()
  } finally {
    saving.value = false
  }
}
<\/script>

<template>
  <FaButton variant="outline" size="sm" @click="save">
    取消
  </FaButton>
  <FaButton :loading="saving">保存</FaButton>
</template>`

const props = [
  ['<code>variant</code>', "'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'", "<code>'default'</code>", '按钮视觉变体'],
  ['<code>size</code>', "'default' | 'sm' | 'lg' | 'icon' | 'icon-sm' | 'icon-lg'", "<code>'default'</code>", '尺寸；icon 系列为纯图标方形按钮'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用'],
  ['<code>loading</code>', '<code>boolean</code>', '<code>false</code>', '显示加载图标并禁用点击'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaButton 按钮

常用的操作按钮。基于 reka-ui 封装 + Tailwind 样式。

## 基础用法

<Demo title="基础按钮" description="variant 控制视觉变体，loading 显示加载态" :source="srcBasic">
  <ClientOnly><InteractiveBasicButton /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaButton Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 按钮内容 |

### Emits

无自定义 emits，原生事件（`click` 等）透传。
