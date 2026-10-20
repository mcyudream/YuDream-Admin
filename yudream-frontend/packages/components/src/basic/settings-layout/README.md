# FaSettingsLayout 设置页布局

设置页骨架组件：宽屏（lg 及以上）左侧竖向吸顶导航 + 右侧内容面板；窄屏不做横向标签，改用分区选择器（下拉）切换分区。默认整块内容居中限宽（参考现代后台设置页版式），`surface` 模式下铺满白底、替代卡片式的 `YdPageMain`。

## 使用场景

- 系统设置、个人设置等多分区页面
- 插件设置页分区导航
- 任何「左侧分类 + 右表单」的版式

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `modelValue` | `string \| number` | - | 当前激活的分区值 |
| `list` | `Array<{ icon?: string, label: string, value: string \| number, class?: HTMLAttributes['class'] }>` | **必需** | 分区列表 |
| `title` | `string` | - | 页内标题；不传且无 header/title 插槽时整个头部不渲染 |
| `description` | `string` | - | 页内说明，与标题同排 |
| `align` | `'center' \| 'start'` | `'center'` | `center` 居中并限宽；`start` 左对齐、不加默认宽度限制 |
| `surface` | `boolean` | `false` | 页面级铺满：整块白底（`bg-card`）并至少占满可视高度，用于替换卡片式 `YdPageMain`；自带水平内边距 `px-5 lg:px-6`，窄屏内容不贴可视区边缘 |
| `widthClass` | `string` | `'max-w-4xl'` | 居中模式的最大宽度类；页面 `class` 中同名类可覆盖（twMerge） |
| `class` | `HTMLAttributes['class']` | - | 容器 CSS 类 |
| `headerClass` | `HTMLAttributes['class']` | - | 页内头部 CSS 类 |
| `navClass` | `HTMLAttributes['class']` | - | 宽屏竖向导航 CSS 类 |
| `listClass` | `HTMLAttributes['class']` | - | 窄屏横向标签列表 CSS 类 |
| `contentClass` | `HTMLAttributes['class']` | - | 内容面板 CSS 类 |

## Slots

| 名称 | 说明 |
|------|------|
| `[value]` | 对应分区的内容（动态插槽名） |
| `header` | 完全自定义页内头部（替代 title/description） |
| `title` / `description` | 自定义标题/说明内容 |

## Events

| 事件名 | 参数 | 说明 |
|--------|------|------|
| `update:modelValue` | `value: string \| number` | 分区切换时触发 |

## 使用示例

```vue
<YdSettingsLayout
  v-model="active"
  title="系统设置"
  description="管理站点基础信息、品牌素材与登录方式。"
  :list="[
    { icon: 'morph:settings', label: '基础设置', value: 'basic' },
    { icon: 'morph:image', label: '品牌素材', value: 'brand' },
  ]"
>
  <template #basic>
    <YdSettingsSection title="基础信息" description="站点名称与版权信息。">
      <!-- 表单 -->
      <template #footer>
        <YdButton>保存设置</YdButton>
      </template>
    </YdSettingsSection>
  </template>
</YdSettingsLayout>
```

## 注意事项

1. **动态插槽**：内容插槽名对应分区的 `value` 值
2. **默认居中**：不传 `align` 时整块内容在父容器中居中并限宽 896px；如页面需要左对齐铺满，传 `align="start"`；需要更宽（例如内部含管理表格）可传 `widthClass="max-w-5xl"` 或在页面 `class` 中覆盖 `max-w-*`
3. **吸顶依赖滚动容器**：竖向导航使用 `sticky`，页面需要在可滚动的布局内（宿主 `YdPageMain` 页面天然满足）
4. **建议搭配**：内容面板内使用 `YdSettingsSection` 排版每个分区；页面若已有 `YdPageHeader` 页头，不再传 `title`/`description`，避免双标题
5. **页面级用法（宿主设置页规范）**：设置类页面不再套 `YdPageMain` 卡片，改为页面根节点 `min-h-full bg-card` + `<YdSettingsLayout surface>`，页面自身滚动、导航吸顶；保存类操作统一放在 `YdPageHeader`（多分区页面按当前分区切换按钮），分区内不再放「页底保存」按钮；分区 `#footer` 留给对话框式确认，不放常规保存
6. **响应式行为**：lg（1024px）以下不再渲染横向标签，而是显示分区选择器（`YdSelect`，按 `list` 的 label 生成候选），切换后正文直接展示对应分区；无需调用方处理
7. **surface 模式内边距**：`surface` 模式页面自身铺满白底、没有外层卡片补留白，水平方向由组件自带 `px-5 lg:px-6`（滚动模式下页头、分区选择器与内容区一并生效）；非 surface 模式不加水平内边距，由外部容器（如 `YdPageMain`）负责
