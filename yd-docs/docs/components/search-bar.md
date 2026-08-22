<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaButton, FaInput, FaSearchBar } from '@yudream/components'
import { ref } from 'vue'

const fold = ref(true)
<\/script>

<template>
  <FaSearchBar v-model:fold="fold" show-toggle>
    <template #default="{ fold }">
      <div class="flex flex-wrap items-center gap-2 px-4">
        <FaInput placeholder="用户名" class="w-40" />
        <FaInput placeholder="手机号" class="w-40" />
        <FaButton>搜索</FaButton>
        <!-- 展开后才显示的筛选项 -->
        <template v-if="!fold">
          <FaInput placeholder="邮箱" class="w-40" />
        </template>
      </div>
    </template>
  </FaSearchBar>
</template>`

const srcCustom = `<script setup>
import { FaSearchBar } from '@yudream/components'
<\/script>

<template>
  <!-- 自行控制折叠按钮的样式与位置 -->
  <FaSearchBar v-model:fold="fold">
    <template #default="{ fold, toggle }">
      <div class="px-4">
        筛选内容
        <FaButton size="sm" variant="ghost" @click="toggle">
          {{ fold ? '展开' : '收起' }}
        </FaButton>
      </div>
    </template>
  </FaSearchBar>
</template>`
</script>

# FaSearchBar 搜索栏

列表页顶部的筛选区容器。组件本身不提供输入控件，而是通过默认插槽承载筛选表单，并内置「展开 / 收起」折叠机制（`fold` 模型），用于收纳次要筛选条件。

依据源码：`yudream-frontend/packages/components/src/basic/search-bar/index.vue`

## 基础用法

`showToggle` 开启后在容器底部居中显示一个折叠切换按钮；插槽作用域暴露 `fold`（当前是否折叠）与 `toggle`（切换函数），用 `v-if="!fold"` 控制次要筛选项的显隐。

<Demo title="可折叠搜索栏" description="showToggle 显示底部切换按钮，fold 控制次要条件" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="search-bar" /></ClientOnly>
</Demo>

## 自定义触发器

不传 `showToggle` 时组件不渲染内置按钮，可完全用插槽作用域的 `toggle` 自行实现展开交互。

<Demo title="自定义折叠按钮" description="通过 toggle 函数自行控制折叠" :source="srcCustom">
  <div style="padding: 12px 16px; display: flex; gap: 8px; align-items: center;">筛选内容<button class="demo-btn" style="height: 26px; padding: 0 10px; font-size: 12px;">展开</button></div>
</Demo>

## API

### Props

<ApiTable title="FaSearchBar Props" :data="[
  ['<code>showToggle</code>', '<code>boolean</code>', '<code>false</code>', '是否显示内置的底部折叠切换按钮'],
  ['<code>background</code>', '<code>boolean</code>', '<code>false</code>', '是否显示背景色块（<code>bg-secondary</code> + 内边距）'],
  ['<code>v-model:fold</code>', '<code>boolean</code>', '<code>true</code>', '折叠状态；<code>true</code> 表示折叠'],
]" />

### Slots

| 插槽 | 作用域 | 说明 |
| --- | --- | --- |
| `default` | <code>{ fold: boolean, toggle: () => void }</code> | 筛选表单内容 |

### Emits

| 事件 | 参数 | 说明 |
| --- | --- | --- |
| `toggle` | `(value: boolean)` | 点击切换按钮后触发，参数为切换后的 `fold` 值 |
