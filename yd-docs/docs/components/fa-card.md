<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingMisc from '../.vitepress/theme/components/demos/InteractiveRemainingMisc.vue'

const srcBasic = `<template>
  <FaCard title="卡片标题" class="w-80">
    卡片内容
  </FaCard>
</template>`

const srcDesc = `<template>
  <FaCard
    title="卡片标题"
    description="这是一段辅助描述，用于补充说明卡片内容。"
    class="w-80"
  >
    卡片内容
  </FaCard>
</template>`

const srcSlot = `<template>
  <FaCard class="w-96">
    <template #header>
      <div class="flex gap-4 items-center justify-between">
        <div>
          <div class="text-base font-semibold">自定义头部</div>
          <div class="text-sm text-muted-foreground">
            header slot 会覆盖 title 和 description
          </div>
        </div>
        <FaIcon name="i-lucide:badge-check" class="text-primary size-5" />
      </div>
    </template>

    卡片内容区域

    <template #footer>
      <div class="flex gap-2 w-full justify-end">
        <FaButton variant="outline">取消</FaButton>
        <FaButton>确定</FaButton>
      </div>
    </template>
  </FaCard>
</template>`

const srcCustom = `<template>
  <FaCard
    title="自定义样式"
    description="通过 class、headerClass、contentClass 和 footerClass 调整区域样式。"
    class="py-0 gap-0 w-96 overflow-hidden"
    header-class="bg-primary/8 py-4"
    content-class="py-6 text-sm leading-6"
    footer-class="bg-muted text-sm text-muted-foreground py-3"
  >
    这里是自定义内容区域，可以根据业务场景调整间距、背景和边框。
    <template #footer>
      自定义页脚区域
    </template>
  </FaCard>
</template>`

const props = [
  ['<code>title</code>', '<code>string</code>', '—', '标题文本，渲染为 CardTitle'],
  ['<code>description</code>', '<code>string</code>', '—', '描述文本，渲染为 CardDescription，位于标题下方'],
  ['<code>class</code>', '<code>string</code>', '—', '透传到卡片根元素'],
  ['<code>headerClass</code>', '<code>string</code>', '—', '透传到头部区域（CardHeader）'],
  ['<code>contentClass</code>', '<code>string</code>', '—', '透传到内容区域（CardContent）'],
  ['<code>footerClass</code>', '<code>string</code>', '—', '透传到页脚区域（CardFooter）'],
]
</script>

# FaCard 卡片

通用内容容器，将内容分组在带边框和阴影的卡片中。内部由 `Card` / `CardHeader` / `CardTitle` / `CardDescription` / `CardContent` / `CardFooter` 组合而成（基于 reka-ui 风格封装 + UnoCSS 样式）。

## 基础用法

<Demo title="基础卡片" description="通过 title 属性设置标题，默认插槽放置内容" :source="srcBasic">
  <ClientOnly><InteractiveRemainingMisc type="card" /></ClientOnly>
</Demo>

## 带描述

<Demo title="标题 + 描述" description="description 渲染在标题下方，使用弱化文字样式" :source="srcDesc">
  <div style="width: 320px; border: 1px solid var(--vp-c-border); border-radius: 12px; padding: 24px; box-shadow: 0 1px 2px rgba(0,0,0,.05);">
    <div style="font-weight: 600;">卡片标题</div>
    <div style="color: var(--vp-c-text-2); font-size: 14px; margin: 4px 0 12px;">这是一段辅助描述，用于补充说明卡片内容。</div>
    <div style="color: var(--vp-c-text-2); font-size: 14px;">卡片内容</div>
  </div>
</Demo>

## 插槽

`header` 插槽会完全覆盖 `title` 和 `description` 的默认渲染，适合在头部放图标、操作按钮等自定义布局；`footer` 插槽渲染底部区域，常放操作按钮。

<Demo title="自定义头部与页脚" description="header 插槽覆盖 title/description，footer 插槽放置操作按钮" :source="srcSlot">
  <div style="width: 384px; max-width: 100%; border: 1px solid var(--vp-c-border); border-radius: 12px; padding: 24px; box-shadow: 0 1px 2px rgba(0,0,0,.05);">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
      <div>
        <div style="font-weight: 600;">自定义头部</div>
        <div style="color: var(--vp-c-text-2); font-size: 14px;">header slot 会覆盖 title 和 description</div>
      </div>
    </div>
    <div style="color: var(--vp-c-text-2); font-size: 14px; margin-bottom: 12px;">卡片内容区域</div>
    <div style="display: flex; gap: 8px; justify-content: flex-end;">
      <span style="border: 1px solid var(--vp-c-border); border-radius: 6px; padding: 4px 12px; font-size: 14px;">取消</span>
      <span style="background: var(--vp-c-brand-1); color: #fff; border-radius: 6px; padding: 4px 12px; font-size: 14px;">确定</span>
    </div>
  </div>
</Demo>

## 自定义样式

通过 `class`、`headerClass`、`contentClass`、`footerClass` 分别调整四个区域的样式（UnoCSS 原子类直接透传并合并）。

<Demo title="分区定制样式" description="每个区域独立透传 class，可覆盖间距、背景、文字等" :source="srcCustom">
  <div style="width: 384px; max-width: 100%; border: 1px solid var(--vp-c-border); border-radius: 12px; overflow: hidden; box-shadow: 0 1px 2px rgba(0,0,0,.05);">
    <div style="background: var(--vp-c-bg-soft); padding: 16px 24px;">
      <div style="font-weight: 600;">自定义样式</div>
      <div style="color: var(--vp-c-text-2); font-size: 14px;">headerClass 控制头部背景与间距</div>
    </div>
    <div style="padding: 24px; font-size: 14px; color: var(--vp-c-text-2);">contentClass 控制内容区样式。</div>
    <div style="background: var(--vp-c-bg-soft); padding: 12px 24px; font-size: 14px; color: var(--vp-c-text-2);">footerClass 控制页脚样式</div>
  </div>
</Demo>

## 渲染规则

```mermaid
flowchart TD
  A[FaCard] --> B{"header 插槽 / title / description<br/>任一存在？"}
  B -->|是| C[渲染 CardHeader]
  B -->|否| D[不渲染头部]
  C --> E{"有 header 插槽？"}
  E -->|是| F[渲染插槽内容]
  E -->|否| G[渲染 CardTitle + CardDescription]
  A --> H{"有默认插槽？"}
  H -->|是| I[渲染 CardContent]
  A --> J{"有 footer 插槽？"}
  J -->|是| K[渲染 CardFooter]
```

各区域默认样式（可被对应 `*Class` 覆盖）：

| 区域 | 默认样式要点 |
| --- | --- |
| 根（Card） | `bg-card text-card-foreground flex flex-col gap-6 overflow-hidden rounded-xl border py-6 shadow-sm` |
| 头部（CardHeader） | `grid auto-rows-min items-start gap-1.5 px-6`，含 CardAction 时自动切换为两列布局 |
| 内容（CardContent） | `min-w-0 break-words px-6` |
| 页脚（CardFooter） | `flex items-center px-6` |

## API

### Props

<ApiTable title="FaCard Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `header` | 自定义头部，存在时覆盖 `title` / `description` |
| `default` | 卡片内容，渲染在 CardContent 中 |
| `footer` | 页脚区域，渲染在 CardFooter 中 |

### Emits

无自定义 emits。

## 注意事项

- 三个区域都是条件渲染：没有 `title`/`description`/`header` 插槽就不渲染头部，没有默认插槽就不渲染内容区，没有 `footer` 插槽就不渲染页脚。
- 组件宽度默认撑满父容器，示例中的 `w-80` / `w-96` 仅用于演示。
- 背景、文字颜色使用 `bg-card` / `text-card-foreground` 等中性语义变量，深浅模式由主题自动切换，业务侧不要写死颜色。

## 源码引用

- `yudream-frontend/packages/components/src/basic/card/index.vue`
- `yudream-frontend/packages/components/src/basic/card/card/`（Card、CardHeader、CardTitle、CardDescription、CardContent、CardFooter、CardAction）
