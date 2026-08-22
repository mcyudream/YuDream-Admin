<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
// 组件实际使用时无需手动导入，框架会自动导入
<\/script>

<template>
  <FaAlert
    icon="i-lucide:info"
    title="提示信息"
    description="这是一条普通提示，用于展示页面说明或操作反馈。"
  />
</template>`

const srcVariant = `<template>
  <FaAlert
    icon="i-lucide:circle-alert"
    title="危险提示"
    description="删除后数据将无法恢复，请谨慎操作。"
    variant="destructive"
  />
</template>`

const srcSlot = `<template>
  <FaAlert icon="i-lucide:terminal" title="命令执行完成">
    <template #description>
      <div>
        已成功生成文件，可继续进行下一步操作。
        <FaButton variant="link" class="px-0 h-auto">
          查看详情
        </FaButton>
      </div>
    </template>
  </FaAlert>
</template>`

const props = [
  ['<code>icon</code>', '<code>string</code>', '—', '左侧图标名称（Iconify 类名，如 <code>i-lucide:info</code>）；设置后告警切换为「图标 + 内容」两列布局'],
  ['<code>title</code>', '<code>string</code>', '—', '标题文字，渲染为 <code>AlertTitle</code>'],
  ['<code>description</code>', '<code>string</code>', '—', '描述文字；也可用 <code>#description</code> 插槽传入富内容，插槽优先'],
  ['<code>variant</code>', "<code>'default' | 'destructive'</code>", "<code>'default'</code>", '视觉变体；<code>destructive</code> 为危险告警样式'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]
</script>

# FaAlert 告警

用于页面中展示需要用户关注的警告性信息，支持普通与危险两种变体。基于 Tailwind 样式封装。

> 源码位置：`yudream-frontend/packages/components/src/basic/alert/index.vue`

## 基础用法

通过 `title` 与 `description` 设置内容，`icon` 传入 Iconify 图标类名。

<Demo title="基础告警" description="icon + title + description 的标准形态" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="alert" /></ClientOnly>
</Demo>

## 危险告警

设置 `variant="destructive"` 展示危险操作提示，描述文字会以危险色呈现。

<Demo title="危险告警" description="删除确认、不可逆操作等场景" :source="srcVariant">
  <div style="border: 1px solid var(--vp-c-divider); border-radius: 8px; padding: 12px 16px; display: grid; grid-template-columns: 16px 1fr; gap: 2px 12px;">
    <span style="font-size: 14px;">⚠️</span>
    <strong style="font-size: 14px; color: var(--vp-c-danger-1);">危险提示</strong>
    <span></span>
    <span style="font-size: 13px; color: var(--vp-c-danger-1); opacity: .85;">删除后数据将无法恢复，请谨慎操作。</span>
  </div>
</Demo>

## 使用插槽自定义描述

需要插入按钮、链接等富内容时，使用 `#description` 插槽代替 `description` 属性，插槽内容优先级更高。

<Demo title="自定义描述" description="#description 插槽内嵌 FaButton" :source="srcSlot">
  <div style="border: 1px solid var(--vp-c-divider); border-radius: 8px; padding: 12px 16px; display: grid; grid-template-columns: 16px 1fr; gap: 2px 12px;">
    <span style="font-size: 14px;">💻</span>
    <strong style="font-size: 14px;">命令执行完成</strong>
    <span></span>
    <span style="font-size: 13px; color: var(--vp-c-text-2);">已成功生成文件，可继续进行下一步操作。<a href="#">查看详情</a></span>
  </div>
</Demo>

## 示例讲解

以基础用法为例：

```vue
<FaAlert
  icon="i-lucide:info"
  title="提示信息"
  description="这是一条普通提示，用于展示页面说明或操作反馈。"
/>
```

- `icon` 接收 Iconify 图标类名，内部由内置 `Icon` 组件渲染；未设置时不占位。
- 设置 `icon` 后组件追加两列网格 class，图标与标题/描述左对齐排列。
- `title` 渲染为加粗标题行并单行截断；`description` 为次要色说明文字。

## API

### Props

<ApiTable title="FaAlert Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `description` | 自定义描述内容，优先于 `description` 属性 |

### Emits

无自定义 emits。
