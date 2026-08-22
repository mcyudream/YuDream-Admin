<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingFormTextarea from '../.vitepress/theme/components/demos/InteractiveRemainingFormTextarea.vue'

const srcBasic = `<script setup>
import { FaTextarea } from '@yudream/components'
import { ref } from 'vue'

const value = ref('')
<\/script>

<template>
  <FaTextarea v-model="value" placeholder="请输入内容" class="w-96" />
</template>`

const srcDisabled = `<script setup>
import { FaTextarea } from '@yudream/components'
import { ref } from 'vue'

const value = ref('这是一段不可编辑的文本内容。')
<\/script>

<template>
  <FaTextarea v-model="value" disabled class="w-96" />
</template>`

const srcSlot = `<script setup>
import { FaButton, FaIcon, FaTextarea } from '@yudream/components'
import { ref } from 'vue'

const value = ref('')
<\/script>

<template>
  <FaTextarea
    v-model="value"
    placeholder="console.log('Hello, world!');"
    align="block"
    start-class="justify-between"
    end-class="justify-between"
    class="w-120"
  >
    <template #start>
      <span>script.ts</span>
      <FaButton variant="ghost" size="icon" class="size-6">
        <FaIcon name="i-ep:refresh" />
      </FaButton>
    </template>
    <template #end>
      <span>Line 1, Column 1</span>
      <FaButton size="sm" class="px-2 h-8">
        Run
        <FaIcon name="i-lucide:corner-down-left" />
      </FaButton>
    </template>
  </FaTextarea>
</template>`

const props = [
  ['<code>v-model</code>', '<code>string | number</code>', '—', '绑定值；泛型 <code>T extends string | number</code>'],
  ['<code>align</code>', "<code>'inline' | 'block'</code>", "<code>'inline'</code>", '前后插槽（start/end）相对输入区的对齐方式；<code>inline</code> 与输入区同行，<code>block</code> 位于输入区上/下方'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用'],
  ['<code>class</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到最外层 InputGroup 容器（宽度需在这里控制，组件无默认宽度）'],
  ['<code>inputClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到内部原生 <code>&lt;textarea&gt;</code> 元素'],
  ['<code>startClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到 start 插槽的 addon 容器'],
  ['<code>endClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到 end 插槽的 addon 容器'],
]

const slots = [
  ['<code>start</code>', '—', '输入区前部内容；<code>align="inline"</code> 时在行内左侧，<code>align="block"</code> 时在输入区上方'],
  ['<code>end</code>', '—', '输入区尾部内容；<code>align="inline"</code> 时在行内右侧，<code>align="block"</code> 时在输入区下方'],
]
</script>

# FaTextarea 多行文本框

多行文本输入。与 `FaInput` 同属 InputGroup 体系，支持 start/end 前后插槽，适合做代码片段、备注、简介等多行录入场景。框架会自动全局注册，页面中无需手动导入。

## 基础用法

<Demo title="基础多行输入" description="v-model 双向绑定；组件无默认宽度，用 class 控制（如 w-96）" :source="srcBasic">
  <ClientOnly><InteractiveRemainingFormTextarea variant="basic" /></ClientOnly>
</Demo>

## 禁用

<Demo title="禁用状态" :source="srcDisabled">
  <ClientOnly><InteractiveRemainingFormTextarea variant="disabled" /></ClientOnly>
</Demo>

## 前后插槽

<Demo title="start / end 插槽" description="align=block 时插槽位于输入区上下方，可做编辑器式工具条" :source="srcSlot">
  <ClientOnly><InteractiveRemainingFormTextarea variant="slot" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaTextarea Props" :data="props" />

### Slots

<ApiTable title="FaTextarea Slots" :data="slots" :columns="['插槽', '作用域参数', '说明']" />

### Emits

无自定义 emits，原生 `input`/`focus`/`blur` 等事件经 `$attrs` 透传，`v-model` 双向绑定。

## 注意事项

- 内部 `<textarea>` 自带 `resize-none`，用户无法拖拽调整尺寸；高度通过 `rows` 属性（`$attrs` 透传）或 `inputClass` 控制。
- 未声明的原生属性（`placeholder`、`rows`、`maxlength`、`readonly` 等）通过 `$attrs` 透传到内部 `<textarea>`。
- 与 `FaInput` 不同，`FaTextarea` 没有 `clearable` 和 `type` 属性，也没有 defineExpose。
- 单行输入请使用 `FaInput`。

## 源码

- 组件：`yudream-frontend/packages/components/src/basic/textarea/index.vue`
- 内部输入元素：`yudream-frontend/packages/components/src/basic/textarea/textarea/Textarea.vue`
- 示例：`yudream-frontend/packages/components/src/basic/textarea/_examples/`
