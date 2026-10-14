<script setup lang="ts">
import { YdButton, YdCombobox } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '../components/DemoCard.vue'

const selected = ref<string | number>()
const options = ref([
  { label: 'OpenAI', value: 'openai' },
  { label: 'Anthropic', value: 'anthropic' },
])
const created = ref<string>()

function createProvider(value: string) {
  options.value = [...options.value, { label: value, value }]
  selected.value = value
  created.value = value
}

function reset() {
  selected.value = undefined
  created.value = undefined
  options.value = [
    { label: 'OpenAI', value: 'openai' },
    { label: 'Anthropic', value: 'anthropic' },
  ]
}
</script>

<template>
  <DemoCard title="创建编码" description="搜索未匹配时可选择使用输入编码；创建事件由业务处理并回填受控值。">
    <div class="creatable-layout">
      <div class="creatable-form">
        <YdCombobox v-model="selected" :options="options" allow-create aria-label="选择或创建供应商编码" placeholder="搜索供应商或输入新编码" @create="createProvider" />
        <div class="creatable-actions">
          <YdButton size="sm" variant="outline" :disabled="!selected && !created" @click="reset">
            重置演示
          </YdButton>
          <span class="creatable-help">输入不存在的编码后选择“使用此编码”。</span>
        </div>
      </div>
      <aside class="creatable-summary" aria-label="创建编码状态">
        <div class="summary-heading">
          <span>受控值</span>
          <YdTag :variant="created ? 'secondary' : 'outline'">
            {{ created ? '已创建' : '候选中' }}
          </YdTag>
        </div>
        <strong class="selected-value">{{ selected || '尚未选择' }}</strong>
        <p class="summary-note">
          候选 {{ options.length }} 项；create 事件只在调用方接受后写回模型。
        </p>
        <p v-if="created" class="created-value" role="status">
          已创建并选择：{{ created }}
        </p>
      </aside>
    </div>
  </DemoCard>
</template>

<style scoped>
.creatable-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(240px, 0.8fr);
  gap: var(--yd-content-gap);
  align-items: start;
}

.creatable-form {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.creatable-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.creatable-help,
.summary-note {
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.creatable-summary {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: var(--yd-surface-padding);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.summary-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-1);
  font-size: 12px;
  font-weight: 600;
}

.selected-value {
  overflow-wrap: anywhere;
  font-size: 18px;
  color: var(--color-text-1);
}

.summary-note,
.created-value {
  margin: 0;
}

.created-value {
  color: var(--color-text-2);
  font-size: 12px;
}

@media (width <= 720px) {
  .creatable-layout {
    grid-template-columns: 1fr;
  }
}
</style>
