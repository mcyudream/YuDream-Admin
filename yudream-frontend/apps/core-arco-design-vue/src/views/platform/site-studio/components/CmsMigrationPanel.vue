<script setup lang="ts">
// 旧版 CMS 内容迁移面板：读取当前主题的旧版首页与文章（apiCms.home/apiCms.page，旧 API 仅查看），
// 显式选择要复制的页面，预检逐项展示结果（失败不静默），确认后执行一次原子 batch——
// 只新增页面副本（含首页副本），不删除旧数据，不自动保存/发布。
// 预检捕获 contextKey/文档 id/editRevision 绑定，执行前复验：切主题、换文档或草稿变化后预检失效，绝不误导入。
import type { LegacyMigrationPlan, MigrationArticle, MigrationContextBinding, MigrationHome, MigrationMarkdownMode } from '@/utils/site-studio-migration'
import { computed, ref, watch } from 'vue'
import apiCms from '@/api/modules/platform-cms'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { isSameMigrationContext, planLegacyMigration } from '@/utils/site-studio-migration'

defineOptions({ name: 'CmsMigrationPanel' })

const MIGRATION_PAGE_SIZE = 100
const MIGRATION_ARTICLE_LIMIT = 500

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const loading = ref(false)
const loadError = ref('')
const truncated = ref(false)
const home = ref<MigrationHome | null>(null)
const articles = ref<MigrationArticle[]>([])
const selectedPageIds = ref<string[]>([])
const includeHome = ref(true)
const homePath = ref('/imported-home')
const markdownMode = ref<MigrationMarkdownMode>('text')
const plan = ref<LegacyMigrationPlan | null>(null)
const planBinding = ref<MigrationContextBinding | null>(null)

const markdownModeOptions = [
  { label: '纯文本保留', value: 'text' },
  { label: '安全渲染后解析', value: 'render' },
]

const articleOptions = computed(() => articles.value.map(article => ({
  label: `${article.title?.trim() || '未命名'}（${article.slug?.trim() || '无 slug'}）`,
  value: article.id,
})))

const currentContext = computed<MigrationContextBinding | null>(() => store.doc
  ? { contextKey: store.contextKey, documentId: store.doc.id, editRevision: store.editRevision }
  : null)

const planStale = computed(() => !!plan.value
  && (!planBinding.value || !currentContext.value || !isSameMigrationContext(planBinding.value, currentContext.value)))

const plannedCount = computed(() => (plan.value?.homeEntry ? 1 : 0) + (plan.value?.pageEntries.length || 0))

const canPrecheck = computed(() => !!store.doc && (!!home.value || articles.value.length > 0))

// 任一迁移输入变化后，旧预检结果不再代表将执行的内容，直接作废
watch([includeHome, homePath, markdownMode, selectedPageIds], () => {
  plan.value = null
  planBinding.value = null
})

async function loadSource() {
  if (!store.doc) {
    toast.error('先在「概览」创建站点文档，再迁移旧版内容')
    return
  }
  const theme = store.themeCode
  loading.value = true
  loadError.value = ''
  plan.value = null
  planBinding.value = null
  try {
    const homeResponse = await apiCms.home(theme)
    if (theme !== store.themeCode) {
      return
    }
    home.value = homeResponse.data
    const collected: MigrationArticle[] = []
    let pageNumber = 1
    let total = Number.POSITIVE_INFINITY
    while (collected.length < MIGRATION_ARTICLE_LIMIT && (pageNumber - 1) * MIGRATION_PAGE_SIZE < total) {
      const response = await apiCms.page({ page: pageNumber, size: MIGRATION_PAGE_SIZE, theme })
      if (theme !== store.themeCode) {
        return
      }
      const records = response.data.records || []
      collected.push(...records)
      total = Number(response.data.total || 0)
      if (records.length < MIGRATION_PAGE_SIZE) {
        break
      }
      pageNumber++
    }
    truncated.value = collected.length >= MIGRATION_ARTICLE_LIMIT && total > collected.length
    articles.value = collected
    selectedPageIds.value = []
    includeHome.value = !!home.value
    toast.success(`已读取旧版 CMS（主题 ${theme}）：首页布局 ${home.value ? 1 : 0} 份、文章 ${collected.length} 篇`)
  }
  catch (error) {
    if (theme === store.themeCode) {
      loadError.value = error instanceof Error ? error.message : '旧版 CMS 内容读取失败'
    }
  }
  finally {
    if (theme === store.themeCode) {
      loading.value = false
    }
  }
}

function runPrecheck() {
  if (!store.doc) {
    toast.error('先在「概览」创建站点文档，再迁移旧版内容')
    return
  }
  try {
    plan.value = planLegacyMigration({
      document: store.doc,
      home: home.value,
      includeHome: includeHome.value && !!home.value,
      homePath: homePath.value,
      articles: articles.value.filter(article => selectedPageIds.value.includes(article.id)),
      markdownMode: markdownMode.value,
    })
    planBinding.value = { contextKey: store.contextKey, documentId: store.doc.id, editRevision: store.editRevision }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '迁移预检失败')
  }
}

function executeMigration() {
  const binding = planBinding.value
  const current = plan.value
  if (!current || !binding || !current.commands.length) {
    return
  }
  const verify = () => !!currentContext.value && isSameMigrationContext(binding, currentContext.value)
  if (!verify()) {
    toast.error('站点上下文或草稿已变化，预检结果已失效，请重新预检')
    return
  }
  modal.confirm({
    title: '执行迁移？',
    content: `将一次性新增 ${plannedCount.value} 个页面副本：只新增，不修改或删除现有页面与旧版 CMS 数据，不自动保存或发布。迁移结果只进入本地草稿，完成后需手动保存。`,
    confirmButtonText: '执行迁移',
    onConfirm: () => {
      // 确认期间也可能切主题/编辑草稿：执行前最后一刻复验绑定
      if (!verify()) {
        toast.error('站点上下文或草稿已变化，本次迁移已放弃')
        return
      }
      try {
        store.execute(current.commands)
        toast.success(`已新增 ${plannedCount.value} 个页面副本（仅本地草稿，尚未保存）`)
        plan.value = null
        planBinding.value = null
        selectedPageIds.value = []
      }
      catch (error) {
        toast.error(error instanceof Error ? error.message : '迁移执行失败')
      }
    },
  })
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="旧版 CMS 内容迁移">
    <h2 class="text-sm font-semibold m-0">
      旧版 CMS 内容迁移
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      读取当前主题（{{ store.themeCode }}）的旧版首页与文章，显式选择要复制的页面，预检通过后执行一次原子 batch，把所选内容作为新页面副本加入工作台：不删除旧数据，不自动保存/发布。HTML 严格按安全元素与 992/768 断点解析，含 JS 的页面会被拒绝；Markdown 可保留纯文本或安全渲染后解析；外部资源链接保持原样引用，不自动搬运离线副本。
    </p>

    <div class="flex flex-wrap gap-2 items-center">
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :loading="loading" @click="loadSource">
        <YdIcon name="morph:cloud-download" /> 读取旧版首页与文章
      </YdButton>
      <span v-if="articles.length" class="text-xs text-muted-foreground">
        已读取文章 {{ articles.length }} 篇{{ truncated ? `（已达读取上限 ${MIGRATION_ARTICLE_LIMIT} 篇，其余未加载）` : '' }}
      </span>
    </div>
    <YdAlert v-if="loadError" variant="destructive" :title="loadError" />

    <template v-if="home || articles.length">
      <YdCheckbox v-model="includeHome" :disabled="!home">
        复制旧版首页布局为页面副本（不覆盖当前根 /）
      </YdCheckbox>

      <div class="flex flex-col gap-1">
        <div class="flex flex-wrap gap-2 items-center">
          <span class="text-sm">要复制的文章页</span>
          <YdButton variant="ghost" :disabled="!articles.length" @click="selectedPageIds = articles.map(article => article.id)">
            全选
          </YdButton>
          <YdButton variant="ghost" :disabled="!selectedPageIds.length" @click="selectedPageIds = []">
            清空
          </YdButton>
          <span class="text-xs text-muted-foreground">已选 {{ selectedPageIds.length }} / {{ articles.length }} 篇</span>
        </div>
        <YdCheckboxGroup v-model="selectedPageIds" :options="articleOptions" />
      </div>

      <div class="flex flex-wrap gap-4 items-center">
        <span class="text-sm flex gap-2 items-center">
          Markdown 方式
          <YdRadioGroup v-model="markdownMode" :options="markdownModeOptions" />
        </span>
        <span class="text-sm flex gap-2 items-center">
          首页副本路径
          <YdInput v-model="homePath" placeholder="/imported-home" class="w-56" aria-label="首页副本路径" />
        </span>
      </div>

      <div class="flex flex-wrap gap-2 items-center">
        <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!canPrecheck || loading" @click="runPrecheck">
          <YdIcon name="morph:shield-check" /> 预检
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" :disabled="!plan || !plan.commands.length || planStale" @click="executeMigration">
          <YdIcon name="morph:copy-check" /> 执行迁移
        </YdButton>
      </div>

      <template v-if="plan">
        <YdAlert v-if="planStale" variant="destructive" title="预检结果已失效：站点上下文或草稿已变化，请重新预检" />
        <YdAlert v-if="plan.failures.length" variant="destructive" title="以下条目迁移失败，已逐项跳过（不影响其他条目）">
          <template #description>
            <ul class="m-0 p-0 list-none flex flex-col gap-1">
              <li v-for="(failure, index) in plan.failures" :key="`${failure.sourceId}-${index}`">
                {{ failure.title }}：{{ failure.reason }}
              </li>
            </ul>
          </template>
        </YdAlert>
        <template v-if="plannedCount">
          <p class="text-xs text-muted-foreground m-0">
            预检通过 {{ plannedCount }} 项，将新增以下页面副本：
          </p>
          <ul class="text-xs m-0 p-0 list-none flex flex-col gap-1">
            <li v-if="plan.homeEntry" :key="plan.homeEntry.sourceId">
              首页副本「{{ plan.homeEntry.title }}」 → {{ plan.homeEntry.path }}
            </li>
            <li v-for="entry in plan.pageEntries" :key="entry.sourceId">
              「{{ entry.title }}」（{{ entry.sourceSlug || '无 slug' }}） → {{ entry.path }}
            </li>
          </ul>
        </template>
        <YdAlert v-if="plan.warnings.length" icon="morph:info" title="迁移提示">
          <template #description>
            <ul class="m-0 p-0 list-none flex flex-col gap-1">
              <li v-for="(warning, index) in plan.warnings" :key="`${warning.sourceId}-${index}`">
                {{ warning.title }}：{{ warning.reason }}
              </li>
            </ul>
          </template>
        </YdAlert>
        <YdAlert v-if="plan.externalUrls.length" icon="morph:info" :title="`外部资源引用 ${plan.externalUrls.length} 项保持原链接，不自动搬运离线副本`">
          <template #description>
            <span class="break-all">{{ plan.externalUrls.join('；') }}</span>
          </template>
        </YdAlert>
      </template>
    </template>
  </section>
</template>
