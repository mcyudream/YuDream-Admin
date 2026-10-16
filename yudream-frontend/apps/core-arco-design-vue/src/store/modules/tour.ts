import type { PluginGuide } from '@/api/modules/platform-plugin'
import { computed, ref } from 'vue'

export interface TourStepDefinition {
  /** 目标元素选择器，约定 [data-tour="..."] 形式；为空时该步骤居中展示。 */
  target?: string
  title?: string
  content: string
  placement?: 'top' | 'bottom' | 'left' | 'right'
}

export interface TourDefinition {
  code: string
  title: string
  /** 引导自动播放的页面路径。 */
  routePath: string
  permission?: string
  steps: TourStepDefinition[]
}

const COMPLETED_STORAGE_KEY = 'yd.tour.completed'
const TARGET_WAIT_INTERVAL = 250
const TARGET_WAIT_TIMEOUT = 8000

function normalizePath(path: string) {
  const trimmed = path.replace(/\/+$/, '')
  return trimmed || '/'
}

function readCompleted(): Record<string, Record<string, number>> {
  try {
    const raw = localStorage.getItem(COMPLETED_STORAGE_KEY)
    if (!raw) {
      return {}
    }
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed as Record<string, Record<string, number>> : {}
  }
  catch {
    return {}
  }
}

export const useTourStore = defineStore(
  'tour',
  () => {
    const appSettingsStore = useAppSettingsStore()
    const account = useAppAccountStore()
    const { auth } = useAppAuth()

    const pluginGuides = ref<TourDefinition[]>([])
    const active = ref<TourDefinition>()
    const open = ref(false)
    // 播放序号：新一次播放或关闭都会递增，用于丢弃迟到的等待回调
    let playToken = 0

    const builtInGuides = computed<TourDefinition[]>(() => [
      {
        code: 'platform.document.word-template',
        title: 'Word 模板上手',
        routePath: '/platform/document',
        permission: 'platform:document:edit',
        steps: [
          { title: '模板与记录', content: '这里维护 DOCX 模板和占位符，并在生成记录中回看每次导出。' },
          { target: '[data-tour="document-upload"]', title: '上传模板', content: '先上传 .docx 文件，再设置名称、编码和占位符。', placement: 'bottom' },
          { target: '[data-tour="document-guide"]', title: '查看写法示例', content: '教程弹窗提供变量、表格循环和 JSON 数据示例，可复制后调整。', placement: 'bottom' },
          { target: '[data-tour="document-tabs"]', title: '切换列表', content: '在模板列表与生成记录之间切换，生成后可从记录下载文件。', placement: 'bottom' },
          { title: '开始生成', content: '在已启用模板的操作菜单中选择“生成”，按占位符填写 JSON 后即可下载文档。跳过或完成本引导后，可通过 AI 助手再次播放。' },
        ],
      },
      {
        code: 'system.welcome',
        title: '欢迎使用余梦管理后台',
        routePath: appSettingsStore.settings.app.home.fullPath,
        steps: [
          { title: '欢迎使用', content: '这是你的管理控制台。跟随本引导认识几个常用入口，随时可以按 Esc 退出。' },
          { target: '[data-tour="topbar-notification"]', title: '通知中心', content: '顶栏的铃铛聚合系统通知与消息，未读数量实时更新。', placement: 'bottom' },
          { target: '.yd-global-ai-trigger', title: '全局 AI 助手', content: 'AI 助手可以帮你打开页面、定位操作并解答使用问题。', placement: 'top' },
          { title: '准备好了', content: '引导完成。之后想再看一遍，可以在 AI 助手里说“播放新手引导”。' },
        ],
      },
    ])

    const activeSteps = computed(() => active.value?.steps ?? [])

    function allGuides(): TourDefinition[] {
      return [...builtInGuides.value, ...pluginGuides.value]
    }

    function isPermitted(definition: TourDefinition) {
      return !definition.permission || auth(definition.permission)
    }

    function completedCodes(): Record<string, number> {
      const owner = String(account.userId || '')
      return readCompleted()[owner] ?? {}
    }

    function isCompleted(code: string) {
      return completedCodes()[code] !== undefined
    }

    function markCompleted(code: string) {
      const owner = String(account.userId || '')
      const map = readCompleted()
      map[owner] = { ...(map[owner] ?? {}), [code]: Date.now() }
      localStorage.setItem(COMPLETED_STORAGE_KEY, JSON.stringify(map))
    }

    function guidesForPath(path: string): TourDefinition[] {
      const normalized = normalizePath(path)
      return allGuides().filter(definition => isPermitted(definition) && normalizePath(definition.routePath) === normalized)
    }

    /** 当前页面可用引导目录（按权限过滤），供 AI 上下文与手动触发使用。 */
    function availableTours(path: string): Array<{ code: string, title: string }> {
      return guidesForPath(path).map(({ code, title }) => ({ code, title }))
    }

    function waitForTargets(definition: TourDefinition, token: number): Promise<boolean> {
      if (!definition.steps.some(step => step.target)) {
        return Promise.resolve(true)
      }
      return new Promise((resolve) => {
        const startedAt = Date.now()
        const poll = () => {
          if (token !== playToken) {
            resolve(false)
            return
          }
          if (definition.steps.every(step => !step.target || document.querySelector(step.target))) {
            resolve(true)
            return
          }
          if (Date.now() - startedAt >= TARGET_WAIT_TIMEOUT) {
            resolve(false)
            return
          }
          setTimeout(poll, TARGET_WAIT_INTERVAL)
        }
        poll()
      })
    }

    async function play(definition: TourDefinition) {
      const token = ++playToken
      // 等待目标元素出现（插件页面远程模块异步渲染），超时则放弃本次自动播放且不写完成态
      if (!await waitForTargets(definition, token) || token !== playToken) {
        return
      }
      active.value = definition
      open.value = true
    }

    /** 进入页面时触发：播放该页第一个未完成的可用引导。 */
    function handleRouteChange(path: string) {
      if (!account.isLogin || open.value) {
        return
      }
      const candidate = guidesForPath(path).find(definition => !isCompleted(definition.code))
      if (candidate) {
        void play(candidate)
      }
    }

    /** 手动/AI 触发：按编码播放引导；编码不存在或无权限时返回 false。 */
    function start(code: string): boolean {
      if (open.value) {
        return false
      }
      const definition = allGuides().find(item => item.code === code && isPermitted(item))
      if (!definition) {
        return false
      }
      void play(definition)
      return true
    }

    function close(code: string) {
      markCompleted(code)
      playToken++
      open.value = false
      active.value = undefined
    }

    function finish() {
      if (active.value) {
        close(active.value.code)
      }
    }

    function skip() {
      if (active.value) {
        close(active.value.code)
      }
    }

    function setPluginGuides(guides: PluginGuide[]) {
      pluginGuides.value = (guides ?? [])
        .filter(guide => guide.code && guide.title && guide.routePath && Array.isArray(guide.steps) && guide.steps.length > 0)
        .map(guide => ({
          code: `plugin:${guide.pluginCode}:${guide.code}`,
          title: guide.title,
          routePath: guide.routePath,
          permission: guide.permission || undefined,
          steps: guide.steps.map(step => ({
            target: step.target || undefined,
            title: step.title || undefined,
            content: step.content,
            placement: step.placement,
          })),
        }))
    }

    function reset() {
      playToken++
      open.value = false
      active.value = undefined
    }

    return {
      pluginGuides,
      active,
      open,
      activeSteps,
      isCompleted,
      availableTours,
      handleRouteChange,
      start,
      finish,
      skip,
      setPluginGuides,
      reset,
    }
  },
)
