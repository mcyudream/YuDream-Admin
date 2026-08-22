<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicSwitch from '../.vitepress/theme/components/demos/InteractiveBasicSwitch.vue'

const srcBasic = `<script setup>
import { FaSwitch } from '@yudream/components'
import { ref } from 'vue'

const enabled = ref(true)
<\/script>

<template>
  <FaSwitch v-model="enabled" />
</template>`

const srcDisabled = `<template>
  <FaSwitch v-model="enabled" disabled />
</template>`

const srcIcon = `<script setup>
import { FaSwitch } from '@yudream/components'
import { ref } from 'vue'

const dark = ref(false)
<\/script>

<template>
  <!-- 开 / 关两种状态分别显示不同图标（Iconify 图标名） -->
  <FaSwitch v-model="dark" on-icon="ri:sun-line" off-icon="ri:moon-line" />
</template>`

const srcBeforeChange = `<script setup>
import { FaSwitch } from '@yudream/components'
import { ref } from 'vue'

const checked = ref(false)

// 返回 false 或 rejected Promise 时阻止切换
function handleBeforeChange() {
  return new Promise<boolean>((resolve) => {
    // 这里可用 FaModal 的 confirm 弹窗确认
    const confirmed = window.confirm('确认要切换当前状态吗？')
    resolve(confirmed)
  })
}
<\/script>

<template>
  <FaSwitch v-model="checked" :before-change="handleBeforeChange" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>boolean</code>', '—', '开关状态（双向绑定）'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用，禁止交互且降低不透明度'],
  ['<code>onIcon</code>', '<code>string</code>', '—', '开启状态下滑块内显示的图标（Iconify 图标名，如 <code>ri:sun-line</code>）'],
  ['<code>offIcon</code>', '<code>string</code>', '—', '关闭状态下滑块内显示的图标'],
  ['<code>beforeChange</code>', '<code>() => boolean \\| Promise&lt;boolean&gt;</code>', '—', '状态变更前拦截钩子；返回 <code>false</code>（或 resolve 为 false / 抛异常）则取消本次切换'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaSwitch 开关

布尔状态切换。基于 reka-ui `SwitchRoot` 封装，滑块颜色随主题主色变化（选中态为 `bg-primary`），不由业务页面控制。

## 基础用法

用 `v-model` 绑定一个布尔值。

<Demo title="基础开关" description="v-model 双向绑定布尔值" :source="srcBasic">
  <ClientOnly><InteractiveBasicSwitch /></ClientOnly>
</Demo>

## 禁用状态

<Demo title="禁用" :source="srcDisabled">
  <div class="demo-row">
    <button class="demo-btn demo-btn--disabled" style="width: 44px; padding: 0;">禁</button>
  </div>
</Demo>

## 带图标

通过 `on-icon` / `off-icon` 在滑块内部显示状态图标，适合「日间 / 夜间」等语义明确的场景。

<Demo title="图标开关" description="on-icon 与 off-icon 分别对应开、关状态" :source="srcIcon">
  <div class="demo-row">
    <button class="demo-btn demo-btn--primary" style="width: 44px; padding: 0;">☀</button>
    <button class="demo-btn" style="width: 44px; padding: 0;">🌙</button>
  </div>
</Demo>

## 前置拦截（beforeChange）

传入 `beforeChange` 后，每次切换都会先执行该函数：返回 `true`（或 resolve `true`）才更新 `v-model`，返回 `false` 或抛出异常则保持原状态。常用于二次确认或权限校验。

<Demo title="beforeChange 拦截" description="确认通过后才会真正切换" :source="srcBeforeChange">
  <div class="demo-row">
    <button class="demo-btn demo-btn--primary" style="width: 44px; padding: 0;">?</button>
    <span style="font-size: 13px; color: var(--vp-c-text-2);">点击后先弹确认框</span>
  </div>
</Demo>

## 实现说明

```mermaid
flowchart LR
  A["FaSwitch<br/>(index.vue)"] -->|"handleChange<br/>beforeChange 拦截"| B["Switch<br/>(switch/Switch.vue)"]
  B --> C["reka-ui SwitchRoot"]
```

- `FaSwitch`（`basic/switch/index.vue`）持有布尔模型，先经过 `beforeChange` 判定再写入模型值。
- 底层 `Switch` 将 `SwitchRootProps` / `SwitchRootEmits` 透传给 reka-ui `SwitchRoot`，并提供 `thumb` 插槽渲染滑块内容；`FaSwitch` 用该插槽放状态图标，未向使用者暴露插槽。

## API

### Props

<ApiTable title="FaSwitch Props" :data="props" />

### Events

无自定义 emits。状态变化完全通过 `v-model`（`update:modelValue`）同步；如需监听变化，直接 `watch` 绑定的值即可。

### Slots

无对外插槽。滑块内部的 `thumb` 插槽由组件自身用于渲染 `onIcon` / `offIcon` 图标。

## 注意事项

- `beforeChange` 返回 `Promise<boolean>` 时组件会等待其完成；期间不会提前更新状态。
- 需要更大点击区域时用 `class` 调整尺寸（根元素默认 `h-[1.15rem] w-8` 圆角胶囊）。

## 源码引用

- `yudream-frontend/packages/components/src/basic/switch/index.vue`
- `yudream-frontend/packages/components/src/basic/switch/switch/Switch.vue`
