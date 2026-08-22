<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicInput from '../.vitepress/theme/components/demos/InteractiveBasicInput.vue'

const srcBasic = `<script setup>
import { FaInput } from '@yudream/components'
import { ref } from 'vue'

const value = ref('')
<\/script>

<template>
  <FaInput v-model="value" placeholder="请输入内容" />
</template>`

const srcPassword = `<script setup>
import { FaInput } from '@yudream/components'
import { ref } from 'vue'

const value = ref('')
<\/script>

<template>
  <!-- type="password" 时自动附带「显示/隐藏密码」切换按钮 -->
  <FaInput v-model="value" type="password" placeholder="请输入密码" />
</template>`

const srcClearable = `<script setup>
import { FaInput } from '@yudream/components'
import { ref } from 'vue'

const value = ref('可清空的内容')

function onClear() {
  console.log('已清空')
}
<\/script>

<template>
  <FaInput v-model="value" clearable placeholder="请输入内容" @clear="onClear" />
</template>`

const srcSlot = `<script setup>
import { FaButton, FaIcon, FaInput, FaTooltip } from '@yudream/components'
import { ref } from 'vue'

const value = ref('')
const value2 = ref('')
<\/script>

<template>
  <!-- inline 对齐（默认）：前后插槽与输入框同一行 -->
  <FaInput v-model="value" placeholder="example.com" input-class="ps-1">
    <template #start>
      https://
    </template>
    <template #end>
      <FaTooltip text="可输入域名、IP、端口、URL 等">
        <FaIcon name="i-ri:question-line" class="text-base cursor-help" />
      </FaTooltip>
    </template>
  </FaInput>

  <!-- block 对齐：前后插槽位于输入框的上方 / 下方 -->
  <FaInput v-model="value2" placeholder="请输入内容" align="block" input-class="shadow-none" end-class="justify-end">
    <template #start>
      标题：
    </template>
    <template #end>
      <FaButton variant="ghost" size="sm" class="px-2 h-8">
        提交
      </FaButton>
    </template>
  </FaInput>
</template>`

const srcExpose = `<script setup>
import { FaInput } from '@yudream/components'
import { ref, useTemplateRef } from 'vue'

const value = ref('')
const inputRef = useTemplateRef('inputRef')

function focus() {
  // defineExpose 暴露的 ref 即内部原生 <input> 元素
  inputRef.value?.ref?.focus()
}
<\/script>

<template>
  <FaInput ref="inputRef" v-model="value" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>string | number</code>', '—', '绑定值；泛型 <code>T extends string | number</code>，清空（clearable）时会被置为 <code>undefined</code>'],
  ['<code>type</code>', "<code>'text' | 'password' | 'number' | 'email' | 'search' | 'tel' | 'url' | (string & {})</code>", "<code>'text'</code>", '原生 input type；传 <code>password</code> 且有值时自动在尾部渲染「显示/隐藏密码」切换按钮'],
  ['<code>align</code>', "<code>'inline' | 'block'</code>", "<code>'inline'</code>", '前后插槽（start/end）相对输入框的对齐方式；<code>inline</code> 与输入框同行，<code>block</code> 位于输入框上/下方'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用；禁用时 clearable 清空按钮不再渲染'],
  ['<code>clearable</code>', '<code>boolean</code>', '<code>false</code>', '显示清空按钮；仅在有值且输入框处于聚焦或悬停状态时出现，点击后值置为 <code>undefined</code> 并触发 <code>clear</code> 事件'],
  ['<code>class</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到最外层 InputGroup 容器'],
  ['<code>inputClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到内部原生 <code>&lt;input&gt;</code> 元素'],
  ['<code>startClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到 start 插槽的 addon 容器'],
  ['<code>endClass</code>', '<code>HTMLAttributes[\'class\']</code>', '—', '透传到 end 插槽的 addon 容器（清空/密码切换按钮也在此容器内）'],
]

const slots = [
  ['<code>start</code>', '—', '输入框前部内容；<code>align="inline"</code> 时在行内左侧，<code>align="block"</code> 时在输入框上方'],
  ['<code>end</code>', '—', '输入框尾部内容；渲染在清空按钮、密码切换按钮之后'],
]

const emits = [
  ['<code>clear</code>', '<code>() => void</code>', '点击清空按钮后触发；原生 <code>input</code>/<code>focus</code>/<code>blur</code> 等事件经 <code>$attrs</code> 透传'],
]

const expose = [
  ['<code>ref</code>', '<code>ComputedRef&lt;HTMLInputElement | null | undefined&gt;</code>', '内部原生 <code>&lt;input&gt;</code> 元素，可用于 <code>focus()</code> 等 DOM 操作'],
]
</script>

# FaInput 输入框

单行文本输入框。基于内部 `Input` + `InputGroup`/`InputGroupAddon` 封装（reka-ui 体系 + Tailwind 样式），支持前后插槽、一键清空与密码显隐切换。框架会自动全局注册，页面中无需手动导入。

## 基础用法

<Demo title="基础输入框" description="v-model 双向绑定，placeholder 等原生属性直接透传到内部 input" :source="srcBasic">
  <ClientOnly><InteractiveBasicInput /></ClientOnly>
</Demo>

## 密码输入

<Demo title="密码输入" description="type=password 且有值时自动出现「眼睛」切换按钮" :source="srcPassword">
  <div class="demo-row">
    <input class="demo-input" type="password" placeholder="请输入密码" />
  </div>
</Demo>

## 可清空

<Demo title="可清空" description="clearable 按钮仅在有值且聚焦/悬停时显示" :source="srcClearable">
  <div class="demo-row">
    <input class="demo-input" value="可清空的内容" />
  </div>
</Demo>

## 前后插槽

<Demo title="start / end 插槽" description="align 控制插槽与输入框同行（inline）或上下排列（block）" :source="srcSlot">
  <div class="demo-row">
    <input class="demo-input" placeholder="https:// | example.com" />
  </div>
</Demo>

## 获取原生 input 引用

<Demo title="defineExpose" description="通过模板引用拿到原生 input 元素，调用 focus 等 DOM 方法" :source="srcExpose">
  <div class="demo-row">
    <input class="demo-input" placeholder="ref 暴露原生 input" />
  </div>
</Demo>

## API

### Props

<ApiTable title="FaInput Props" :data="props" />

### Slots

<ApiTable title="FaInput Slots" :data="slots" :columns="['插槽', '作用域参数', '说明']" />

### Emits

<ApiTable title="FaInput Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Expose

<ApiTable title="FaInput defineExpose" :data="expose" :columns="['成员', '类型', '说明']" />

## 注意事项

- 未在 Props 中声明的原生属性（`placeholder`、`readonly`、`maxlength`、`name` 等）通过 `$attrs` 透传到内部 `<input>`，可直接使用。
- 点击清空按钮后绑定值变为 `undefined` 而非空字符串，表单提交前注意判空逻辑。
- `type="password"` 的显隐切换是组件内部维护的本地状态，不改变传入的 `type` prop。
- 组件内部已设置 `autocomplete="off"`。
- 相关组件：多行文本用 `FaTextarea`，验证码输入用 `FaInputOTP`，数字输入用 `FaNumberField`。

## 源码

- 组件：`yudream-frontend/packages/components/src/basic/input/index.vue`
- 内部输入元素：`yudream-frontend/packages/components/src/basic/input/input/Input.vue`
- 示例：`yudream-frontend/packages/components/src/basic/input/_examples/`
