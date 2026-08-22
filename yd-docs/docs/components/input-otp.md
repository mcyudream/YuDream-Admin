<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingBasicInputOtp from '../.vitepress/theme/components/demos/InteractiveRemainingBasicInputOtp.vue'

const srcBasic = `<script setup>
import { FaInputOTP } from '@yudream/components'
import { ref } from 'vue'

const input = ref('')
<\/script>

<template>
  <FaInputOTP v-model="input" />
</template>`

const srcLength = `<script setup>
import { FaInputOTP } from '@yudream/components'
import { ref } from 'vue'

const input = ref('')
<\/script>

<template>
  <FaInputOTP v-model="input" :length="4" />
</template>`

const srcPattern = `<script setup>
import { FaInputOTP } from '@yudream/components'
import { ref } from 'vue'

const chars = ref('')
const digits = ref('')
const digitsAndChars = ref('')
const custom = ref('')
<\/script>

<template>
  <!-- 仅字母 -->
  <FaInputOTP v-model="chars" pattern="only-chars" />
  <!-- 仅数字 -->
  <FaInputOTP v-model="digits" pattern="only-digits" />
  <!-- 字母和数字 -->
  <FaInputOTP v-model="digitsAndChars" pattern="only-digits-and-chars" />
  <!-- 自定义正则：仅允许 A-F 和 0-9 -->
  <FaInputOTP v-model="custom" pattern="^[A-F0-9]*$" />
</template>`

const srcSeparator = `<script setup>
import { FaInputOTP } from '@yudream/components'
import { ref } from 'vue'

const input = ref('')
<\/script>

<template>
  <!-- 按 2 + 3 分组，组间显示分隔符 -->
  <FaInputOTP v-model="input" :separator="[2, 3]" />
</template>`

const srcCallback = `<script setup>
import { FaInputOTP, useFaToast } from '@yudream/components'
import { ref } from 'vue'

const input = ref('')
const toast = useFaToast()

function handleComplete(value) {
  // value 为完整的验证码字符串
  toast(value)
}
<\/script>

<template>
  <FaInputOTP
    v-model="input"
    @input="value => console.log('input:', value)"
    @complete="handleComplete"
  />
</template>`

const props = [
  ['<code>v-model</code>', '<code>string</code>', '—', '绑定的验证码值；始终是完整字符串（如 <code>&quot;123456&quot;</code>），不要拆成数组'],
  ['<code>length</code>', '<code>number</code>', '<code>6</code>', '验证码位数'],
  ['<code>pattern</code>', "<code>'only-chars' | 'only-digits' | 'only-digits-and-chars' | string</code>", '—', '输入内容匹配模式；预设值映射为 vue-input-otp 内置正则，也可直接传自定义正则字符串'],
  ['<code>separator</code>', '<code>number[]</code>', '<code>[]</code>', '分组长度数组，组与组之间渲染分隔符，如 <code>[3, 3]</code> 表示 3 位一组分两段'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用全部输入格'],
]

const emits = [
  ['<code>input</code>', '<code>(value: string) => void</code>', '任意输入格内容变化时触发，参数为当前完整值'],
  ['<code>complete</code>', '<code>(value: string) => void</code>', '输入位数达到 <code>length</code> 时自动触发，参数为完整验证码'],
]
</script>

# FaInputOTP 验证码输入

一次性密码（OTP）输入组件，用于短信验证码、邮箱验证码、双因素认证（2FA）等场景。基于 [vue-input-otp](https://input-otp.1stg.me/) 封装，支持自动聚焦跳格、粘贴分发、退格回退与自定义位数/分组/字符模式。框架会自动全局注册，页面中无需手动导入。

## 基础用法

<Demo title="基础用法" description="默认 6 位，输入后自动跳到下一格，支持整段粘贴" :source="srcBasic">
  <ClientOnly><InteractiveRemainingBasicInputOtp /></ClientOnly>
</Demo>

## 自定义位数

<Demo title="length" description="通过 length 调整位数，如常见的 4 位短信验证码" :source="srcLength">
  <ClientOnly><InteractiveRemainingBasicInputOtp /></ClientOnly>
</Demo>

## 输入模式

<Demo title="pattern" description="三种预设模式，或直接传正则字符串限制可输入字符" :source="srcPattern">
  <ClientOnly><InteractiveRemainingBasicInputOtp /></ClientOnly>
</Demo>

## 分组分隔符

<Demo title="separator" description="separator 传入每组长度，组间自动插入分隔符；不足部分并入最后一段" :source="srcSeparator">
  <ClientOnly><InteractiveRemainingBasicInputOtp /></ClientOnly>
</Demo>

## 输入与完成事件

<Demo title="input / complete" description="input 随输入实时回调，complete 在填满时触发一次" :source="srcCallback">
  <ClientOnly><InteractiveRemainingBasicInputOtp /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaInputOTP Props" :data="props" />

### Emits

<ApiTable title="FaInputOTP Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

## 行为机制

```mermaid
flowchart LR
  A[用户键入 / 粘贴] --> B{pattern 校验}
  B -- 不匹配 --> C[忽略该字符]
  B -- 匹配 --> D[写入当前 Slot 并前进到下一格]
  D --> E{已填满 length 格?}
  E -- 否 --> A
  E -- 是 --> F[触发 complete 事件]
```

要点：

- 组件内部由 `InputOTP` 容器 + `InputOTPGroup` 分组 + `InputOTPSlot` 单格组成，`separator` 会把 `length` 切成若干连续区间，区间之间渲染 `InputOTPSeparator`；
- `pattern` 的三个预设值分别映射 vue-input-otp 导出的 `REGEXP_ONLY_CHARS`、`REGEXP_ONLY_DIGITS`、`REGEXP_ONLY_DIGITS_AND_CHARS`；其他字符串按自定义正则处理；
- 支持粘贴整段验证码（自动分配到各格）、退格删除并回退上一格、方向键在格间移动。

## 注意事项

- `v-model` 是**一个完整字符串**而不是数组；校验时直接对整个值做请求即可，无需自行拼接。
- `complete` 每次填满都会触发（包括重新修改后再次填满）；若做一次性提交，请在回调中加防抖或状态锁。
- 验证码本身是短数字/字母串，不涉及 Snowflake ID；但若后续要把用户 ID 等 Java `Long`/Snowflake ID 一并传给前端，JSON、TS 模型与 URL 参数中一律使用 `string`，禁止 `Number(id)`。
- `separator` 中非正数会被跳过，超出 `length` 的部分会截断；未覆盖到的剩余位数并入最后一个分组。

## 源码

- 组件：`yudream-frontend/packages/components/src/basic/input-otp/index.vue`
- 内部实现：`yudream-frontend/packages/components/src/basic/input-otp/input-otp/`（`InputOTP.vue`、`InputOTPSlot.vue`、`InputOTPGroup.vue`、`InputOTPSeparator.vue`）
- 示例：`yudream-frontend/packages/components/src/basic/input-otp/_examples/`
