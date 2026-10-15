<script setup lang="ts">
import type { YdChatActivity, YdChatCitation } from '@yudream/components'
import { useYdToast, YdAttachmentList, YdChatActions, YdChatProcess, YdChatReasoning, YdChatSender, YdChatSessionList, YdCitationList, YdSuggestion, YdWelcome } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

/** 与组件的 YdSessionItem 结构一致；该类型暂未从包根导出 */
interface DemoSessionItem {
  id: string
  title: string
  messageCount?: number
  pinned?: boolean
  scopeType?: string
  updatedAt?: string
}

const senderLoading = ref(false)

function handleSend(text: string) {
  senderLoading.value = true
  toast(`已发送：${text}`)
  setTimeout(() => {
    senderLoading.value = false
  }, 2000)
}

const citations: YdChatCitation[] = [
  {
    title: '平台权限模型设计规范',
    path: '架构设计 / 权限模型',
    spaceName: '平台知识库',
    excerpt: '权限码末段动作必须使用既定词表，按资源拆分查看、操作与危险权限……',
  },
  {
    title: '插件生命周期',
    path: '插件系统 / 运行时',
    spaceName: '平台知识库',
    sourceUrl: 'https://yudream.online/wiki/plugin-lifecycle',
  },
]

const activities: YdChatActivity[] = [
  { activityType: 'wiki-progress', phase: '理解问题', status: 'complete', title: '解析问题意图' },
  {
    activityType: 'wiki-retrieval',
    status: 'complete',
    title: '检索知识库',
    hits: [
      { title: '平台权限模型设计规范', path: '架构设计 / 权限模型', score: 0.92, excerpt: '权限码末段动作必须使用既定词表……' },
      { title: '插件生命周期', path: '插件系统 / 运行时', score: 0.81 },
    ],
  },
  { activityType: 'wiki-graph', status: 'running', title: '分析关联图谱' },
]

const sessions = ref<DemoSessionItem[]>([
  { id: 's1', title: '权限模型答疑', messageCount: 12, pinned: true, scopeType: 'WIKI', updatedAt: '2026-10-15 10:20' },
  { id: 's2', title: '插件开发入门', messageCount: 6, scopeType: 'WIKI', updatedAt: '2026-10-14 16:42' },
  { id: 's3', title: '本周平台动态', messageCount: 3, updatedAt: '2026-10-13 09:05' },
])
const activeSessionId = ref('s1')

const suggestionText = ref('')

const suggestionItems = [
  { key: 'translate', label: '翻译', description: '把选中文本翻译为英文', icon: 'morph:messages-square', value: '帮我翻译这段话：' },
  { key: 'summary', label: '总结', description: '提炼要点生成摘要', icon: 'morph:list-checks', value: '帮我总结以下内容：' },
  { key: 'polish', label: '润色', description: '改写为更正式的表达', icon: 'morph:pencil', value: '帮我润色这段文字：' },
]
</script>

<template>
  <DemoCard title="YdWelcome" description="会话空态欢迎语：标题、描述、推荐提问，select 返回点击的文本">
    <div class="p-2 border rounded-lg">
      <YdWelcome
        title="下午好，Husky"
        description="我可以基于平台知识库回答问题，也能帮你整理文档。"
        :suggestions="['平台权限模型是怎么设计的？', '如何开发一个插件？', '帮我总结本周平台动态']"
        @select="text => toast(`选中提问：${text}`)"
      />
    </div>
  </DemoCard>

  <DemoCard title="YdSuggestion" description="斜杠建议：在下方输入框输入 / 触发，select 返回建议项与过滤词">
    <div class="gap-2 grid max-w-md">
      <YdInput v-model="suggestionText" placeholder="输入 / 唤起指令建议" />
      <YdSuggestion :text="suggestionText" :items="suggestionItems" @select="(_, query) => toast(`选择指令（${query || '无过滤词'}）`)" />
    </div>
  </DemoCard>

  <DemoCard title="YdChatSender" description="聊天发送器：附件、建议 chips、生成中停止按钮；send 返回文本与附件">
    <YdChatSender
      :loading="senderLoading"
      :suggestions="['解释一下双闸门机制']"
      constrained
      @send="handleSend"
      @stop="senderLoading = false"
      @suggestion-click="text => toast(`点选建议：${text}`)"
    />
  </DemoCard>

  <DemoCard title="YdCitationList" description="引用来源：excerpt 原文片段 + 来源标识，select 返回被点引用">
    <YdCitationList :citations="citations" @select="item => toast(`打开引用：${item.title}`)" />
  </DemoCard>

  <DemoCard title="YdAttachmentList" description="附件列表（只读形态；可删除形态见浮层交互页）">
    <YdAttachmentList
      :attachments="[
        { fileName: '架构评审纪要.docx', contentType: 'application/msword', size: 88_431, kind: 'DOCUMENT' },
        { fileName: '拓扑图.png', contentType: 'image/png', size: 245_760, kind: 'IMAGE' },
      ]"
    />
  </DemoCard>

  <DemoCard title="YdChatReasoning" description="深度思考：content 为推理文本，pending 时标题旁显示加载态">
    <div class="p-3 border rounded-lg">
      <YdChatReasoning
        content="先定位权限模型的分层：平台配置闸门负责能力开关，用例应用闸门负责单次校验……其次确认动作词表与资源拆分规则……"
        :pending="false"
        default-expanded
      />
    </div>
  </DemoCard>

  <DemoCard title="YdChatActions" description="回答操作条：复制 / 重新生成 / 点赞点踩，iconOnly 切换纯图标模式">
    <div class="flex flex-col gap-3">
      <YdChatActions copy-text="可复制的回答正文" regenerable feedback @regenerate="toast('重新生成')" />
      <YdChatActions copy-text="可复制的回答正文" regenerable feedback icon-only />
    </div>
  </DemoCard>

  <DemoCard title="YdChatProcess" description="执行过程：activities 展示进度 / 检索命中 / 图谱分析等活动状态">
    <YdChatProcess :activities="activities" />
  </DemoCard>

  <DemoCard title="YdChatSessionList" description="会话列表：置顶分组、关键字过滤，create / select / rename / pin / remove 全事件">
    <div class="p-2 border rounded-lg max-w-xs w-full">
      <YdChatSessionList
        :sessions="sessions"
        :active-id="activeSessionId"
        @create="toast('新建会话')"
        @select="(id) => { activeSessionId = id; toast(`切换会话 ${id}`) }"
        @rename="session => toast(`重命名：${session.title}`)"
        @pin="session => toast(`${session.pinned ? '取消置顶' : '置顶'}：${session.title}`)"
        @remove="session => toast(`删除：${session.title}`)"
      />
    </div>
  </DemoCard>
</template>
