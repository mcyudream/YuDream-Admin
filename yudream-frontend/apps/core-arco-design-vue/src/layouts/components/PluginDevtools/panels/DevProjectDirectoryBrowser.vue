<script setup lang="ts">
import type { PluginDevDirectoryBrowse } from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'

// 嵌套在登记表单 FaModal（z-index 2200）之上，需用更高层级 2300
const emit = defineEmits<{
  select: [path: string, inferredCode?: string]
}>()
const open = defineModel<boolean>({ required: true })

const loading = ref(false)
const browse = ref<PluginDevDirectoryBrowse | null>(null)
const pathInput = ref('')

watch(open, (value) => {
  if (value) {
    navigate('')
  }
})

async function navigate(path: string) {
  loading.value = true
  try {
    const res = await apiDevtools.browseDevDirectories(path || undefined)
    browse.value = res.data
    pathInput.value = res.data.path
  }
  catch {
    // 拦截器已提示
  }
  finally {
    loading.value = false
  }
}

function go() {
  navigate(pathInput.value.trim())
}

function choose() {
  if (!browse.value?.path) {
    return
  }
  emit('select', browse.value.path, browse.value.inferredCode || undefined)
  open.value = false
}
</script>

<template>
  <FaModal v-model="open" title="选择插件源码目录" :footer="false" :z-index="2300" content-class="sm:max-w-xl">
    <div class="browser">
      <div class="browser__bar">
        <FaTooltip text="返回盘符列表" side="bottom">
          <FaButton variant="outline" size="icon" :disabled="loading" @click="navigate('')">
            <FaIcon name="i-ri:computer-line" />
          </FaButton>
        </FaTooltip>
        <FaTooltip text="上一级" side="bottom">
          <FaButton variant="outline" size="icon" :disabled="loading || !browse?.parent" @click="browse?.parent && navigate(browse.parent)">
            <FaIcon name="i-ri:arrow-up-line" />
          </FaButton>
        </FaTooltip>
        <FaInput v-model="pathInput" class="flex-1" placeholder="粘贴宿主机绝对路径后回车" @keydown.enter="go" />
        <FaButton variant="outline" size="icon" :loading="loading" title="刷新" @click="go">
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
      </div>

      <div class="browser__list">
        <button
          v-for="entry in browse?.entries || []"
          :key="entry.path"
          type="button"
          class="browser-entry"
          @click="navigate(entry.path)"
        >
          <FaIcon :name="browse?.rootList ? 'i-ri:hard-drive-3-line' : 'i-ri:folder-3-line'" class="browser-entry__icon" />
          <span class="browser-entry__name">{{ entry.name }}</span>
          <FaTag v-if="entry.hasPluginYml" class="text-xs">
            插件模块{{ entry.inferredCode ? ` · ${entry.inferredCode}` : '' }}
          </FaTag>
          <FaTag v-else-if="entry.hasPom" variant="secondary" class="text-xs">
            Maven
          </FaTag>
        </button>
        <div v-if="!loading && browse && !browse.entries.length" class="browser__empty">
          {{ browse.rootList ? '未检测到可用盘符' : '此目录下没有可进入的子目录' }}
        </div>
        <div v-if="loading && !browse" class="browser__empty">
          正在读取目录…
        </div>
      </div>

      <div v-if="browse && !browse.rootList" class="browser__current">
        <span class="browser__current-label">当前目录</span>
        <span class="browser__current-path">{{ browse.path }}</span>
        <FaTag v-if="browse.hasPluginYml" class="text-xs">
          插件模块{{ browse.inferredCode ? ` · ${browse.inferredCode}` : '' }}
        </FaTag>
        <FaTag v-else-if="browse.hasPom" variant="secondary" class="text-xs">
          Maven
        </FaTag>
      </div>

      <div class="browser__actions">
        <FaButton variant="outline" @click="open = false">
          取消
        </FaButton>
        <FaButton :disabled="!browse?.path" @click="choose">
          选择当前目录
        </FaButton>
      </div>
    </div>
  </FaModal>
</template>

<style scoped>
.browser {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.browser__bar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.browser__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  height: 280px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.browser-entry {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px 8px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--color-text-2);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.browser-entry:hover {
  background: var(--color-fill-2);
}

.browser-entry__icon {
  flex-shrink: 0;
  color: var(--color-text-3);
}

.browser-entry__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.browser__empty {
  padding: 32px 12px;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}

.browser__current {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.browser__current-label {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.browser__current-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-1);
  font-size: 12px;
  font-family: monospace;
}

.browser__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}
</style>
