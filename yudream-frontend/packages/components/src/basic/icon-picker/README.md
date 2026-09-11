# YdIconPicker 系统图标选择器

弹出式系统图标选择器，默认提供 Remix Icon 全量清单（约 3200 个），选中值写入 `i-ri:*` 图标名，可直接交给 `FaIcon` 渲染。网格按页浏览，每页 64 个。

## 使用场景

- 菜单图标配置
- 导航、快捷入口等需要选择系统图标的表单
- 需要预览并搜索 Iconify / UnoCSS 图标名的输入场景

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `string \| undefined` | - | 选中图标名，例如 `i-ri:settings-3-line` |
| `placeholder` | `string` | `'选择系统图标'` | 未选择时的占位文案 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `clearable` | `boolean` | `true` | 是否允许清空 |
| `class` | `HTMLAttributes['class']` | - | 触发器 CSS 类 |

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| `change` | 选择或清空时触发 | `value: string \| undefined` |
