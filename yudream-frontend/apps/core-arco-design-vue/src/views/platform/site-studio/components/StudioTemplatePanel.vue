<script setup lang="ts">
// 用户区块模板（站点共享，服务端保存）：列表、插入、把选中元素保存为模板、编辑与删除；
// 模板同时注册进区块注册表，可在“区块”搜索与分类中直接找到。旧的本机片段可一键迁移为模板。
import type { StudioBlockTemplate } from '@/api/modules/platform-cms-block-templates'
import type { StudioBlockDefinition } from '@/utils/site-studio-blocks'
import { computed, onMounted, ref, watch } from 'vue'
import apiTemplates from '@/api/modules/platform-cms-block-templates'
import { useAppAuth } from '@/composables/app/auth'
import { useAppAccountStore } from '@/store/modules/app/account'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import { STUDIO_BLOCK_CATEGORIES, studioBlockRegistry, syncStudioTemplateBlocks, templateToStudioBlock } from '@/utils/site-studio-blocks'
import { freshStudioNodeTree, readStudioSnippets } from '@/utils/site-studio-snippets'

defineOptions({ name: 'StudioTemplatePanel' })

const props = defineProps<{
  query: string
  insertBlock: (block: StudioBlockDefinition) => void
}>()
/** 注册表不是响应式对象：模板同步进注册表后通知插入器刷新区块列表 */
const emit = defineEmits<{ synced: [] }>()

const store = useSiteStudioStore()
const account = useAppAccountStore()
const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

const canView = computed(() => auth('platform:cms:block-template:view'))
const canCreate = computed(() => auth('platform:cms:block-template:create'))
const canEdit = computed(() => auth('platform:cms:block-template:edit'))
const canDelete = computed(() => auth('platform:cms:block-template:delete'))

const templates = ref<StudioBlockTemplate[]>([])
const loading = ref(false)
const loadError = ref('')
const categoryOptions = STUDIO_BLOCK_CATEGORIES.map(category => ({ label: category.label, value: category.key }))
const categoryLabel = (key: string) => STUDIO_BLOCK_CATEGORIES.find(category => category.key === key)?.label ?? '我的模板'

const filtered = computed(() => {
  const term = props.query.trim().toLowerCase()
  return term
    ? templates.value.filter(template => [template.name, template.description, ...template.keywords].some(text => text.toLowerCase().includes(term)))
    : templates.value
})

async function load() {
  if (!canView.value) {
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    templates.value = (await apiTemplates.list()).data || []
    syncStudioTemplateBlocks(studioBlockRegistry, templates.value)
    emit('synced')
  }
  catch (error) {
    loadError.value = error instanceof Error ? error.message : '区块模板加载失败'
  }
  finally {
    loading.value = false
  }
}
onMounted(load)

// ===== 新建/编辑 =====
const formOpen = ref(false)
const editing = ref<StudioBlockTemplate | null>(null)
const form = ref({ name: '', category: 'custom', description: '', keywords: '' })
const saving = ref(false)
const selectedNode = computed(() => store.doc && store.selectedNodeId ? findStudioNode(store.doc, store.selectedNodeId) : undefined)

function openCreate() {
  if (!selectedNode.value) {
    toast.warning('先在画布或图层中选中要保存的元素')
    return
  }
  editing.value = null
  form.value = { name: '', category: 'custom', description: '', keywords: '' }
  formOpen.value = true
}

function openEdit(template: StudioBlockTemplate) {
  editing.value = template
  form.value = { name: template.name, category: template.category, description: template.description, keywords: template.keywords.join('，') }
  formOpen.value = true
}

function keywordsOf(text: string) {
  return text.split(/[,，\s]+/).map(word => word.trim()).filter(Boolean).slice(0, 10)
}

async function submit() {
  const name = form.value.name.trim()
  if (!name) {
    toast.warning('请填写模板名称')
    return
  }
  saving.value = true
  try {
    const payload = { name, category: form.value.category, description: form.value.description.trim(), keywords: keywordsOf(form.value.keywords) }
    if (editing.value) {
      await apiTemplates.update(editing.value.id, { ...payload, expectedRevision: editing.value.revision })
      toast.success('模板已更新')
    }
    else {
      const node = selectedNode.value
      if (!node) {
        throw new Error('选中元素已不存在，请重新选择')
      }
      await apiTemplates.create({ ...payload, nodesJson: JSON.stringify([freshStudioNodeTree(node)]) })
      toast.success('已保存为站点区块模板，可在任意页面插入')
    }
    formOpen.value = false
    await load()
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '模板保存失败')
  }
  finally {
    saving.value = false
  }
}

function confirmDelete(template: StudioBlockTemplate) {
  modal.confirm({
    title: '删除区块模板',
    content: `「${template.name}」将从站点模板库删除；已插入页面的内容不受影响。`,
    confirmButtonText: '删除',
    onConfirm: async () => {
      try {
        await apiTemplates.remove(template.id)
        toast.success('模板已删除')
        await load()
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '模板删除失败')
      }
    },
  })
}

// ===== 旧本机片段迁移（只新增模板，不删除本机数据） =====
const userKey = computed(() => String(account.userId || account.account || 'anonymous'))
const localSnippets = ref(readStudioSnippets(window.localStorage, userKey.value))
watch(userKey, (key) => {
  localSnippets.value = readStudioSnippets(window.localStorage, key)
})
const migrating = ref(false)
async function migrateLocal() {
  migrating.value = true
  let migrated = 0
  try {
    for (const snippet of localSnippets.value) {
      await apiTemplates.create({ name: snippet.name.slice(0, 60), category: 'custom', nodesJson: JSON.stringify(snippet.nodes.map(freshStudioNodeTree)) })
      migrated++
    }
    toast.success(`已迁移 ${migrated} 个本机片段为站点模板；本机数据保留未删除`)
  }
  catch (error) {
    toast.error(`${migrated ? `已迁移 ${migrated} 个，` : ''}${error instanceof Error ? error.message : '迁移失败'}`)
  }
  finally {
    migrating.value = false
    await load()
  }
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="区块模板">
    <p v-if="!canView" class="text-xs text-muted-foreground m-0">
      没有查看区块模板的权限。
    </p>
    <template v-else>
      <div class="flex gap-2 items-center justify-between">
        <span class="text-xs text-muted-foreground">站点共享 · {{ templates.length }} 个</span>
        <YdButton v-if="canCreate" size="sm" variant="outline" :disabled="!selectedNode" aria-label="把选中元素保存为模板" @click="openCreate">
          <YdIcon name="morph:bookmark-plus" /> 保存选中
        </YdButton>
      </div>
      <YdAlert v-if="loadError" variant="destructive" :title="loadError" />
      <YdLoading v-else-if="loading && !templates.length" />
      <YdEmpty v-else-if="!filtered.length" :description="query.trim() ? '没有匹配的模板' : '还没有模板：选中画布中的元素后点“保存选中”'" />
      <ul v-else class="studio-template-list">
        <li v-for="template in filtered" :key="template.id" class="studio-template-item">
          <YdAction v-auth="'platform:cms:edit'" type="button" class="studio-template-insert" :aria-label="`插入模板${template.name}`" @click="insertBlock(templateToStudioBlock(template))">
            <YdIcon name="morph:layout-template" class="shrink-0 size-4" />
            <span class="min-w-0">
              <strong class="studio-template-name">{{ template.name }}</strong>
              <small class="text-muted-foreground">{{ categoryLabel(template.category) }}{{ template.description ? ` · ${template.description}` : '' }}</small>
            </span>
          </YdAction>
          <YdButton v-if="canEdit" variant="ghost" size="icon-sm" :aria-label="`编辑模板${template.name}`" @click="openEdit(template)">
            <YdIcon name="morph:pencil" />
          </YdButton>
          <YdButton v-if="canDelete" variant="ghost" size="icon-sm" :aria-label="`删除模板${template.name}`" @click="confirmDelete(template)">
            <YdIcon name="morph:trash-2" />
          </YdButton>
        </li>
      </ul>
      <div v-if="canCreate && localSnippets.length" class="text-xs text-muted-foreground flex flex-wrap gap-2 items-center">
        <span>此浏览器还有 {{ localSnippets.length }} 个旧版本机片段</span>
        <YdButton size="sm" variant="ghost" :loading="migrating" @click="migrateLocal">
          迁移为站点模板
        </YdButton>
      </div>
    </template>

    <YdModal
      v-model="formOpen"
      :title="editing ? '编辑区块模板' : '保存选中元素为模板'"
      show-cancel-button
      :confirm-button-text="editing ? '保存' : '创建模板'"
      :confirm-button-loading="saving"
      @confirm="submit"
    >
      <div class="flex flex-col gap-3">
        <label class="text-sm flex flex-col gap-1">
          <span>名称</span>
          <YdInput v-model="form.name" :maxlength="60" placeholder="如：产品亮点三栏" />
        </label>
        <label class="text-sm flex flex-col gap-1">
          <span>分类</span>
          <YdSelect v-model="form.category" :options="categoryOptions" aria-label="模板分类" />
        </label>
        <label class="text-sm flex flex-col gap-1">
          <span>说明</span>
          <YdInput v-model="form.description" :maxlength="200" placeholder="可选，在插入器中显示" />
        </label>
        <label class="text-sm flex flex-col gap-1">
          <span>关键词</span>
          <YdInput v-model="form.keywords" placeholder="可选，逗号分隔，用于搜索" />
        </label>
        <p v-if="!editing" class="text-xs text-muted-foreground m-0">
          模板对本站点所有可编辑成员可见；插入的是独立副本，修改模板不会影响已插入的内容。
        </p>
      </div>
    </YdModal>
  </section>
</template>

<style scoped>
.studio-template-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.studio-template-item {
  display: flex;
  gap: 2px;
  align-items: center;
  border: 1px solid oklch(var(--border));
}
.studio-template-insert {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px 8px;
  text-align: left;
}
.studio-template-insert:hover {
  background: oklch(var(--accent));
}
.studio-template-insert small,
.studio-template-name {
  display: block;
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
