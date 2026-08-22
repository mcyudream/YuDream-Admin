<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveMediaImageUpload from '../.vitepress/theme/components/demos/InteractiveMediaImageUpload.vue'

const srcBasic = `<script setup>
import { ref } from 'vue'

const files = ref([])
async function localRequest({ file, onProgress }) {
  onProgress(20)
  const url = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
  onProgress(100)
  return { url }
}
<\/script>

<template>
  <FaImageUpload v-model="files" :http-request="localRequest" :after-upload="response => response.url" />
</template>`

const srcMultiple = `<template>
  <FaImageUpload v-model="files" :http-request="localRequest" :after-upload="response => response.url" multiple :max="3" />
</template>`

const srcValidation = `<script setup>
function beforeUpload(file) {
  return file.type.startsWith('image/') && file.size <= 200 * 1024
}
<\/script>

<template>
  <FaImageUpload v-model="files" :http-request="localRequest" :after-upload="response => response.url" :before-upload="beforeUpload" :width="200" :height="130" :max="0" />
</template>`

const srcCustomRequest = `<template>
  <!-- localRequest 只用 FileReader 生成 data URL，不发送网络请求 -->
  <FaImageUpload v-model="files" :http-request="localRequest" :after-upload="response => response.url" :max="3" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>string[]</code>', '必填', '图片 URL 数组，上传成功追加、删除/排序直接修改该数组'],
  ['<code>action</code>', '<code>string</code>', "<code>''</code>", '上传接口地址'],
  ['<code>method</code>', '<code>string</code>', "<code>'post'</code>", '默认请求使用的 HTTP 方法'],
  ['<code>headers</code>', '<code>Headers \\| Record&lt;string, any&gt;</code>', '<code>{}</code>', '请求头；Headers 实例会被展开为普通对象'],
  ['<code>data</code>', '<code>Record&lt;string, any&gt;</code>', '<code>{}</code>', '随文件一起提交的额外表单字段'],
  ['<code>name</code>', '<code>string</code>', "<code>'file'</code>", 'FormData 中文件字段的字段名'],
  ['<code>afterUpload</code>', '<code>(response: any) => string \\| Promise&lt;string&gt;</code>', '—', '上传成功后从响应解析图片 URL，返回值 push 进 v-model 数组'],
  ['<code>beforeUpload</code>', '<code>(file: File) => boolean \\| Promise&lt;boolean&gt;</code>', '—', '上传前校验，返回 false 则跳过该文件'],
  ['<code>httpRequest</code>', '<code>(options: UploadRequestOptions) => any \\| Promise&lt;any&gt;</code>', '—', '自定义上传请求，传入后完全替代内置 axios 请求'],
  ['<code>multiple</code>', '<code>boolean</code>', '<code>false</code>', '点击选择时允许一次多选（directory 为 true 时隐含多选）'],
  ['<code>max</code>', '<code>number</code>', '<code>1</code>', '图片数量上限；0 表示不限制，达到上限后隐藏上传按钮'],
  ['<code>width</code> / <code>height</code>', '<code>number</code>', '<code>100</code>', '图片格子与上传按钮的尺寸（px）'],
  ['<code>directory</code>', '<code>boolean</code>', '<code>false</code>', '目录上传模式（webkitdirectory），此模式下不响应粘贴'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用上传、删除、排序与预览入口'],
]

const emits = [
  ['<code>onSuccess</code>', '<code>(response: any, file: File) => void</code>', '单张图片上传成功后触发，response 为 httpRequest 的返回值'],
]
</script>

# FaImageUpload 图片上传

图片专用上传组件。与 FaFileUpload 不同，`v-model` 直接绑定 **图片 URL 字符串数组**（`string[]`），上传成功后组件把 `afterUpload` 解析出的 URL 追加进数组，业务侧拿到的即是可持久化的图片地址列表。

## 基础用法

<Demo title="本地图片读取" description="使用真实 FaImageUpload；文件仅在浏览器中读取为 data URL，不上传到任何服务" :source="srcBasic">
  <ClientOnly><InteractiveMediaImageUpload /></ClientOnly>
</Demo>

交互说明：

- 点击图片格上的悬浮遮罩可 **预览**（内置图片预览，支持多图切换）、**删除**、**前移/后移排序**（数组顺序随 v-model 同步变化，带过渡动画）。
- 上传按钮仅在选择图片时提供 `accept="image/*"` 过滤；悬停或聚焦组件时可 `Ctrl+V` 粘贴剪贴板中的图片（仅 `image/*` 类型，`directory` 模式下不响应粘贴）。
- 上传中按钮上显示进度条，期间禁止再次选择。

## 多图与回显

<Demo title="多图上传、预览与排序" description="选择本地图片后可使用真实悬浮操作栏预览、删除和排序" :source="srcMultiple">
  <ClientOnly><InteractiveMediaImageUpload /></ClientOnly>
</Demo>

## 校验与自定义尺寸

<Demo title="beforeUpload 校验 + 自定义宽高" description="真实组件拒绝非图片或超过 200 KB 的文件" :source="srcValidation">
  <ClientOnly><InteractiveMediaImageUpload /></ClientOnly>
</Demo>

## 自定义上传请求

演示传入的 `httpRequest` 仅通过 `FileReader` 在浏览器内生成 data URL，再把结果传给 `afterUpload` 与 `onSuccess`，不发起 HTTP 请求。

<Demo title="本地 httpRequest" description="选择图片即可查看纯本地 mock 的完整上传交互" :source="srcCustomRequest">
  <ClientOnly><InteractiveMediaImageUpload /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaImageUpload Props" :data="props" />

### Emits

<ApiTable title="FaImageUpload Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 自定义上传按钮内容，替换默认的虚线框 + 上传图标 |

### UploadRequestOptions

`httpRequest` 收到的参数对象（注意：与 FaFileUpload 的 `FileUploadRequestOptions` 字段相同、名称不同）：

```ts
interface UploadRequestOptions {
  action: string                            // props.action
  method: string                            // props.method
  headers: Headers | Record<string, any>    // props.headers
  data: Record<string, any>                 // props.data
  name: string                              // props.name，文件字段名
  file: File                                // 当前待上传文件
  onProgress: (percent: number) => void     // 进度回调，0-100
}
```

## 注意事项

- `max` 默认为 `1`（与 FaFileUpload 默认不限不同）；需要多图必须显式设置 `multiple` / `max`，`max=0` 表示不限制。
- `v-model` 只保存 URL 字符串，不保存 File 或状态；上传失败时 URL 不会入数组（`afterUpload` 返回空也不会入列），需要失败提示请在 `httpRequest` 中自行处理。
- 删除、排序操作直接修改 `v-model` 数组并即时生效，无需额外事件；点击预览走框架内置的图片预览能力。
- 后端返回的图片 ID 若为 Java Long / Snowflake，序列化到 JSON 时必须为 string，前端禁止使用 `Number(id)` 转换；作为 URL 或查询参数拼接时同样按字符串处理。

源码：`yudream-frontend/packages/components/src/basic/image-upload/index.vue`，示例：`yudream-frontend/packages/components/src/basic/image-upload/_examples/`
