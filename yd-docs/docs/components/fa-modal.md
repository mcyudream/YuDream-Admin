<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicModal from '../.vitepress/theme/components/demos/InteractiveBasicModal.vue'

const srcBasic = `<script setup>
import { FaModal } from '@yudream/components'
import { useFaModal } from '@yudream/components'
import { ref } from 'vue'

const visible = ref(false)

// 命令式：确认弹窗
const modal = useFaModal()
function remove() {
  modal.confirm({
    title: '删除确认',
    content: '删除后不可恢复，确定删除吗？',
    async beforeClose(action, done) {
      if (action === 'confirm') {
        await api.remove(id)
      }
      done()
    },
  })
}
<\/script>

<template>
  <FaModal v-model="visible" title="编辑资源" :closable="true" @confirm="onConfirm">
    <FormContent />
    <template #footer>
      <FaButton variant="outline" @click="visible = false">取消</FaButton>
      <FaButton @click="onConfirm">确定</FaButton>
    </template>
  </FaModal>
</template>`

const props = [
  ['<code>v-model</code>', '<code>boolean</code>', '<code>false</code>', '是否可见'],
  ['<code>title</code>', "<code>string \\| () => string</code>", '—', '标题'],
  ['<code>description</code>', "<code>string \\| () => string</code>", '—', '描述文案'],
  ['<code>icon</code>', "<code>'info' \\| 'success' \\| 'warning' \\| 'error'</code>", '—', '图标类型'],
  ['<code>closable</code>', '<code>boolean</code>', '<code>true</code>', '显示关闭按钮'],
  ['<code>loading</code>', '<code>boolean</code>', '<code>false</code>', '加载态'],
  ['<code>maximize</code> / <code>maximizable</code>', '<code>boolean</code>', '<code>false</code>', '最大化 / 显示最大化按钮'],
  ['<code>draggable</code>', '<code>boolean</code>', '<code>false</code>', '可拖拽'],
  ['<code>center</code> / <code>alignCenter</code>', '<code>boolean</code>', '<code>false</code>', '居中方式'],
  ['<code>overlay</code> / <code>overlayBlur</code>', '<code>boolean</code>', '—', '遮罩与模糊'],
  ['<code>showConfirmButton</code> / <code>showCancelButton</code>', '<code>boolean</code>', '<code>true</code>', '确认/取消按钮显隐'],
  ['<code>confirmButtonText</code> / <code>cancelButtonText</code>', '<code>string</code>', "'确定' / '取消'", '按钮文案'],
  ['<code>confirmButtonDisabled</code> / <code>confirmButtonLoading</code>', '<code>boolean</code>', '<code>false</code>', '确认按钮禁用/加载'],
  ['<code>beforeClose</code>', "<code>(action: 'confirm' \\| 'cancel' \\| 'close', done) => void</code>", '—', '关闭前拦截，异步校验后调 done()'],
  ['<code>closeOnClickOverlay</code> / <code>closeOnPressEscape</code>', '<code>boolean</code>', '<code>true</code>', '遮罩点击/Esc 关闭'],
  ['<code>destroyOnClose</code>', '<code>boolean</code>', '<code>false</code>', '关闭时销毁内容'],
  ['<code>zIndex</code>', '<code>number</code>', '—', '层级'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: boolean) => void</code>', '可见性变化'],
  ['<code>open</code> / <code>opened</code>', '—', '打开前 / 打开动画结束'],
  ['<code>close</code> / <code>closed</code>', '—', '关闭前 / 关闭动画结束'],
  ['<code>confirm</code>', '—', '点击确认'],
  ['<code>cancel</code>', '—', '点击取消'],
]
</script>

# FaModal 对话框

模态对话框，支持命令式 API。基于 reka-ui 封装。

## 基础用法

<Demo title="声明式 + 命令式" description="声明式 v-model 控制复杂表单弹窗；useFaModal().confirm 用于轻量确认" :source="srcBasic">
  <ClientOnly><InteractiveBasicModal /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaModal Props" :data="props" />

### Emits

<ApiTable title="FaModal Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 内容区 |
| `header` | 自定义头部 |
| `footer` | 自定义底部 |

### 命令式 useFaModal()

```ts
const modal = useFaModal()
modal.create(options)   // => { open, close, update }
modal.info(options)
modal.success(options)
modal.warning(options)
modal.error(options)
modal.confirm({ title, content, beforeClose, ... })  // 支持异步 beforeClose 拦截
```

`options.content` 可传 `Component | VNode | string`。

::: tip FaDrawer
抽屉组件结构与 Modal 一致，额外支持 `side?: 'top' | 'bottom' | 'left' | 'right'`、`centered`、`bordered`；命令式入口 `useFaDrawer().create(options)`。
:::
