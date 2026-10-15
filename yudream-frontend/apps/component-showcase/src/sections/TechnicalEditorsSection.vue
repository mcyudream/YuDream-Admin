<script setup lang="ts">
import type { YdCodeEditorInstance } from '@yudream/components'
import { YdButton, YdCodeEditor, YdForm, YdFormItem, YdMarkdownEditor, YdUiProvider } from '@yudream/components'
import { reactive, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const markdown = ref('# 可编辑 Markdown\n\n输入内容可同步到 **受控模型**。\n\n```ts\nconst editor = "theme aware"\n```')
const readOnlyMarkdown = '# 只读预览\n\n使用同一编辑器的只读模式展示 Markdown。'
const json = ref('{"name":"YuDream","enabled":true}')
const jsonEditor = ref<YdCodeEditorInstance | null>(null)
const codeSample = 'const theme = "yd"\nconsole.log(theme)'
const formModel = reactive({ markdown: markdown.value, json: json.value })
const editorRules = { required: true, message: '请填写内容', trigger: 'blur' as const }
</script>

<template>
  <YdUiProvider>
    <DemoCard title="Markdown 编辑器" description="受控内容、只读预览、主题跟随、错误提示与可注入的图片上传回调。">
      <YdForm :model="formModel">
        <YdFormItem field="markdown" label="Markdown 内容" extra="说明文本应通过 aria-describedby 关联到实际编辑输入。" :rules="editorRules">
          <template #default="{ fieldProps }">
            <YdMarkdownEditor v-bind="fieldProps" v-model="formModel.markdown" aria-label="Markdown 内容编辑器" height="360px" />
          </template>
        </YdFormItem>
      </YdForm>
      <p class="editor-count">
        模型字符数：{{ formModel.markdown.length }}
      </p>
    </DemoCard>
    <DemoCard title="只读与错误状态" description="展示只读预览、主题和可访问的错误关联。">
      <YdMarkdownEditor :model-value="readOnlyMarkdown" readonly error="此内容由发布流程锁定。" aria-label="锁定的 Markdown 内容" height="260px" />
    </DemoCard>
    <DemoCard title="Monaco 代码编辑器" description="JSON、HTML、CSS、JavaScript 语法、补全、只读状态和实例格式化方法。">
      <div class="mb-2 flex justify-end">
        <YdButton size="sm" variant="outline" @click="jsonEditor?.format()">
          格式化 JSON
        </YdButton>
      </div>
      <YdForm :model="formModel">
        <YdFormItem field="json" label="JSON 配置" extra="标签点击应聚焦 Monaco 的可编辑内容。" :rules="editorRules">
          <template #default="{ fieldProps }">
            <YdCodeEditor v-bind="fieldProps" ref="jsonEditor" v-model="formModel.json" language="json" aria-label="JSON 代码编辑器" height="260px" />
          </template>
        </YdFormItem>
      </YdForm>
      <YdCodeEditor :model-value="codeSample" language="javascript" readonly aria-label="只读 JavaScript 编辑器" height="160px" class="mt-3" />
    </DemoCard>
  </YdUiProvider>
</template>

<style scoped>
.editor-count { margin-top: 12px; color: var(--color-text-3); font-size: 12px; }
</style>
