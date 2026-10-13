# 插件前端 YD UI 规范

插件业务界面只使用已发布 `@yudream/components` 提供并经 SDK/resolver 暴露的公开 `Yd*` 入口。不要在业务页面、组件或 composable 中使用 `Fa*`/`useFa*`、裸 Arco/其他通用 UI 控件、原生通用交互元素，或用别名包装绕过规则。允许语义内容、布局元素和真实链接。技术编辑器、图表或流程引擎必须由正式 YD 适配器承载。

新增 UI 能力缺失时，先向宿主组件契约补能力并等待新版本验签；插件只能使用已验签的版本，不能复制宿主组件实现或依赖组件库内部路径。组件行为和选型以宿主同步的 [`YD UI guidelines`](https://ydadocs.yudream.online/) 与当前发布包公开类型为准。

品牌变量仅可在 `@PluginTheme` 明确注册的样式资产内按声明 scope 定义：SITE 用 SITE 页面/公开路由锚点，ADMIN 仅 `:root`/`.dark`。`SITE`/`ADMIN` 同时声明但无法证明资产隔离时扫描器不豁免。任何业务对品牌变量的消费仍禁止；业务样式使用主题中性语义变量。

插件前端 CI 必须安装 workspace 声明的已发布组件包、Vue 编译器和 SDK，并运行 `pnpm run test:ui-audit` 与 `pnpm run audit:ui`。AST 门禁读取安装包的 `resolver` 确认公开入口，扫描完整 Vue SFC、脚本、JSX、第三方控件和主题资产；不得添加工作区基线或宽泛忽略来掩盖新违规。

```vue
<template>
  <YdForm ref="form" :model="model">
    <YdFormItem label="名称" field="name" required>
      <YdInput v-model="model.name" />
    </YdFormItem>
    <YdButton type="submit">保存</YdButton>
  </YdForm>
</template>
```
