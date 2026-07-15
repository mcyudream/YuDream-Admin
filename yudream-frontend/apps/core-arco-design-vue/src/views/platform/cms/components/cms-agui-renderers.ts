import { activityRegistry, agentToolcallRegistry } from '@tdesign-vue-next/chat'
import type { ActivityComponentProps, ToolcallComponentProps } from '@tdesign-vue-next/chat'
import { defineComponent, h } from 'vue'
import type { DefineComponent } from 'vue'
import { normalizeAguiCard } from '../config/cms-agui-card'

interface ToolResult {
  action?: string
  message?: string
  payload?: Record<string, unknown>
}

function webFetchPreview(payload?: Record<string, unknown>) {
  if (!payload) {
    return null
  }
  const status = payload.status === undefined ? '' : `HTTP ${payload.status}`
  const title = String(payload.title || '').trim()
  const description = String(payload.description || '').trim()
  const content = String(payload.content || '').replaceAll(/\s+/g, ' ').trim()
  const heading = [status, title].filter(Boolean).join(' · ')
  const summary = description || content.slice(0, 240)
  return heading || summary ? { heading, summary } : null
}

const toolLabels: Record<string, string> = {
  'cms.ask.user': '澄清需求',
  'cms.canvas.patch': '更新画布',
  'cms.canvas.validate': '校验画布完整性',
  'cms.canvas.selected.text': '修改选中文案',
  'cms.canvas.selected.html': '替换选中内容',
  'cms.canvas.selected.style': '修改选中样式',
  'cms.canvas.block.add': '追加画布区块',
  'cms.canvas.selected.remove': '删除选中元素',
  'cms.chrome.style': '校验或调整固定布局',
  'web.fetch': '网页参考内容',
}

const CmsProgressActivity = defineComponent({
  name: 'CmsProgressActivity',
  props: {
    content: { type: Object, required: true },
  },
  setup(props) {
    return () => {
      const content = (props.content || {}) as Record<string, string>
      return h('section', { 'class': 'cms-agui-activity', 'aria-live': 'polite' }, [
        h('span', { class: 'cms-agui-activity__dot' }),
        h('div', { class: 'cms-agui-activity__body' }, [
          h('strong', content.title || 'CMS 构建任务'),
          h('p', content.content || '正在处理请求。'),
        ]),
      ])
    }
  },
})

const AguiCardActivity = defineComponent({
  name: 'AguiCardActivity',
  props: {
    content: { type: Object, required: true },
  },
  setup(props) {
    async function runAction(action: 'copy' | 'open' | 'submit', value: string) {
      if (action === 'copy') {
        await navigator.clipboard.writeText(value)
      }
      else if (action === 'open' && value.startsWith('/')) {
        window.open(value, '_blank', 'noopener,noreferrer')
      }
      else if (action === 'submit' && value.trim()) {
        window.dispatchEvent(new CustomEvent('cms-ai-follow-up', { detail: value.trim() }))
      }
    }

    return () => {
      const card = normalizeAguiCard(props.content)
      return h('section', { class: ['cms-agui-card', `is-${card.tone}`] }, [
        h('header', { class: 'cms-agui-card__head' }, [
          h('strong', card.title),
          ...(card.summary ? [h('p', card.summary)] : []),
        ]),
        ...(card.fields.length
          ? [h('dl', { class: 'cms-agui-card__fields' }, card.fields.flatMap(field => [
              h('div', { class: 'cms-agui-card__field' }, [h('dt', field.label), h('dd', field.value)]),
            ]))]
          : []),
        ...(card.actions.length
          ? [h('div', { class: 'cms-agui-card__actions' }, card.actions.map(action => h('button', {
              type: 'button',
              onClick: () => void runAction(action.action, action.value),
            }, action.label)))]
          : []),
      ])
    }
  },
})

const CmsToolCall = defineComponent({
  name: 'CmsToolCall',
  props: {
    status: { type: String, required: true },
    toolName: { type: String, required: true },
    result: { type: Object, default: undefined },
    error: { type: Object, default: undefined },
  },
  setup(props) {
    function requestVariant() {
      window.dispatchEvent(new CustomEvent('cms-ai-follow-up', { detail: '基于刚才的区块再生成一个视觉变体' }))
    }

    return () => {
      const result = (props.result || {}) as ToolResult
      const status = props.status || 'executing'
      const message = props.error instanceof Error
        ? props.error.message
        : result.message || (status === 'executing' ? '正在执行。' : '操作已完成。')
      const action = result.action ? ` · ${result.action}` : ''
      const validationErrors = props.toolName === 'cms.canvas.validate' && Array.isArray(result.payload?.errors)
        ? result.payload.errors.map(String).join('；')
        : ''
      const displayStatus = props.toolName === 'cms.canvas.validate' && result.payload?.valid === false ? 'error' : status
      const fetchPreview = props.toolName === 'web.fetch' && displayStatus === 'complete'
        ? webFetchPreview(result.payload)
        : null
      const children: any[] = [
        h('span', { 'class': 'cms-agui-tool__state', 'aria-hidden': 'true' }),
        h('div', { class: 'cms-agui-tool__body' }, [
          h('strong', toolLabels[props.toolName] || props.toolName),
          h('p', validationErrors || `${message}${action}`),
          ...(fetchPreview?.heading ? [h('p', { class: 'cms-agui-tool__meta' }, fetchPreview.heading)] : []),
          ...(fetchPreview?.summary ? [h('p', { class: 'cms-agui-tool__preview' }, fetchPreview.summary)] : []),
        ]),
      ]
      if (props.toolName === 'cms.canvas.block.add' && displayStatus === 'complete') {
        children.push(
          h('button', {
            type: 'button',
            class: 'cms-agui-tool__action',
            onClick: requestVariant,
          }, '再生成一个变体'),
        )
      }
      return h('section', { class: ['cms-agui-tool', `is-${displayStatus}`] }, children)
    }
  },
})

export function registerCmsAguiRenderers() {
  if (!activityRegistry.has('cms-progress')) {
    activityRegistry.register({
      activityType: 'cms-progress',
      component: CmsProgressActivity as unknown as (props: ActivityComponentProps) => any,
      description: 'CMS 构建进度',
    })
  }
  if (!activityRegistry.has('agui-card')) {
    activityRegistry.register({
      activityType: 'agui-card',
      component: AguiCardActivity as unknown as (props: ActivityComponentProps) => any,
      description: 'AG-UI 结构化卡片',
    })
  }

  Object.entries(toolLabels).forEach(([name, description]) => {
    if (!agentToolcallRegistry.has(name)) {
      agentToolcallRegistry.register({
        name,
        description,
        component: defineComponent({
          name: `CmsAguiTool_${name.replaceAll('.', '_')}`,
          setup(_, { attrs }) {
            return () => h(CmsToolCall as any, { ...attrs, toolName: name })
          },
        }) as unknown as DefineComponent<ToolcallComponentProps>,
      })
    }
  })
}
