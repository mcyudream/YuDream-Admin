# YuDream Admin 表格设计规范

本文档定义 YuDream Admin 后台管理系统中表格组件的统一视觉规范与使用标准。

## 1. 设计原则

### 信息层级清晰
- 表头使用浅背景和加粗字体,区分于数据行
- 重要信息(如用户名)使用 `font-medium`,次要信息(如邮箱)使用 `text-muted-foreground`
- 状态使用徽章组件直观呈现

### 扫描效率优先
- 单元格垂直对齐,保持视线平滑移动
- 悬停高亮整行,帮助用户聚焦当前数据
- 操作按钮收纳到下拉菜单,减少视觉干扰

### 响应式友好
- 桌面端使用完整表格,展示所有列
- 移动端切换为卡片列表,保留关键信息
- 操作菜单在两端保持一致的交互方式

## 2. 视觉规范

### 2.1 颜色与主题

**背景色**
```css
--table-bg: transparent                    /* 表格背景,透明以适配容器 */
--table-header-bg: var(--color-fill-1)    /* 表头背景,浅灰 */
--table-hover-bg: var(--color-fill-1)     /* 悬停背景,同表头 */
--table-border-color: var(--color-border-2) /* 边框颜色,细线 */
```

**文字颜色**
```css
--table-header-text: var(--color-text-2)  /* 表头文字,次要 */
--table-cell-text: var(--color-text-1)    /* 单元格文字,主要 */
--table-cell-text-secondary: var(--color-text-3) /* 次要信息,如邮箱 */
```

### 2.2 尺寸与间距

**单元格尺寸**
- 最小高度: 64px (图文并茂场景)
- 标准高度: 48px (纯文本场景)
- 紧凑高度: 40px (紧凑外观)

**内边距**
- 水平: 16px (左右各 16px)
- 垂直: 12px (上下各 12px,图文场景)
- 垂直: 8px (上下各 8px,纯文本场景)

**间距节奏**
- 头像与文字: 12px (gap-3)
- 姓名与副文本: 4px (gap-1)
- 徽章内边距: 4px 8px

### 2.3 字体

**表头**
```css
font-size: 13px;
font-weight: 600;
text-transform: uppercase;
letter-spacing: 0.025em;
```

**单元格主文本**
```css
font-size: 14px;
font-weight: 500; /* 重要字段如姓名 */
font-weight: 400; /* 普通字段 */
```

**单元格副文本**
```css
font-size: 12px;
font-weight: 400;
color: var(--color-text-3);
```

### 2.4 圆角

- 表格容器: 8px (跟随卡片)
- 头像: 50% (圆形)
- 徽章: 4px
- 下拉菜单: 8px

## 3. 组件选型

### 3.1 YdTable
**适用场景**: 桌面专属、内部小型表格  
**特点**: TanStack Table 核心,完全可定制  
**限制**: 不自带移动端方案

### 3.2 YdResponsiveTable
**适用场景**: 需要桌面+移动双端支持的数据列表  
**特点**: 自动断点切换,卡片视图可定制  
**最佳实践**: 使用 `#card` 插槽自定义移动端展示

### 3.3 YdEnhancedTable (新增)
**适用场景**: 用户列表、成员管理等图文并茂场景  
**特点**: 
- 内置用户信息列(头像+姓名+邮箱)
- 内置状态徽章映射
- 内置操作下拉菜单
- 动态禁用操作项

**何时使用**:
- ✅ 数据行包含用户/成员/联系人等身份信息
- ✅ 需要直观展示状态(活跃/停用/待审核等)
- ✅ 操作按钮多于 2 个,希望收纳到菜单
- ❌ 移动端为主要使用场景(建议配合 YdResponsiveTable)
- ❌ 表格列结构复杂,需要深度定制

## 4. 交互规范

### 4.1 悬停反馈
```css
tbody tr {
  transition: background-color 0.15s ease;
}
tbody tr:hover {
  background-color: var(--table-hover-bg);
}
```

### 4.2 操作菜单
**触发方式**:
- 桌面: 点击三点按钮
- 移动: 长按行或点击三点按钮

**菜单项顺序**:
1. 主要操作(查看、编辑)
2. 次要操作(重置密码、发送邮件)
3. 危险操作(删除、禁用) - 使用红色文字

**状态**:
- 启用: 正常文字颜色
- 禁用: `text-muted-foreground` + `cursor-not-allowed`
- 危险: `text-destructive`

### 4.3 选择交互
**单选**: 使用单选按钮列  
**多选**: 使用复选框列  
**跨页选择**: 保留已选状态,提供"全选本页"和"清除全部"

## 5. 状态展示

### 5.1 状态徽章变体

| 状态 | 变体 | 颜色 | 使用场景 |
|------|------|------|----------|
| 正常/活跃 | `success` | 绿色 | Active, Enabled, Online |
| 警告/待处理 | `warning` | 橙色 | Pending, Processing, Warning |
| 错误/停用 | `danger` | 红色 | Suspended, Error, Disabled |
| 中性/邀请 | `default` | 灰色 | Invited, Neutral, Unknown |

### 5.2 徽章放置
- 优先: 独立列,居中对齐
- 次选: 与名字同列,右对齐
- 避免: 与操作按钮同列

## 6. 图文组合规范

### 6.1 用户信息列
**标准结构**:
```
[头像 40x40] [姓名(font-medium 14px)          ]
             [邮箱(text-muted 12px)           ]
```

**间距**:
- 头像到文字: 12px
- 姓名到邮箱: 4px

**对齐**:
- 头像: 垂直居中
- 文字块: 垂直居中
- 文字: 左对齐

### 6.2 缩略图列
**尺寸**: 48x48px (小) / 64x64px (中) / 80x80px (大)  
**圆角**: 4px (产品/文章) / 50% (头像)  
**占位**: 使用图标占位符,不留空白

### 6.3 图标列
**尺寸**: 16px (小) / 20px (中) / 24px (大)  
**颜色**: 继承文字颜色或使用主题色  
**对齐**: 与文字基线对齐

## 7. 空状态与加载

### 7.1 空状态
```vue
<YdEmpty
  icon="morph:inbox"
  title="没有数据"
  description="暂时没有符合条件的记录"
/>
```

### 7.2 加载状态
```vue
<YdLoadingOverlay :loading="isLoading">
  <YdEnhancedTable ... />
</YdLoadingOverlay>
```

### 7.3 错误状态
```vue
<YdAlert variant="danger">
  <template #icon><YdIcon name="morph:alert-circle" /></template>
  <template #title>加载失败</template>
  <template #default>
    <div>无法加载数据,请稍后重试</div>
    <YdButton size="sm" @click="reload">重新加载</YdButton>
  </template>
</YdAlert>
```

## 8. 辅助功能(A11y)

### 8.1 键盘导航
- Tab: 聚焦下一个可操作元素
- Enter/Space: 激活当前元素
- 方向键: 移动选择(可选)

### 8.2 屏幕阅读器
- 表头使用 `<th scope="col">`
- 操作按钮必须有 `aria-label`
- 状态徽章使用语义化文本

### 8.3 对比度
- 文字与背景对比度 ≥ 4.5:1
- 悬停状态清晰可辨
- 禁用状态对比度 ≥ 3:1

## 9. 性能优化

### 9.1 虚拟滚动
数据行超过 100 行时,考虑使用虚拟滚动:
```vue
<YdTable
  :data="largeDataset"
  :enable-virtual="true"
  :row-height="48"
/>
```

### 9.2 懒加载图片
头像和缩略图使用懒加载:
```vue
<img :src="avatarUrl" loading="lazy" />
```

### 9.3 分页策略
- 默认每页: 20 条
- 可选: 10 / 20 / 50 / 100
- 大数据集: 使用服务端分页

## 10. 迁移检查清单

从 Arco Table 迁移到 YdEnhancedTable:

- [ ] 将 Arco 列定义转换为 TanStack 列配置
- [ ] 提取用户信息字段,配置 `userColumn`
- [ ] 提取状态字段,配置 `statusColumn` 和 `statusMap`
- [ ] 将操作按钮转换为 `actions` 数组
- [ ] 验证 ID 字段保持字符串类型
- [ ] 测试明暗主题和三套外观
- [ ] 验证操作菜单的动态禁用逻辑
- [ ] 检查移动端响应式表现(如需要)
- [ ] 运行 UI 门禁检查,确保零违规

## 11. 示例代码

完整示例见:
- 组件文档: `docs/yd-enhanced-table.md`
- 实时预览: `apps/component-showcase/src/sections/EnhancedTableSection.vue`
- 业务示例: `apps/core-arco-design-vue/src/views/system/user/index.vue` (待更新)

## 12. 相关资源

- [YD 组件使用规范](./yd-ui-guidelines.md)
- [YD 组件 API 文档](./yd-components.md)
- [前端工程规范](./dev/frontend.md)
- [shadcn-admin 参考](https://shadcn-admin.netlify.app/users)
