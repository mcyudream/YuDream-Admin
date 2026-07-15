<script setup lang="ts">
import type { AgentConnectionStyle, AgentFlowEdgeView } from './types'

defineProps<{
  edge: AgentFlowEdgeView
  sourceName: string
  targetName: string
}>()

const emit = defineEmits<{
  changeStyle: [style: AgentConnectionStyle]
  changeLabel: [label: string]
  delete: []
  close: []
}>()
</script>

<template>
  <aside class="inspector">
    <header class="inspector-header">
      <div>
        <span>连线设置</span>
        <strong>{{ sourceName }} → {{ targetName }}</strong>
      </div>
      <button type="button" title="关闭连线设置" aria-label="关闭连线设置" @click="emit('close')">
        <FaIcon name="i-ri:close-line" />
      </button>
    </header>

    <div class="inspector-body">
      <section class="form-section">
        <h3>连线样式</h3>
        <p class="section-help">
          用于表达节点间的数据流向，新建连线会沿用画布顶部的默认样式。
        </p>
        <div class="style-options">
          <button type="button" :class="{ active: (edge.data?.connectionStyle || 'arrow') === 'arrow' }" @click="emit('changeStyle', 'arrow')">
            <span class="line-preview arrow" />
            <span><strong>箭头</strong><small>有明确方向的数据流</small></span>
          </button>
          <button type="button" :class="{ active: edge.data?.connectionStyle === 'line' }" @click="emit('changeStyle', 'line')">
            <span class="line-preview" />
            <span><strong>线段</strong><small>不显示终点箭头</small></span>
          </button>
        </div>
      </section>

      <section class="form-section">
        <h3>连线信息</h3>
        <label class="form-field">
          <span>连线标签</span>
          <FaInput :model-value="String(edge.label || '')" class="w-full" maxlength="40" placeholder="例如：条件成立" @update:model-value="emit('changeLabel', String($event || ''))" />
        </label>
        <div class="meta-row">
          <span>起点</span><b>{{ sourceName }}</b>
        </div>
        <div class="meta-row">
          <span>终点</span><b>{{ targetName }}</b>
        </div>
      </section>
    </div>

    <footer class="inspector-footer">
      <FaButton class="w-full" variant="destructive" @click="emit('delete')">
        <FaIcon name="i-ri:delete-bin-line" /> 删除连线
      </FaButton>
    </footer>
  </aside>
</template>

<style scoped>
.inspector { display: flex; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; border-left: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.inspector-header { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 11px 16px; border-bottom: 1px solid var(--color-border-2); }
.inspector-header > div { display: grid; min-width: 0; gap: 3px; }
.inspector-header span { color: var(--color-text-3); font-size: 10px; }
.inspector-header strong { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.inspector-header button { display: grid; width: 28px; height: 28px; place-items: center; border: 0; border-radius: 6px; color: var(--color-text-3); background: var(--color-fill-2); cursor: pointer; }
.inspector-body { min-height: 0; overflow: auto; padding: 0 16px 24px; flex: 1; }
.form-section { padding: 17px 0; border-bottom: 1px solid var(--color-border-2); }
.form-section h3 { margin: 0 0 9px; color: var(--color-text-1); font-size: 12px; }
.section-help { margin: 0 0 13px; color: var(--color-text-3); font-size: 9px; line-height: 1.6; }
.style-options { display: grid; gap: 9px; }
.style-options button { display: grid; min-height: 60px; grid-template-columns: 64px minmax(0, 1fr); align-items: center; gap: 8px; padding: 9px; border: 1px solid var(--color-border-2); border-radius: 6px; color: var(--color-text-2); background: var(--color-bg-1); text-align: left; cursor: pointer; }
.style-options button:hover { border-color: rgb(var(--primary-3)); }
.style-options button.active { border-color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); box-shadow: 0 0 0 2px rgb(var(--primary-2)); }
.style-options button > span:last-child { display: grid; gap: 3px; }
.style-options strong { color: var(--color-text-1); font-size: 11px; }
.style-options small { color: var(--color-text-3); font-size: 9px; }
.line-preview { position: relative; display: block; width: 48px; height: 2px; background: var(--color-text-3); }
.line-preview.arrow::after { position: absolute; top: 50%; right: 0; width: 7px; height: 7px; border-top: 2px solid var(--color-text-3); border-right: 2px solid var(--color-text-3); content: ''; transform: translateY(-50%) rotate(45deg); }
.form-field { display: grid; gap: 7px; margin: 13px 0; color: var(--color-text-2); font-size: 11px; }
.meta-row { display: flex; min-height: 35px; align-items: center; justify-content: space-between; gap: 12px; border-bottom: 1px dashed var(--color-border-2); color: var(--color-text-3); font-size: 10px; }
.meta-row b { max-width: 190px; overflow: hidden; color: var(--color-text-2); font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.inspector-footer { padding: 12px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
</style>
