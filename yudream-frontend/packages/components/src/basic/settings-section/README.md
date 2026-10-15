# FaSettingsSection 设置分区

设置页内单分区的标准排版：标题 + 说明 + 分隔线 + 内容 + 底部操作栏。可独立使用，也常作为 `FaSettingsLayout` 内容面板的分区单元。

## 使用场景

- 设置页的每个分区（如「个人资料」「通知偏好」）
- 详情页内「标题 + 说明 + 表单 + 保存栏」的任意区块

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `title` | `string` | - | 分区标题 |
| `description` | `string` | - | 分区说明，无内容时不渲染 |
| `class` | `HTMLAttributes['class']` | - | 容器 CSS 类 |
| `headerClass` | `HTMLAttributes['class']` | - | 头部（标题/说明）CSS 类 |
| `contentClass` | `HTMLAttributes['class']` | - | 内容区 CSS 类 |
| `footerClass` | `HTMLAttributes['class']` | - | 底部操作栏 CSS 类 |

## Slots

| 名称 | 说明 |
|------|------|
| `default` | 分区内容（表单等） |
| `title` | 自定义标题 |
| `description` | 自定义说明 |
| `footer` | 底部操作栏（通常是保存按钮），提供后才渲染 |

## 注意事项

1. **footer 按需渲染**：只有提供 `#footer` 插槽才出现分隔线和操作栏
2. **多字段排版**：分区内容默认纵向排布，多列栅格由调用方用 `grid` 类自行组合
