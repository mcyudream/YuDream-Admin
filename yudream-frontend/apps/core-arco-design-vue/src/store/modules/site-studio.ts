import type { StudioRelease, StudioState } from '@/api/modules/platform-cms-studio'
import type { SiteDocument, StudioCommand } from '@/types/site-studio'
import { defineStore } from 'pinia'
import { computed, ref, toRaw } from 'vue'
import apiStudio from '@/api/modules/platform-cms-studio'
import { applyStudioCommand, findStudioNode, validateStudioDocument } from '@/utils/site-studio'
import { cloneStudioJson } from '@/utils/site-studio-validation'

function stableJson(value: unknown): string {
  const normalize = (item: unknown): unknown => {
    if (Array.isArray(item)) {
      return item.map(normalize)
    }
    if (item && typeof item === 'object') {
      return Object.fromEntries(Object.entries(item).sort(([left], [right]) => left.localeCompare(right)).map(([key, child]) => [key, normalize(child)]))
    }
    return item
  }
  return JSON.stringify(normalize(value))
}

const MERGE_WINDOW_MS = 1000

export const useSiteStudioStore = defineStore('site-studio', () => {
  const themeCode = ref('default')
  const state = ref<StudioState | null>(null)
  const doc = ref<SiteDocument | null>(null)
  const savedJson = ref<string | null>(null)
  /** Key-order-insensitive JSON of `doc` for dirty/no-op checks only, maintained by setDoc. */
  const docJson = ref<string | null>(null)
  const history = ref<string[]>([])
  const future = ref<string[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const saving = ref(false)
  const publishing = ref(false)
  const saveError = ref('')
  const selectedNodeId = ref('')
  const activePageId = ref('')
  const editRegion = ref<'page' | 'header' | 'footer'>('page')
  const breakpoint = ref<'desktop' | 'tablet' | 'mobile'>('desktop')
  const releases = ref<StudioRelease[]>([])
  const releasesTotal = ref(0)
  const releasesPage = ref(1)
  const releasesLoading = ref(false)
  const releasesError = ref('')
  const localEditRevision = ref(0)
  let contextEpoch = 0
  /** Consecutive edits with the same mergeKey share one undo entry while nothing else changed the document. */
  let lastMerge: { key: string, at: number, revision: number } | null = null
  let releasesEpoch = 0

  const dirty = computed(() => doc.value !== null && docJson.value !== savedJson.value)
  const busy = computed(() => loading.value || saving.value || publishing.value)
  const canUndo = computed(() => history.value.length > 0 && !busy.value)
  const canRedo = computed(() => future.value.length > 0 && !busy.value)
  const activePage = computed(() => doc.value?.pages.find(page => page.id === activePageId.value) || null)
  const contextKey = computed(() => `site-studio:${themeCode.value}`)
  const editRevision = computed(() => `${state.value?.revision || 'draft'}#${localEditRevision.value}`)

  function assertEditable() {
    if (busy.value) {
      throw new Error('正在读取或保存站点，请完成后再修改')
    }
  }

  function errorText(error: unknown, fallback: string) {
    const cause = error as { response?: { data?: { message?: string } }, message?: string }
    return cause?.response?.data?.message || cause?.message || fallback
  }

  function trimHistory() {
    let bytes = history.value.reduce((total, value) => total + value.length * 2, 0)
    while (history.value.length > 50 || (bytes > 16 * 1024 * 1024 && history.value.length > 1)) {
      bytes -= (history.value.shift()?.length || 0) * 2
    }
  }

  /**
   * Every document replacement goes through here so docJson never goes stale.
   * History keeps insertion-order JSON: key order is CSS declaration order.
   */
  function setDoc(document: SiteDocument | null, json = document ? stableJson(document) : null) {
    doc.value = document
    docJson.value = json
    localEditRevision.value++
  }

  function snapshot() {
    return JSON.stringify(toRaw(doc.value))
  }

  function syncSelection() {
    if (doc.value && !doc.value.pages.some(page => page.id === activePageId.value)) {
      activePageId.value = doc.value.pages[0]?.id || ''
    }
    if (!doc.value || (selectedNodeId.value && !findStudioNode(doc.value, selectedNodeId.value))) {
      selectedNodeId.value = ''
    }
  }

  function decode(next: StudioState) {
    if (!next.documentJson) {
      return null
    }
    const document: unknown = JSON.parse(next.documentJson)
    validateStudioDocument(document)
    return document
  }

  function applyState(next: StudioState) {
    const document = decode(next)
    state.value = next
    setDoc(document)
    savedJson.value = docJson.value
    history.value = []
    future.value = []
    selectedNodeId.value = ''
    syncSelection()
  }

  async function load(code = themeCode.value) {
    const epoch = ++contextEpoch
    releasesEpoch++
    themeCode.value = code
    loading.value = true
    saving.value = false
    publishing.value = false
    loadError.value = ''
    saveError.value = ''
    state.value = null
    setDoc(null)
    savedJson.value = null
    history.value = []
    future.value = []
    selectedNodeId.value = ''
    releases.value = []
    releasesTotal.value = 0
    releasesLoading.value = false
    try {
      const response = await apiStudio.get(code)
      if (epoch !== contextEpoch || code !== themeCode.value) {
        return
      }
      applyState(response.data)
    }
    catch (error) {
      if (epoch === contextEpoch) {
        loadError.value = errorText(error, '网站工作台加载失败')
      }
    }
    finally {
      if (epoch === contextEpoch) {
        loading.value = false
      }
    }
  }

  function execute(command: StudioCommand | StudioCommand[], options: { mergeKey?: string } = {}) {
    assertEditable()
    if (!doc.value) {
      throw new Error('尚未创建站点文档')
    }
    const normalized: StudioCommand = Array.isArray(command) ? { type: 'batch', commands: command } : command
    // The store only ever holds validated documents; apply clones its input itself.
    const result = applyStudioCommand(toRaw(doc.value), normalized, { trustedInput: true })
    const resultJson = stableJson(result)
    if (resultJson === docJson.value) {
      return result
    }
    const now = Date.now()
    const chain = lastMerge
    const merging = chain !== null && Boolean(options.mergeKey) && chain.key === options.mergeKey
      && chain.revision === localEditRevision.value && now - chain.at < MERGE_WINDOW_MS && history.value.length > 0
    if (!merging) {
      history.value.push(snapshot())
      trimHistory()
    }
    future.value = []
    setDoc(result, resultJson)
    lastMerge = options.mergeKey ? { key: options.mergeKey, at: now, revision: localEditRevision.value } : null
    syncSelection()
    return result
  }

  function executeFromCanvas(command: StudioCommand) {
    return execute(command)
  }

  function undo() {
    assertEditable()
    if (!doc.value) {
      return
    }
    const previous = history.value.pop()
    if (!previous) {
      return
    }
    future.value.push(snapshot())
    setDoc(JSON.parse(previous) as SiteDocument)
    syncSelection()
  }

  function redo() {
    assertEditable()
    if (!doc.value) {
      return
    }
    const next = future.value.pop()
    if (!next) {
      return
    }
    history.value.push(snapshot())
    trimHistory()
    setDoc(JSON.parse(next) as SiteDocument)
    syncSelection()
  }

  async function saveDraft() {
    assertEditable()
    if (!doc.value || !state.value) {
      throw new Error('尚未创建站点草稿')
    }
    if (!dirty.value) {
      return
    }
    const epoch = contextEpoch
    const target = themeCode.value
    const documentJson = JSON.stringify(doc.value)
    validateStudioDocument(doc.value)
    saving.value = true
    saveError.value = ''
    try {
      const response = await apiStudio.saveDraft(target, state.value.revision, documentJson)
      if (epoch !== contextEpoch || target !== themeCode.value) {
        throw new Error('编辑目标已切换，原主题的保存结果不会覆盖当前页面')
      }
      const document = decode(response.data)
      state.value = response.data
      savedJson.value = document ? stableJson(document) : null
      // Edits made while the request was in flight stay dirty against the new baseline.
      localEditRevision.value++
    }
    catch (error) {
      if (epoch === contextEpoch) {
        saveError.value = errorText(error, '保存草稿失败，请重试')
      }
      throw error
    }
    finally {
      if (epoch === contextEpoch) {
        saving.value = false
      }
    }
  }

  async function changePublication(action: 'publish' | 'unpublish') {
    assertEditable()
    if (!state.value) {
      throw new Error('尚未加载站点状态')
    }
    const epoch = contextEpoch
    const target = themeCode.value
    if (action === 'publish' && dirty.value) {
      await saveDraft()
    }
    if (epoch !== contextEpoch || target !== themeCode.value || !state.value) {
      throw new Error('编辑目标已切换，请重新确认发布对象')
    }
    publishing.value = true
    saveError.value = ''
    try {
      const response = await apiStudio[action](target, state.value.revision)
      if (epoch !== contextEpoch || target !== themeCode.value) {
        throw new Error('编辑目标已切换，原主题的发布结果不会覆盖当前页面')
      }
      state.value = response.data
      localEditRevision.value++
    }
    catch (error) {
      if (epoch === contextEpoch) {
        saveError.value = errorText(error, action === 'publish' ? '发布失败，请重试' : '停止发布失败，请重试')
      }
      throw error
    }
    finally {
      if (epoch === contextEpoch) {
        publishing.value = false
      }
    }
  }

  const publish = () => changePublication('publish')
  const unpublish = () => changePublication('unpublish')

  async function loadReleases(page = 1) {
    const epoch = ++releasesEpoch
    const context = contextEpoch
    const target = themeCode.value
    releasesLoading.value = true
    releasesError.value = ''
    try {
      const response = await apiStudio.releases(target, page)
      if (epoch !== releasesEpoch || context !== contextEpoch || target !== themeCode.value) {
        return
      }
      releases.value = response.data.list || []
      releasesTotal.value = Number(response.data.total || 0)
      releasesPage.value = page
    }
    catch (error) {
      if (epoch === releasesEpoch && context === contextEpoch) {
        releasesError.value = errorText(error, '发布版本加载失败')
      }
    }
    finally {
      if (epoch === releasesEpoch && context === contextEpoch) {
        releasesLoading.value = false
      }
    }
  }

  async function restoreRelease(releaseId: string) {
    assertEditable()
    if (!state.value) {
      throw new Error('尚未加载站点状态')
    }
    const epoch = contextEpoch
    const target = themeCode.value
    saving.value = true
    saveError.value = ''
    try {
      const response = await apiStudio.restore(target, releaseId, state.value.revision)
      if (epoch !== contextEpoch || target !== themeCode.value) {
        throw new Error('编辑目标已切换，原主题的恢复结果不会覆盖当前页面')
      }
      applyState(response.data)
    }
    catch (error) {
      if (epoch === contextEpoch) {
        saveError.value = errorText(error, '恢复失败，请重试')
      }
      throw error
    }
    finally {
      if (epoch === contextEpoch) {
        saving.value = false
      }
    }
  }

  function loadDocumentLocally(document: SiteDocument) {
    assertEditable()
    validateStudioDocument(document)
    if (doc.value) {
      history.value.push(snapshot())
      trimHistory()
    }
    setDoc(cloneStudioJson(document))
    future.value = []
    activePageId.value = document.pages[0]?.id || ''
    selectedNodeId.value = ''
  }

  function reset() {
    contextEpoch++
    releasesEpoch++
    themeCode.value = 'default'
    state.value = null
    setDoc(null)
    savedJson.value = null
    history.value = []
    future.value = []
    selectedNodeId.value = ''
    activePageId.value = ''
    editRegion.value = 'page'
    saveError.value = ''
    loadError.value = ''
    loading.value = false
    saving.value = false
    publishing.value = false
    releases.value = []
    releasesTotal.value = 0
    releasesLoading.value = false
    releasesError.value = ''
  }

  return {
    themeCode,
    state,
    doc,
    savedJson,
    dirty,
    busy,
    canUndo,
    canRedo,
    activePage,
    contextKey,
    editRevision,
    loading,
    loadError,
    saving,
    publishing,
    saveError,
    selectedNodeId,
    activePageId,
    editRegion,
    breakpoint,
    releases,
    releasesTotal,
    releasesPage,
    releasesLoading,
    releasesError,
    load,
    execute,
    executeFromCanvas,
    undo,
    redo,
    saveDraft,
    publish,
    unpublish,
    loadReleases,
    restoreRelease,
    loadDocumentLocally,
    reset,
  }
})
