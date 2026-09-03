<script setup lang="ts">
import type { PluginStorePluginDescriptor, PluginStorePluginVersion } from '@/api/modules/platform-plugin-marketplace'

const props = withDefaults(defineProps<{
  item: PluginStorePluginVersion
  showDescription?: boolean
}>(), {
  showDescription: false,
})

const descriptor = computed<PluginStorePluginDescriptor>(() => {
  if (props.item.descriptor) {
    return props.item.descriptor
  }
  return props.item as unknown as PluginStorePluginDescriptor
})

const isInstallable = computed(() => props.item.installable !== false && descriptor.value.installable !== false)
const installDisabledReason = computed(() => props.item.installDisabledReason || descriptor.value.installDisabledReason)
</script>

<template>
  <div class="version-body">
    <p v-if="showDescription" class="version-description">
      {{ descriptor.description || '暂无插件简介。' }}
    </p>

    <div class="release-notes-panel">
      <div class="section-title">
        发布说明
      </div>
      <p class="release-notes">{{ descriptor.releaseNotes || '该版本暂无发布说明。' }}</p>
    </div>

    <div v-if="descriptor.compatibility && Object.values(descriptor.compatibility).some(Boolean)" class="metadata-panel">
      <div class="section-title">
        兼容性要求
      </div>
      <div class="compatibility-list">
        <FaTag v-if="descriptor.compatibility?.host" variant="secondary">宿主：{{ descriptor.compatibility?.host }}</FaTag>
        <FaTag v-if="descriptor.compatibility?.spi" variant="secondary">SPI：{{ descriptor.compatibility?.spi }}</FaTag>
        <FaTag v-if="descriptor.compatibility?.frontendSdk" variant="secondary">前端 SDK：{{ descriptor.compatibility?.frontendSdk }}</FaTag>
      </div>
    </div>
    <div v-if="descriptor.dependencies?.length" class="metadata-panel">
      <div class="section-title">
        插件依赖
      </div>
      <div class="dependency-list">
        <FaTag v-for="dependency in descriptor.dependencies" :key="dependency.code" variant="secondary">
          {{ dependency.code }}{{ dependency.range ? ` (${dependency.range})` : '' }} · {{ dependency.required === false ? '可选' : '必需' }}
        </FaTag>
      </div>
    </div>
    <div v-if="descriptor.publisher || descriptor.license" class="metadata-panel">
      <div class="section-title">
        发布者与许可证
      </div>
      <div class="metadata-list">
        <template v-if="descriptor.publisher">
          <strong>{{ descriptor.publisher?.name }}</strong>
          <span>{{ descriptor.publisher?.id }}</span>
          <FaTag v-if="descriptor.publisher?.verified" variant="secondary">已验证</FaTag>
          <a v-if="descriptor.publisher?.url" :href="descriptor.publisher?.url" target="_blank" rel="noopener noreferrer" class="metadata-link">
            发布者主页
          </a>
        </template>
        <span v-if="descriptor.license">许可证：{{ descriptor.license }}</span>
      </div>
    </div>
    <div v-if="!isInstallable && installDisabledReason" class="install-blocked-reason">
      {{ installDisabledReason }}
    </div>
    <div v-if="descriptor.screenshots?.length" class="screenshot-list">
      <img v-for="screenshot in descriptor.screenshots" :key="screenshot" :src="screenshot" :alt="`${descriptor.displayName || descriptor.code} 截图`">
    </div>

    <details class="technical-details">
      <summary>技术详情</summary>
      <div class="detail-grid">
        <div><span>插件编码</span><strong>{{ descriptor.code }}</strong></div>
        <div><span>描述符版本</span><strong>{{ descriptor.version }}</strong></div>
        <div><span>入口</span><strong class="break-all">{{ descriptor.main }}</strong></div>
        <div v-if="descriptor.jar"><span>Maven 坐标</span><strong class="break-all">{{ descriptor.jar?.mavenCoordinates }}</strong></div>
        <div v-if="descriptor.jar"><span>JAR 地址</span><strong class="break-all">{{ descriptor.jar?.url }}</strong></div>
        <div v-if="descriptor.jar"><span>SHA-256</span><strong class="break-all">{{ descriptor.jar?.sha256 }}</strong></div>
        <div v-if="descriptor.source?.repository"><span>源码仓库</span><strong class="break-all">{{ descriptor.source?.repository }}</strong></div>
        <div v-if="descriptor.source?.commit"><span>源码提交</span><strong class="break-all">{{ descriptor.source?.commit }}</strong></div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.version-description {
  margin: 0 0 4px;
  color: var(--color-text-3);
  font-size: 13px;
}

.release-notes-panel,
.metadata-panel,
.install-blocked-reason,
.technical-details {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.section-title {
  margin-bottom: 8px;
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 700;
}

.compatibility-list,
.dependency-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.compatibility-list :deep(.fa-tag),
.dependency-list :deep(.fa-tag) {
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}

.metadata-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  color: var(--color-text-2);
  font-size: 13px;
}

.metadata-link {
  max-width: 100%;
  color: rgb(var(--primary-6));
  overflow-wrap: anywhere;
}

.release-notes {
  max-height: 260px;
  margin: 0;
  overflow: auto;
  color: var(--color-text-2);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.install-blocked-reason {
  color: rgb(var(--danger-6));
  font-size: 13px;
}

.screenshot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.screenshot-list img {
  width: min(100%, 260px);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  object-fit: cover;
}

.technical-details {
  color: var(--color-text-2);
}

.technical-details summary {
  cursor: pointer;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 700;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.detail-grid div {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.detail-grid span {
  color: var(--color-text-3);
  font-size: 12px;
}

.detail-grid strong {
  overflow: hidden;
  color: var(--color-text-1);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.break-all {
  white-space: normal !important;
  overflow-wrap: anywhere;
}
</style>
