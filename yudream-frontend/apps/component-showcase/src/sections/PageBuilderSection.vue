<script setup lang="ts">
import type { YdPageBuilderEditorFacade, YdPageBuilderSnapshot } from '@yudream/components'
import { YdPageBuilder } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const htmlContent = ref('<main><h1>开始构建页面</h1><p>拖入组件后，内容会同步到受控模型。</p></main>')
const cssContent = ref('main { max-width: 720px; margin: 48px auto; padding: 24px; font-family: sans-serif; } h1 { margin-bottom: 12px; }')
const builderProjectJson = ref('')
const latestSnapshot = ref<YdPageBuilderSnapshot | null>(null)
const status = ref('等待构建器初始化')
const readOnly = ref(false)
const disabled = ref(false)

function onReady(_editor: YdPageBuilderEditorFacade) {
  status.value = '构建器已就绪'
}

function onChange(snapshot: YdPageBuilderSnapshot) {
  latestSnapshot.value = snapshot
  status.value = `已同步项目 · ${snapshot.htmlContent.length} 字符 HTML`
}

function resetContent() {
  htmlContent.value = '<main><h1>开始构建页面</h1><p>拖入组件后，内容会同步到受控模型。</p></main>'
  cssContent.value = 'main { max-width: 720px; margin: 48px auto; padding: 24px; font-family: sans-serif; } h1 { margin-bottom: 12px; }'
  builderProjectJson.value = ''
}
</script>

<template>
  <DemoCard title="可视化页面构建器" description="GrapesJS 由 YD 技术适配器异步创建和销毁；HTML、CSS 与项目 JSON 可受控同步，业务上传协议由调用方注入。">
    <div class="page-builder-demo__toolbar">
      <YdButton type="button" variant="outline" @click="resetContent">
        重置示例内容
      </YdButton>
      <YdButton variant="outline" :aria-pressed="readOnly" @click="readOnly = !readOnly">{{ readOnly ? '恢复编辑' : '只读构建器' }}</YdButton>
      <YdButton variant="outline" :aria-pressed="disabled" @click="disabled = !disabled">{{ disabled ? '启用构建器' : '禁用构建器' }}</YdButton>
      <span role="status">{{ status }}</span>
    </div>
    <div class="page-builder-demo__canvas">
      <YdPageBuilder
        v-model:html-content="htmlContent"
        v-model:css-content="cssContent"
        v-model:builder-project-json="builderProjectJson"
        height="560px"
        :read-only="readOnly"
        :disabled="disabled"
        aria-label="可视化页面构建器示例"
        @ready="onReady"
        @change="onChange"
        @error="status = `构建器错误：${$event.message}`"
      />
    </div>
    <p class="page-builder-demo__meta" role="status">
      当前模型：HTML {{ htmlContent.length }} 字符 · CSS {{ cssContent.length }} 字符 · 项目数据 {{ builderProjectJson.length }} 字符
    </p>
  </DemoCard>
</template>

<style scoped>
.page-builder-demo__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.page-builder-demo__toolbar span,
.page-builder-demo__meta {
  color: var(--color-text-3);
  font-size: 12px;
}

.page-builder-demo__canvas {
  min-height: 320px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.page-builder-demo__meta {
  margin: 10px 0 0;
}
</style>
