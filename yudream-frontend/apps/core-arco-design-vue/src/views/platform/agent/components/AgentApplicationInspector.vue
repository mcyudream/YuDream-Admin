<script setup lang="ts">
import type { AgentApplicationPayload, AgentTool, SystemAgentTool } from '@/api/modules/platform-agent'

/** 与编排页工具模块分组保持同一形状（editor 依据 toolModules 生成）。 */
interface AgentToolPickerTool {
  code: string
  name: string
  description?: string
  permissionCode?: string
  risk?: 'READ' | 'WRITE'
  requiresConfirmation?: boolean
  selectable: boolean
  reason?: string
}

interface AgentToolPickerGroup {
  code: string
  name: string
  description?: string
  source?: string
  lazy: boolean
  closed: boolean
  closedReason?: string
  stateText: string
  tools: AgentToolPickerTool[]
}

const props = defineProps<{
  form: AgentApplicationPayload
  toolCodes: string[]
  systemTools: SystemAgentTool[]
  customTools: AgentTool[]
  toolGroups: AgentToolPickerGroup[]
  codeDisabled?: boolean
}>()

const emit = defineEmits<{
  updateField: [key: keyof AgentApplicationPayload, value: unknown]
}>()

const sourceLabels: Record<string, string> = { system: '系统', plugin: '插件', mcp: 'MCP', python: 'Python' }

/** 应用授权摘要按工具模块汇总：模块名 + 数量 + 常驻/按需/关闭状态。 */
const authorizedModules = computed(() => {
  const selected = new Set(props.toolCodes)
  const groups = props.toolGroups
    .map(group => ({ ...group, tools: group.tools.filter(tool => selected.has(tool.code)) }))
    .filter(group => group.tools.length)
  const covered = new Set(groups.flatMap(group => group.tools.map(tool => tool.code)))
  const missing = props.toolCodes
    .filter(code => !covered.has(code))
    .map(code => ({
      code,
      name: props.systemTools.find(tool => tool.code === code)?.name
        || props.customTools.find(tool => tool.code === code)?.name
        || code,
    }))
  return { groups, missing }
})

const authorizedCount = computed(() => props.toolCodes.length)
const moduleCount = computed(() => authorizedModules.value.groups.length)
/** 当前不可用的授权：模块关闭、工具停用或已不在候选列表中。 */
const unavailableCount = computed(() => authorizedModules.value.groups
  .reduce((total, group) => total + group.tools.filter(tool => !tool.selectable).length, 0)
  + authorizedModules.value.missing.length)

function groupSourceText(group: AgentToolPickerGroup) {
  return group.source ? sourceLabels[group.source] ?? group.source : '系统'
}
</script>

<template>
  <aside class="inspector">
    <header class="inspector-header">
      <div>
        <span>应用设置</span>
        <strong>{{ form.name || '未命名 Agent 应用' }}</strong>
      </div>
      <YdIcon name="morph:settings" />
    </header>

    <div class="inspector-body">
      <section class="form-section">
        <h3>基础信息</h3>
        <label class="form-field required">
          <span>应用名称</span>
          <YdInput :model-value="form.name" class="w-full" maxlength="60" placeholder="例如：运营内容 Agent" @update:model-value="emit('updateField', 'name', $event)" />
          <small>显示在 Agent 应用列表和调用入口中</small>
        </label>
        <label class="form-field required">
          <span>应用编码</span>
          <YdInput :model-value="form.code" class="w-full" :disabled="codeDisabled" maxlength="60" placeholder="例如：content-agent" @update:model-value="emit('updateField', 'code', $event)" />
          <small>仅支持稳定的英文业务标识，创建后不可修改</small>
        </label>
        <label class="form-field">
          <span>应用描述</span>
          <YdTextarea :model-value="form.description" class="w-full" :autosize="{ minRows: 3, maxRows: 6 }" maxlength="200" placeholder="说明这个 Agent 解决的问题和使用范围" @update:model-value="emit('updateField', 'description', $event)" />
        </label>
      </section>

      <section class="form-section">
        <h3>Agent 指令</h3>
        <label class="form-field">
          <span>系统提示词</span>
          <YdTextarea :model-value="form.systemPrompt" class="w-full" input-class="font-mono" :autosize="{ minRows: 7, maxRows: 14 }" placeholder="定义 Agent 的角色、任务边界、处理步骤和输出格式" @update:model-value="emit('updateField', 'systemPrompt', $event)" />
          <small>工作流节点指令会在此基础上补充具体任务</small>
        </label>
      </section>

      <section class="form-section">
        <div class="section-title">
          <h3>应用授权摘要</h3>
          <span>{{ authorizedCount }} 项派生授权 · {{ moduleCount }} 个模块</span>
        </div>
        <p class="summary-copy">
          授权范围由画布中各模型节点选择的工具自动汇总，不能在应用级单独编辑；尚未定义节点工具的历史应用会暂时保留原授权。
        </p>
        <p v-if="unavailableCount" class="summary-warning">
          <YdIcon name="morph:triangle-alert" />
          <span>{{ unavailableCount }} 项授权当前不可用（模块关闭、工具停用或已下线），发布后运行时可能无法调用。</span>
        </p>

        <div v-if="authorizedModules.groups.length" class="module-summary" aria-label="模型节点派生的工具授权">
          <div
            v-for="group in authorizedModules.groups"
            :key="group.code"
            class="module-summary__group"
            :class="{ 'is-closed': group.closed }"
          >
            <div class="module-summary__head">
              <strong>{{ group.name }}</strong>
              <YdTag :variant="group.closed ? 'destructive' : group.lazy ? 'outline' : 'secondary'">
                {{ group.stateText }}
              </YdTag>
              <YdTag variant="outline">
                {{ groupSourceText(group) }}
              </YdTag>
              <span class="module-summary__count">{{ group.tools.length }} 项</span>
            </div>
            <p v-if="group.closed" class="module-summary__reason">
              {{ group.closedReason || '模块已关闭' }}，其中的工具在运行时不可用。
            </p>
            <ul class="module-summary__tools">
              <li v-for="tool in group.tools" :key="tool.code">
                <span class="tool-line">
                  <b>{{ tool.name }}</b>
                  <YdTag v-if="tool.risk === 'READ'" variant="secondary">只读</YdTag>
                  <YdTag v-else-if="tool.risk === 'WRITE'" :variant="tool.requiresConfirmation ? 'destructive' : 'outline'">
                    {{ tool.requiresConfirmation ? '写入 · 需确认' : '写入' }}
                  </YdTag>
                </span>
                <small>{{ tool.code }}</small>
                <small v-if="!tool.selectable" class="tool-line__reason">{{ tool.reason }}</small>
              </li>
            </ul>
          </div>
        </div>

        <div v-if="authorizedModules.missing.length" class="module-summary__group is-closed">
          <div class="module-summary__head">
            <strong>未识别的授权</strong>
            <YdTag variant="destructive">
              不可用
            </YdTag>
            <span class="module-summary__count">{{ authorizedModules.missing.length }} 项</span>
          </div>
          <p class="module-summary__reason">
            这些工具已不在候选列表中，运行时无法解析；请在对应模型节点重新选择。
          </p>
          <ul class="module-summary__tools">
            <li v-for="tool in authorizedModules.missing" :key="tool.code">
              <span class="tool-line"><b>{{ tool.name }}</b></span>
              <small>{{ tool.code }}</small>
            </li>
          </ul>
        </div>

        <p v-if="!authorizedCount" class="empty-copy">
          尚未有模型节点授权工具；在文本生成、结构化提取、意图分类或视觉理解节点中配置。
        </p>
      </section>
    </div>
  </aside>
</template>

<style scoped>
.inspector { display: flex; width: 100%; height: 100%; min-width: 0; min-height: 0; overflow: hidden; flex-direction: column; border-left: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.inspector-header { display: flex; min-height: 66px; align-items: center; justify-content: space-between; padding: 11px 16px; border-bottom: 1px solid var(--color-border-2); }
.inspector-header div { display: grid; min-width: 0; gap: 3px; }
.inspector-header span { color: var(--color-text-3); font-size: 10px; }
.inspector-header strong { overflow: hidden; color: var(--color-text-1); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.inspector-header > :last-child { color: var(--color-text-3); font-size: 18px; }
.inspector-body { min-height: 0; overflow: auto; padding: 0 16px 24px; flex: 1; }
.form-section { padding: 17px 0; border-bottom: 1px solid var(--color-border-2); }
.form-section:last-child { border-bottom: 0; }
.form-section h3 { margin: 0 0 13px; color: var(--color-text-1); font-size: 12px; }
.form-field { display: grid; gap: 7px; margin-top: 13px; color: var(--color-text-2); font-size: 11px; }
.form-field.required > span::after { margin-left: 3px; color: rgb(var(--danger-6)); content: '*'; }
.form-field small { color: var(--color-text-3); font-size: 9px; line-height: 1.5; }
.section-title { display: flex; align-items: center; justify-content: space-between; }
.section-title h3 { margin-bottom: 0; }
.section-title span { color: var(--color-text-2); font-size: 10px; }
.summary-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.6; }
.summary-warning { display: flex; align-items: flex-start; gap: 5px; margin: 8px 0 0; color: rgb(var(--warning-6)); font-size: 10px; line-height: 1.55; }
.summary-warning > :first-child { flex: none; margin-top: 2px; }
.module-summary { display: grid; gap: 8px; margin-top: 12px; }
.module-summary__group { display: grid; gap: 7px; padding: 9px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; }
.module-summary__group.is-closed { background: color-mix(in oklab, var(--color-fill-1) 55%, var(--color-bg-1)); }
.module-summary__head { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.module-summary__head strong { color: var(--color-text-1); font-size: 11px; font-weight: 600; }
.module-summary__count { margin-left: auto; color: var(--color-text-3); font-size: 10px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.module-summary__reason { margin: 0; color: rgb(var(--warning-6)); font-size: 9px; line-height: 1.5; }
.module-summary__tools { display: grid; gap: 7px; margin: 0; padding: 0; list-style: none; }
.module-summary__tools li { display: grid; min-width: 0; gap: 2px; }
.module-summary__tools li + li { padding-top: 7px; border-top: 1px solid var(--color-border-2); }
.tool-line { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.tool-line b { overflow: hidden; color: var(--color-text-1); font-size: 11px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.module-summary__tools small { overflow: hidden; color: var(--color-text-3); font-size: 9px; text-overflow: ellipsis; white-space: nowrap; }
.module-summary__tools small.tool-line__reason { color: rgb(var(--warning-6)); white-space: normal; }
.empty-copy { margin: 0; color: var(--color-text-3); font-size: 10px; line-height: 1.5; }
</style>
