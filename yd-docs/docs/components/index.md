# YD 组件库 @yudream/components

YD 是宿主页面、共享业务组件与插件的唯一公开 UI 入口。内部延续 Fantastic-admin 的主题、基础交互与组件实现；业务使用 `Yd*` 和 `useYd*`，不直接使用 Fa、Arco、Reka 或裸通用交互控件。现有能力无法覆盖时，先补齐 YD 封装、主题、公开契约和预览，再接入页面。

## 契约状态

::: warning 发布准备与生产可用分开
当前工作区准备发布 `@yudream/components 1.4.0` 与 `@yudream/plugin-sdk 1.9.0`，包含 **127 个 YD 组件入口**。这不是远程发布或验签成功的声明。只有对应 publish / verify 流水线通过后，插件才能升级依赖并进行全局迁移；状态统一维护在 [契约发布记录](yd-contract-releases.md)。
:::

源码目录、包版本与宿主运行时版本是不同概念。插件不得复制宿主组件、伪造版本或在 remoteEntry 内另外捆绑组件库。先使用已有发布且验签的契约，准备中的新能力只在当前工作区验证。

## 规范与 API

| 内容 | 入口 |
| --- | --- |
| 视觉语言、场景选型与持续维护 | [YD 视觉与组件使用规范](yd-ui-guidelines.md) |
| 基础、集合、日历、动效、流程等组件 API | [YD 通用组件库](yd-components.md) |
| 表单、标签、评分、动作与根装配 | [YD 表单与基础装配契约](yd-form-components.md) |
| Harness、Skills、MCP、工作区与回答协议 | [YD Agent 工作台](yd-agent-workbench.md) |
| 灵动 / 雅致 / 紧凑与主题插件 | [后台外观预设与动效](frontend-appearance.md) |
| 发布、远程验签与插件同步 | [YD 契约发布记录](yd-contract-releases.md) |

以上公开页面直接包含仓库 `docs/` 的权威文档，不维护第二份 API 与选型正文。新增或改变组件的同一工作项必须同步组件、规范、resolver、SDK、预览和检查规则。

## 导入与自动导入

宿主从根入口导入 YD 组件；AI 类型与元件也支持 `@yudream/components/ai` 子入口。插件通过 SDK 的宿主共享桥使用相同入口。

```vue
<script setup lang="ts">
import { YdButton, YdCard, YdInput } from '@yudream/components'
import { ref } from 'vue'

const name = ref('')
</script>

<template>
  <YdCard title="项目名称">
    <YdInput v-model="name" placeholder="输入名称" />
    <YdButton :disabled="!name">保存</YdButton>
  </YdCard>
</template>
```

当前真实 resolver 名称为 `ComponentsResolver`，组合式自动导入映射为 `ComponentsAutoImports`；不能使用历史文档中的不存在入口 `FaResolver`。

```ts
import { ComponentsAutoImports, ComponentsResolver } from '@yudream/components/resolver'

// Components({ resolvers: [ComponentsResolver()] })
// AutoImport({ imports: [ComponentsAutoImports] })
```

页面与预览加载共享 `@yudream/components/appearance.css`。主色、字体与明暗由 FA 主题运行时管理；组件消费统一令牌，ADMIN 主题插件在根变量层覆盖。业务样式只用中性语义变量，不手写品牌色或控件强调态。

## 按任务选择组件

普通操作使用 `YdButton` 与 YD 表单控件；业务列表使用 `YdResponsiveTable` 的移动卡片槽。层级浏览/勾选用 `YdTree`，逐级单值选择用 `YdCascader`，远程分页集合选择用 `YdTablePicker`。步骤位置、历史和顺序任务分别用 `YdSteps`、`YdTimeline`、`YdMultiStepLoader`。浮层按内容规模和是否保留上下文选择 Popover、Modal 或 Drawer。

动效分为功能反馈和视觉装饰。三套外观均保留已启用的功能动画；雅致与紧凑的减少动态效果偏好只柔化速度、幅度与密度，不冻结加载、计数或流光。完整决策与不兼容 API 边界见 [选型规范](yd-ui-guidelines.md)。

## 历史 Fa API 兼容参考

本目录已有 Fa 页面保留，供理解存量契约和迁移时核对参数，**不再作为业务选型推荐**。47 个基础 YD adapter 保留相应 Fa 的行为契约并增加统一外观；例如新页面使用 `YdButton`，历史 `FaButton` 页面可用作其基础参数参考。组件入口迁移仍需核对 props、model、事件、插槽与方法。

Arco 或 FA Pro 的同名参数不能直接复制到 YD，表格、树、多选、日期、上传与命令式浮层尤其需要显式适配。旧组件页的原生控件演示是历史效果示意，不能作为新页面使用裸控件的依据。真实组件与主题验证以主前端 `apps/component-showcase` 的可交互预览为准。

- [历史 FaButton 参数](/components/fa-button)、[FaInput 参数](/components/fa-input)、[FaTable 参数](/components/fa-table)
- [历史 FaModal 参数](/components/fa-modal)、[FaDrawer 参数](/components/fa-drawer)、[FaResponsiveTable 参数](/components/responsive-table)
- [插件宿主共享与工程化](/plugin/frontend-remote)、[插件 SDK](/plugin/sdk/)
