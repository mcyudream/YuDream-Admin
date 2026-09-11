<script setup lang="ts">
import type { PluginModule } from '@/api/modules/platform-plugin'
import type { PluginStorePluginDescriptor, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'
import { compareSemVer } from './semver'
import VersionBody from './version-body.vue'

type VersionOperation = 'install' | 'update' | 'installed' | 'local-newer' | 'unavailable'

const props = withDefaults(defineProps<{
  item: PluginStorePluginVersion
  localModule?: PluginModule
  installingVersion?: string
  updatingVersion?: string
  pending?: boolean
  collapsible?: boolean
}>(), {
  localModule: undefined,
  installingVersion: '',
  updatingVersion: '',
  pending: false,
  collapsible: false,
})

const emit = defineEmits<{
  install: [releaseVersion: string]
  update: [releaseVersion: string]
}>()

const descriptor = computed<PluginStorePluginDescriptor>(() => {
  if (props.item.descriptor) {
    return props.item.descriptor
  }
  return props.item as unknown as PluginStorePluginDescriptor
})

const isInstallable = computed(() => props.item.installable !== false && descriptor.value.installable !== false)
const installDisabledReason = computed(() => props.item.installDisabledReason || descriptor.value.installDisabledReason)

const operation = computed<VersionOperation>(() => {
  if (!props.localModule) {
    return 'install'
  }
  const comparison = compareSemVer(props.item.releaseVersion, props.localModule.version || '')
  if (comparison === undefined) {
    return 'unavailable'
  }
  if (comparison > 0) {
    return 'update'
  }
  return comparison === 0 ? 'installed' : 'local-newer'
})

const operationLabel = computed(() => {
  switch (operation.value) {
    case 'installed': return '已安装'
    case 'local-newer': return '本地版本较新'
    case 'unavailable': return '版本不可比较'
    default: return ''
  }
})

const operationTitle = computed(() => {
  if (operation.value === 'unavailable') {
    return `本地版本 ${props.localModule?.version || '未知'} 或市场版本 ${props.item.releaseVersion} 不符合 SemVer，无法判断更新关系`
  }
  return undefined
})
</script>

<template>
  <article v-if="!collapsible" class="version-card">
    <div class="version-head">
      <div class="version-head-main">
        <div class="version-title">
          <h3>{{ item.releaseVersion }}</h3>
          <FaTag v-if="item.sourceName" variant="secondary" :title="`来源：${item.sourceName}`">{{ item.sourceName }}</FaTag>
          <FaTag v-if="operationLabel" variant="secondary" :title="operationTitle">{{ operationLabel }}</FaTag>
        </div>
        <p class="version-description">
          {{ descriptor.description || '暂无插件简介。' }}
        </p>
      </div>
      <div class="version-actions">
        <FaButton
          v-if="operation === 'install'"
          v-auth="'platform:plugin:manage'"
          size="sm"
          :title="installDisabledReason"
          :loading="installingVersion === item.releaseVersion"
          :disabled="!isInstallable || pending"
          @click="emit('install', item.releaseVersion)"
        >
          安装
        </FaButton>
        <FaButton
          v-else-if="operation === 'update'"
          v-auth="'platform:plugin:manage'"
          size="sm"
          variant="outline"
          :loading="updatingVersion === item.releaseVersion"
          :disabled="pending"
          @click="emit('update', item.releaseVersion)"
        >
          更新
        </FaButton>
      </div>
    </div>
    <VersionBody :item="item" />
  </article>

  <details v-else class="version-card version-card--compact">
    <summary class="version-summary">
      <FaIcon name="i-ri:arrow-right-s-line" class="version-caret" />
      <h3>{{ item.releaseVersion }}</h3>
      <FaTag v-if="item.sourceName" variant="secondary" :title="`来源：${item.sourceName}`">{{ item.sourceName }}</FaTag>
      <FaTag v-if="operationLabel" variant="secondary" :title="operationTitle">{{ operationLabel }}</FaTag>
      <span class="version-actions" @click.stop.prevent>
        <FaButton
          v-if="operation === 'install'"
          v-auth="'platform:plugin:manage'"
          size="sm"
          :title="installDisabledReason"
          :loading="installingVersion === item.releaseVersion"
          :disabled="!isInstallable || pending"
          @click="emit('install', item.releaseVersion)"
        >
          安装
        </FaButton>
        <FaButton
          v-else-if="operation === 'update'"
          v-auth="'platform:plugin:manage'"
          size="sm"
          variant="outline"
          :loading="updatingVersion === item.releaseVersion"
          :disabled="pending"
          @click="emit('update', item.releaseVersion)"
        >
          更新
        </FaButton>
      </span>
    </summary>
    <div class="version-compact-body">
      <VersionBody :item="item" show-description />
    </div>
  </details>
</template>

<style scoped>
.version-card {
  min-width: 0;
}

.version-head,
.version-title,
.version-summary,
.version-actions {
  display: flex;
  align-items: flex-start;
}

.version-head {
  gap: 12px;
  justify-content: space-between;
}

.version-head-main {
  min-width: 0;
}

.version-title {
  gap: 12px;
  align-items: center;
}

.version-title h3,
.version-summary h3 {
  margin: 0;
  color: var(--color-text-1);
  font-size: 15px;
}

.version-description {
  margin: 6px 0;
  color: var(--color-text-3);
  font-size: 13px;
}

.version-actions {
  flex: none;
  gap: 8px;
  align-items: center;
}

.version-card--compact {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.version-summary {
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  cursor: pointer;
  list-style: none;
}

.version-summary::-webkit-details-marker {
  display: none;
}

.version-summary .version-actions {
  margin-left: auto;
}

.version-caret {
  flex: none;
  color: var(--color-text-3);
  transition: transform 0.15s;
}

.version-card--compact[open] > .version-summary .version-caret {
  transform: rotate(90deg);
}

.version-compact-body {
  padding: 0 12px 12px;
}

@media (max-width: 640px) {
  .version-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
