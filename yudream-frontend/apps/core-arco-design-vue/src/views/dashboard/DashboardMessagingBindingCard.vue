<script setup lang="ts">
import type { MessagingBindingTarget } from '@/api/modules/profile'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { useNow } from '@vueuse/core'
import { useYdToast, YdAlert, YdButton, YdCopyButton, YdDrawer, YdEmpty, YdIcon, YdLoading, YdResult, YdTag, YdTooltip } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'
import apiProfile from '@/api/modules/profile'
import { bindingCodeExpired, bindingTargetLabel, useMessagingBinding } from '@/composables/use-messaging-binding'
import DashboardMessagingBindingTarget from './DashboardMessagingBindingTarget.vue'

defineProps<{ card: DashboardCard }>()

const toast = useYdToast()
const drawerVisible = ref(false)
const { targets, loading, loadError, issuingConnectionId, issueError, bindingCode, boundCount, load, issue } = useMessagingBinding(apiProfile)
const now = useNow({ interval: 1000 })
const featuredTargets = computed(() => [...targets.value].sort((a, b) => Number(a.bound) - Number(b.bound)).slice(0, 2))
const expired = computed(() => bindingCode.value ? bindingCodeExpired(bindingCode.value, now.value.getTime()) : false)
const command = computed(() => bindingCode.value ? `/绑定 ${bindingCode.value.code}` : '')
const expiresAtText = computed(() => {
  const value = bindingCode.value?.expiresAt
  return value && Number.isFinite(Date.parse(value)) ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '未知，请重新生成'
})
const busy = computed(() => loading.value || Boolean(issuingConnectionId.value))

onMounted(load)

function generate(target: MessagingBindingTarget) {
  drawerVisible.value = true
  issue(target)
}

function openConnections() {
  drawerVisible.value = true
  load()
}
</script>

<template>
  <div class="dashboard-card__content dashboard-messaging-binding">
    <YdLoading v-if="loading && !targets.length" class="binding-state" text="正在加载机器人连接…" />
    <YdResult v-else-if="loadError && !targets.length" class="binding-state" status="error" :illustration="false" title="连接加载失败" :description="loadError">
      <YdButton size="sm" variant="outline" :loading="loading" @click="load">
        重新加载
      </YdButton>
    </YdResult>
    <YdEmpty v-else-if="!targets.length" class="binding-state" icon="morph:bot" title="暂无可绑定的机器人" description="请联系管理员启用机器人连接。">
      <YdButton size="sm" variant="outline" @click="load">
        重新检查
      </YdButton>
    </YdEmpty>
    <template v-else>
      <div class="binding-summary" role="status">
        <span>已绑定 <strong>{{ boundCount }}</strong> / {{ targets.length }} 个连接</span>
        <div class="binding-summary__actions">
          <YdTooltip v-if="loadError" :text="loadError">
            <YdTag variant="destructive">
              刷新失败
            </YdTag>
          </YdTooltip>
          <YdButton size="icon-sm" variant="ghost" :loading="loading" :disabled="busy" aria-label="刷新消息绑定状态" @click="load">
            <YdIcon name="morph:refresh-cw" />
          </YdButton>
        </div>
      </div>
      <p class="binding-hint">
        官方身份写入 OpenID，不会覆盖 QQ 号。
      </p>
      <div class="binding-preview">
        <DashboardMessagingBindingTarget v-for="target in featuredTargets" :key="target.connectionId" :target="target" :loading="issuingConnectionId === target.connectionId" :disabled="busy" @issue="generate" />
      </div>
      <div class="binding-footer">
        <YdButton class="w-full" size="sm" variant="outline" @click="openConnections">
          查看全部连接（{{ targets.length }}）
        </YdButton>
      </div>
    </template>

    <YdDrawer v-model="drawerVisible" title="消息协议绑定" :footer="false" content-class="w-full sm:max-w-[520px]">
      <div class="flex flex-col gap-4">
        <p class="text-sm text-muted-foreground">
          每个已启用的机器人连接都有独立绑定入口。选择连接并生成绑定码，再向该连接对应的机器人发送完整绑定命令。
        </p>
        <YdAlert icon="morph:shield-check" description="官方身份写入 OpenID，不会覆盖 QQ 号。绑定码仅供本人使用，请勿转发给他人。" />
        <YdLoading v-if="issuingConnectionId" text="正在生成绑定码…" />
        <YdAlert v-if="issueError" variant="destructive" title="绑定码生成失败" :description="issueError" />
        <section v-if="bindingCode" class="binding-command" aria-label="当前绑定命令" aria-live="polite">
          <strong class="text-sm break-words">发送给「{{ bindingTargetLabel(bindingCode) }}」</strong>
          <span v-if="bindingCode.connectionName && bindingCode.connectionName !== bindingTargetLabel(bindingCode)" class="text-xs text-muted-foreground break-words">连接：{{ bindingCode.connectionName }}</span>
          <code class="text-lg font-semibold break-all">{{ command }}</code>
          <p class="text-xs text-muted-foreground">
            有效至 {{ expiresAtText }}
          </p>
          <YdAlert v-if="expired" variant="destructive" title="绑定码已过期" description="请在对应连接下重新生成绑定码。" />
          <div class="flex flex-wrap gap-2">
            <YdCopyButton class="min-h-8" :text="command" label="复制绑定命令" :disabled="expired" @error="toast.error('复制失败，请手动复制绑定命令')" />
            <YdButton size="sm" variant="outline" :loading="loading" :disabled="busy" @click="load">
              我已发送，刷新状态
            </YdButton>
          </div>
          <p class="text-xs text-muted-foreground">
            生成或复制命令不代表绑定完成，请以机器人回复和刷新后的状态为准。重新生成后请使用最新绑定码。
          </p>
        </section>
        <div class="flex flex-wrap gap-2 items-center justify-between">
          <strong class="text-sm" role="status">机器人连接 · 已绑定 {{ boundCount }} / {{ targets.length }}</strong>
          <YdButton size="sm" variant="ghost" :loading="loading" :disabled="busy" @click="load">
            <YdIcon name="morph:refresh-cw" />刷新状态
          </YdButton>
        </div>
        <YdAlert v-if="loadError" variant="destructive" title="状态刷新失败" :description="loadError" />
        <YdLoading v-if="loading && !targets.length" text="正在加载机器人连接…" />
        <YdEmpty v-else-if="!targets.length && !loadError" title="暂无可绑定的机器人" description="请联系管理员启用机器人连接。" />
        <div v-else class="min-w-0" :aria-busy="loading">
          <DashboardMessagingBindingTarget v-for="target in targets" :key="target.connectionId" :target="target" :loading="issuingConnectionId === target.connectionId" :disabled="busy" @issue="generate" />
        </div>
      </div>
    </YdDrawer>
  </div>
</template>

<style scoped>
.dashboard-messaging-binding {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.binding-summary {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--color-text-2);
}

.binding-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.binding-summary__actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.binding-preview {
  min-width: 0;
}

.binding-footer {
  margin-top: auto;
}

.binding-command {
  display: grid;
  gap: 10px;
  padding: 16px;
  color: var(--color-text-1);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius);
}

.binding-state {
  margin: auto 0;
}

.binding-state :deep(.yd-empty) {
  gap: 8px;
  padding: 0;
}

.binding-state.yd-empty {
  gap: 8px;
  padding: 0;
}

@container dashboard-card (height < 420px) {
  .binding-preview :deep(.binding-target) {
    gap: 6px;
    padding: 8px 0;
  }
}

@container dashboard-card (height < 380px) {
  .binding-preview :deep(.binding-target + .binding-target) {
    display: none;
  }
}
</style>
