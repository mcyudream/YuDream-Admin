import type { WikiNode } from '@/api/modules/platform-wiki'
import { computed, reactive } from 'vue'

export interface WikiDraftFields {
  title: string
  body: string
  pageType: string
  related: string[]
  tags: string[]
  sources: string[]
  summary: string
}

export function wikiDraftFields(node: WikiNode): WikiDraftFields {
  return {
    title: node.title,
    body: node.body ?? node.markdown ?? '',
    pageType: node.pageType ?? 'concept',
    related: [...(node.related ?? [])],
    tags: [...(node.tags ?? [])],
    sources: [...(node.sources ?? [])],
    summary: node.summary ?? '',
  }
}

export function createWikiEditorState() {
  const state = reactive({
    key: '',
    generation: 0,
    fields: {
      title: '',
      body: '',
      pageType: 'concept',
      related: [],
      tags: [],
      sources: [],
      summary: '',
    } as WikiDraftFields,
    saved: '',
    remote: '',
    conflict: false,
  })
  const dirty = computed(() => Boolean(state.key && JSON.stringify(state.fields) !== state.saved))

  function load(key: string, fields: WikiDraftFields) {
    const snapshot = JSON.stringify(fields)
    if (key === state.key && dirty.value) {
      if (snapshot !== state.remote && snapshot !== state.saved) {
        state.remote = snapshot
        state.conflict = true
      }
      return
    }
    if (key === state.key && state.conflict) {
      return
    }
    if (key !== state.key) {
      state.generation++
    }
    state.key = key
    Object.assign(state.fields, JSON.parse(snapshot) as WikiDraftFields)
    state.saved = snapshot
    state.remote = snapshot
    state.conflict = false
  }

  function ticket() {
    return { key: state.key, generation: state.generation, snapshot: JSON.stringify(state.fields) }
  }

  function acknowledge(saved: ReturnType<typeof ticket>) {
    if (saved.key !== state.key || saved.generation !== state.generation) {
      return false
    }
    state.saved = saved.snapshot
    state.remote = saved.snapshot
    state.conflict = false
    return true
  }

  function discard() {
    Object.assign(state.fields, JSON.parse(state.remote) as WikiDraftFields)
    state.saved = state.remote
    state.conflict = false
  }

  return { state, dirty, load, ticket, acknowledge, discard }
}
