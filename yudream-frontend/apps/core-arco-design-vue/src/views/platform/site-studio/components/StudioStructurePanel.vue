<script setup lang="ts">
import type { YdTreeNode } from '@yudream/components'
import type { StudioNode, StudioPageSettings, StudioParticlePreset } from '@/types/site-studio'
import { computed, ref } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import StudioBlockInserter from './StudioBlockInserter.vue'

defineOptions({ name: 'StudioStructurePanel' })

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const regionOptions = [
  { label: '页面', value: 'page' },
  { label: '页头', value: 'header' },
  { label: '页脚', value: 'footer' },
]

const pageFormOpen = ref(false)
const pageFormEditing = ref('')
const pageForm = ref({ name: '', path: '/', particles: '' as StudioParticlePreset | '', toastTitle: '', toastBody: '', toastIcon: '', icon: '', hostRoutes: '', requires: '' })
const particleOptions = [
  { label: '沿用站点默认', value: '' },
  { label: '无', value: 'none' },
  { label: '金色微光', value: 'dust' },
  { label: '飘雪', value: 'snow' },
  { label: '花瓣', value: 'petals' },
  { label: '幽光孢子', value: 'spores' },
  { label: '余烬', value: 'embers' },
  { label: '星屑', value: 'stars' },
]
const imageOptions = computed(() => [{ label: '无图标', value: '' }, ...(store.doc?.assets ?? []).filter(asset => asset.kind === 'image').map(asset => ({ label: asset.name, value: asset.id }))])

const VOID_TAGS = new Set(['br', 'col', 'hr', 'img', 'source', 'track', 'wbr'])

/** 图层树：当前编辑目标的节点集合 → YdTree 数据 */
const layerNodes = computed<YdTreeNode[]>(() => {
  const convert = (nodes: StudioNode[]): YdTreeNode[] => nodes.map(node => ({
    key: node.id,
    label: `${node.text !== undefined && node.text ? node.text : node.type} · ${node.tag}`,
    children: VOID_TAGS.has(node.tag) || node.text !== undefined ? undefined : convert(node.children),
  }))
  return convert(store.editRegion === 'page' ? store.activePage?.root || [] : store.doc?.theme[store.editRegion] || [])
})

const expandedKeys = ref<string[]>([])

/** 图层树选中键与 store.selectedNodeId 的双向收敛 */
const layerSelection = computed<string[]>({
  get: () => (store.selectedNodeId ? [store.selectedNodeId] : []),
  set: (keys) => {
    store.selectedNodeId = keys[0] || ''
  },
})

const selectedLayerLabel = computed(() => {
  const node = store.doc && store.selectedNodeId ? findStudioNode(store.doc, store.selectedNodeId) : undefined
  return node ? `${node.type} · ${node.tag}` : ''
})

function selectPage(pageId: string) {
  store.activePageId = pageId
  store.editRegion = 'page'
  store.selectedNodeId = ''
}

function openPageForm(pageId?: string) {
  const page = pageId ? store.doc?.pages.find(item => item.id === pageId) : null
  pageFormEditing.value = pageId || ''
  pageForm.value = {
    name: page ? page.name : `页面 ${store.doc ? store.doc.pages.length + 1 : 1}`,
    path: page ? page.path : '/',
    particles: page?.settings?.particles ?? '',
    toastTitle: page?.settings?.toast?.title ?? '',
    toastBody: page?.settings?.toast?.body ?? '',
    toastIcon: page?.settings?.toast?.iconAssetId ?? '',
    icon: page?.settings?.iconAssetId ?? '',
    hostRoutes: (page?.settings?.hostRoutes ?? []).join(', '),
    requires: (page?.settings?.requires ?? []).join(', '),
  }
  pageFormOpen.value = true
}

function normalizePagePath(value: string) {
  const trimmed = value.trim()
  if (!trimmed || trimmed === '/') {
    return '/'
  }
  const body = trimmed.replace(/^\/+|\/+$/g, '')
  return /^[\w\-/:]*$/.test(body) ? `/${body}` : ''
}

function submitPageForm() {
  const name = pageForm.value.name.trim()
  const path = normalizePagePath(pageForm.value.path)
  if (!name || !path) {
    toast.error('页面名称与路径不能为空，路径仅支持字母数字、- _ / 与动态段 :参数')
    return
  }
  const form = pageForm.value
  const settings: StudioPageSettings = {
    ...(form.particles ? { particles: form.particles } : {}),
    ...(form.icon ? { iconAssetId: form.icon } : {}),
    ...(form.hostRoutes.trim() ? { hostRoutes: form.hostRoutes.split(/[\s,，]+/).filter(Boolean) } : {}),
    ...(form.requires.trim() ? { requires: form.requires.split(/[\s,，]+/).filter(Boolean) } : {}),
    ...(form.toastTitle.trim() ? { toast: { title: form.toastTitle.trim(), body: form.toastBody.trim(), ...(form.toastIcon ? { iconAssetId: form.toastIcon } : {}) } } : {}),
  }
  const settingsPatch = Object.keys(settings).length ? { settings } : {}
  try {
    if (pageFormEditing.value) {
      const page = store.doc?.pages.find(item => item.id === pageFormEditing.value)
      // 清空设置时整页替换去掉 settings 字段（update-page 为浅合并）
      if (page?.settings && !settingsPatch.settings) {
        store.execute({ type: 'update-page', pageId: pageFormEditing.value, patch: { name, path, settings: {} } })
      }
      else {
        store.execute({ type: 'update-page', pageId: pageFormEditing.value, patch: { name, path, ...settingsPatch } })
      }
    }
    else {
      const id = crypto.randomUUID()
      store.execute({ type: 'add-page', page: { id, name, path, root: [], ...settingsPatch } })
      selectPage(id)
    }
    pageFormOpen.value = false
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '页面保存失败')
  }
}

function confirmRemovePage(pageId: string) {
  modal.confirm({
    title: '删除页面',
    content: '仅从当前站点文档移除该页面；保存草稿后才写回服务端，已发布版本不受影响。',
    confirmButtonText: '删除',
    onConfirm: () => {
      try {
        store.execute({ type: 'remove-page', pageId })
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '页面删除失败')
      }
    },
  })
}
</script>

<template>
  <div class="p-3 flex flex-col gap-3 h-full min-h-0 overflow-auto">
    <section class="flex flex-col gap-2" aria-label="编辑区域">
      <h3 class="text-xs text-muted-foreground font-medium">
        编辑区域
      </h3>
      <YdSegmented v-model="store.editRegion" :options="regionOptions" class="w-full" aria-label="选择页头页脚或页面" />
    </section>

    <section class="flex flex-col gap-2 min-h-0" aria-label="页面列表">
      <div class="flex items-center justify-between">
        <h3 class="text-xs text-muted-foreground font-medium">
          页面
        </h3>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="新建页面" @click="openPageForm()">
          <YdIcon name="morph:plus" />
        </YdButton>
      </div>
      <YdList dense class="max-h-56 overflow-auto">
        <YdListRow v-for="page in store.doc?.pages || []" :key="page.id">
          <div class="studio-page-row w-full">
            <YdAction type="button" class="text-left flex-1 min-w-0" :aria-pressed="store.editRegion === 'page' && store.activePageId === page.id" @click="selectPage(page.id)">
              <span class="min-w-0 truncate">
                {{ page.name }}
                <span class="text-xs text-muted-foreground">{{ page.path }}</span>
              </span>
            </YdAction>
            <span v-if="store.editRegion === 'page' && store.activePageId === page.id" class="flex gap-0.5 items-center">
              <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="页面设置" @click="openPageForm(page.id)">
                <YdIcon name="morph:pencil" />
              </YdButton>
              <YdButton v-if="(store.doc?.pages.length || 0) > 1" v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除页面" @click="confirmRemovePage(page.id)">
                <YdIcon name="morph:trash-2" />
              </YdButton>
            </span>
          </div>
        </YdListRow>
      </YdList>
    </section>

    <section class="flex flex-shrink-0 flex-col gap-2 min-h-40" aria-label="图层">
      <div class="flex items-center justify-between">
        <h3 class="text-xs text-muted-foreground font-medium">
          图层
        </h3>
        <span v-if="selectedLayerLabel" class="text-xs text-muted-foreground truncate">{{ selectedLayerLabel }}</span>
      </div>
      <YdTree
        v-model:selected-keys="layerSelection"
        v-model:expanded-keys="expandedKeys"
        :data="layerNodes"
        selectable
        filterable
        filter-placeholder="搜索图层"
        class="flex-1 min-h-0"
        aria-label="图层树"
      />
    </section>

    <StudioBlockInserter />

    <YdModal
      v-model="pageFormOpen"
      :title="pageFormEditing ? '页面设置' : '新建页面'"
      show-cancel-button
      confirm-button-text="保存"
      @confirm="submitPageForm"
    >
      <label class="text-sm flex flex-col gap-1">
        <span>页面名称</span>
        <YdInput v-model="pageForm.name" placeholder="如：关于我们" />
      </label>
      <label class="text-sm mt-2 flex flex-col gap-1">
        <span>访问路径</span>
        <YdInput v-model="pageForm.path" placeholder="/ 或 /about" />
        <span class="text-xs text-muted-foreground">根路径 / 表示站点首页；子页对应公开站 /site/路径</span>
      </label>
      <label class="text-sm mt-2 flex flex-col gap-1">
        <span>页面图标（站点导航中显示，如快捷栏物品图标）</span>
        <YdSelect v-model="pageForm.icon" :options="imageOptions" aria-label="页面图标" />
      </label>
      <label class="text-sm mt-2 flex flex-col gap-1">
        <span>页面依赖（未启用时页面关闭、导航隐藏）</span>
        <YdInput v-model="pageForm.requires" placeholder="如 plugin:minecraft-server, capability:wiki" aria-label="页面依赖" />
      </label>
      <label class="text-sm mt-2 flex flex-col gap-1">
        <span>承载宿主公开页（如 /wiki、/market、/timeline；逗号分隔）</span>
        <YdInput v-model="pageForm.hostRoutes" placeholder="留空则不承载" aria-label="承载宿主公开页" />
        <span class="text-xs text-muted-foreground">访问这些地址时，宿主页面嵌入本页「宿主页面插槽」位置显示；需在「公开页适配」中开启站点外壳</span>
      </label>
      <label class="text-sm mt-2 flex flex-col gap-1">
        <span>氛围粒子</span>
        <YdSelect v-model="pageForm.particles" :options="particleOptions" aria-label="页面氛围粒子" />
      </label>
      <fieldset class="mt-2 p-0 border-0 flex flex-col gap-1">
        <legend class="text-sm">
          首次进入提示（同一次访问每页只弹一次）
        </legend>
        <YdInput v-model="pageForm.toastTitle" placeholder="标题，如：进度已达成！（留空不提示）" aria-label="提示标题" />
        <YdInput v-model="pageForm.toastBody" placeholder="内容，如：多人游戏 · 找到服务器入口" aria-label="提示内容" />
        <YdSelect v-model="pageForm.toastIcon" :options="imageOptions" aria-label="提示图标" />
      </fieldset>
    </YdModal>
  </div>
</template>

<style scoped>
.studio-page-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 6px;
  border-radius: var(--yd-control-radius);
}
.studio-page-row[aria-pressed='true'] {
  background: oklch(var(--muted));
}
</style>
