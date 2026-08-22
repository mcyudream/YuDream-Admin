<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicDrawer from '../.vitepress/theme/components/demos/InteractiveBasicDrawer.vue'

const srcBasic = `<script setup>
import { ref } from 'vue'

// 组件实际使用时无需手动导入，框架会自动导入
const open = ref(false)
<\/script>

<template>
  <FaButton @click="open = true">打开抽屉</FaButton>
  <FaDrawer v-model="open" title="抽屉标题" description="抽屉描述文字">
    <div class="text-sm text-muted-foreground">
      这里是抽屉内容区域。
    </div>
  </FaDrawer>
</template>`

const srcSide = `<script setup>
import { reactive } from 'vue'

const drawers = reactive({ top: false, bottom: false, left: false, right: false })
<\/script>

<template>
  <FaButton @click="drawers.top = true">上方</FaButton>
  <FaButton @click="drawers.bottom = true">下方</FaButton>
  <FaButton @click="drawers.left = true">左侧</FaButton>
  <FaButton @click="drawers.right = true">右侧</FaButton>

  <FaDrawer v-model="drawers.top" side="top" title="上方抽屉">从页面顶部弹出。</FaDrawer>
  <FaDrawer v-model="drawers.bottom" side="bottom" title="下方抽屉">从页面底部弹出。</FaDrawer>
  <FaDrawer v-model="drawers.left" side="left" title="左侧抽屉">从页面左侧弹出。</FaDrawer>
  <FaDrawer v-model="drawers.right" side="right" title="右侧抽屉">从页面右侧弹出。</FaDrawer>
</template>`

const props = [
  ['<code>v-model</code>（modelValue）', '<code>boolean</code>', '<code>false</code>', '是否打开抽屉'],
  ['<code>id</code>', '<code>string</code>', '—', '抽屉唯一标识，缺省自动生成'],
  ['<code>side</code>', "<code>'top' \\| 'bottom' \\| 'left' \\| 'right'</code>", "<code>'right'</code>", '弹出方向（与 Modal 的核心差异）'],
  ['<code>title</code>', "<code>string \\| () => string</code>", '—', '标题'],
  ['<code>description</code>', "<code>string \\| () => string</code>", '—', '描述文案，为空时不渲染'],
  ['<code>zIndex</code>', '<code>number</code>', '<code>2000</code>', '层级'],
  ['<code>loading</code>', '<code>boolean</code>', '<code>false</code>', '内容区整体加载遮罩'],
  ['<code>closable</code>', '<code>boolean</code>', '<code>true</code>', '显示右上角关闭按钮'],
  ['<code>centered</code>', '<code>boolean</code>', '<code>false</code>', '标题/描述/底部按钮居中'],
  ['<code>bordered</code>', '<code>boolean</code>', '<code>true</code>', '头部、底部是否带分割边框'],
  ['<code>overlay</code> / <code>overlayBlur</code>', '<code>boolean</code>', '<code>true</code> / <code>false</code>', '遮罩显隐 / 遮罩模糊'],
  ['<code>showConfirmButton</code> / <code>showCancelButton</code>', '<code>boolean</code>', '<code>true</code> / <code>false</code>', '确认/取消按钮显隐'],
  ['<code>confirmButtonText</code> / <code>cancelButtonText</code>', "<code>string \\| () => string</code>", "'确定' / '取消'", '按钮文案'],
  ['<code>confirmButtonDisabled</code> / <code>confirmButtonLoading</code>', '<code>boolean</code>', '<code>false</code>', '确认按钮禁用 / 加载'],
  ['<code>beforeClose</code>', "<code>(action: 'confirm' \\| 'cancel' \\| 'close', done: () => void) => void</code>", '—', '关闭前拦截，异步校验后调用 done() 才真正关闭'],
  ['<code>header</code> / <code>footer</code>', '<code>boolean</code>', '<code>true</code>', '头部 / 底部区域显隐'],
  ['<code>closeOnClickOverlay</code> / <code>closeOnPressEscape</code>', '<code>boolean</code>', '<code>true</code>', '点击遮罩 / 按 Esc 是否关闭'],
  ['<code>destroyOnClose</code>', '<code>boolean</code>', '<code>true</code>', '关闭时销毁内容；为 false 且打开过则 forceMount 保留内容'],
  ['<code>openAutoFocus</code>', '<code>boolean</code>', '<code>false</code>', '打开时是否自动聚焦内容内首个可聚焦元素'],
  ['<code>contentClass</code> / <code>headerClass</code> / <code>footerClass</code>', "<code>HTMLAttributes['class']</code>", '—', '面板 / 头部 / 底部追加 class'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: boolean) => void</code>', '打开状态变化'],
  ['<code>open</code> / <code>opened</code>', '—', '打开时 / 打开动画结束'],
  ['<code>close</code> / <code>closed</code>', '—', '关闭时 / 关闭动画结束'],
  ['<code>confirm</code>', '—', '点击确认按钮（beforeClose 放行后触发）'],
  ['<code>cancel</code>', '—', '点击取消按钮（beforeClose 放行后触发）'],
]
</script>

# FaDrawer 抽屉

从屏幕边缘滑出的抽屉面板，内置头部（标题/描述）、内容区和底部操作按钮。基于 reka-ui Dialog（Sheet）封装，交互模型与 [FaModal](./fa-modal.md) 同构。

## 与 FaModal 的同构差异

`DrawerProps` 与 `ModalProps` 的头部、底部按钮、`beforeClose` 拦截、事件序列完全一致，差异仅在以下字段：

| 差异点 | FaDrawer | FaModal |
| --- | --- | --- |
| 弹出位置 | `side: 'top' \| 'bottom' \| 'left' \| 'right'`，默认 `'right'` | 固定居中弹窗 |
| 居中/边框 | `centered`（标题与按钮居中）、`bordered`（分割线，默认 `true`） | `center` / `alignCenter` / `border` |
| 特有弹窗能力 | 无 | `icon`、`maximize` / `maximizable`、`draggable` |
| `destroyOnClose` 默认值 | **`true`**（关闭即销毁内容） | `false` |
| 命令式 API | `useFaDrawer().create(options)`，仅 `create` | `useFaModal()` 额外提供 `info/success/warning/error/confirm` 快捷方法 |

```mermaid
flowchart LR
  trigger[触发按钮] --> open[open 事件]
  open --> opened[opened 动画结束]
  opened --> action{用户操作}
  action -->|confirm/cancel/close| beforeClose[beforeClose 拦截?]
  beforeClose -->|done\(\)| close[close 事件]
  beforeClose -->|不调用 done| opened
  close --> closed[closed 动画结束]
```

## 基础用法

<Demo title="声明式抽屉" description="v-model 控制开关，title/description 支持函数形式" :source="srcBasic">
  <ClientOnly><InteractiveBasicDrawer /></ClientOnly>
</Demo>

## 弹出方向

<Demo title="side 四个方向" description="top / bottom / left / right" :source="srcSide">
  <div class="demo-row">
    <button class="demo-btn">上方</button>
    <button class="demo-btn">下方</button>
    <button class="demo-btn">左侧</button>
    <button class="demo-btn">右侧</button>
  </div>
</Demo>

## API

### Props

<ApiTable title="FaDrawer Props" :data="props" />

### Emits

<ApiTable title="FaDrawer Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 内容区，滚动由组件内置 `overflow-y-auto` 处理 |
| `header` | 自定义头部，替换 title/description 的默认渲染 |
| `footer` | 自定义底部，替换默认确认/取消按钮 |

## 命令式调用

通过 `useFaDrawer().create(options)` 命令式创建，详见 [useFaDrawer](./use-fa-drawer.md)。

## 注意事项

- 点击确认/取消按钮**默认会自动关闭**抽屉；需要异步校验（如提交表单）时用 `beforeClose`，在其中 `await` 完成后调用 `done()`，不调用则保持打开。
- `beforeClose` 的 `action` 区分三种触发源：`'confirm'`、`'cancel'`、`'close'`（遮罩 / Esc / 关闭按钮）。
- `loading` 是整个内容区的遮罩；`confirmButtonLoading` 只作用于确认按钮。
- `destroyOnClose` 默认 `true`，与 Modal 相反——表单内容在关闭后会被销毁重挂，若需保留编辑中的状态请显式设为 `false`。
- 底部按钮默认只有"确定"，`showCancelButton: true` 才出现"取消"。

::: info 源码
`yudream-frontend/packages/components/src/basic/drawer/index.vue`、`yudream-frontend/packages/components/src/basic/drawer/index.ts`（`DrawerProps` / `DrawerEmits` 定义）、`yudream-frontend/packages/components/src/basic/drawer/sheet/`（reka-ui Sheet 封装）
:::
