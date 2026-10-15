<script setup lang="ts">
import type { YdImageCaptchaChallenge, YdImageCaptchaResult } from '@yudream/components'
import { useYdToast, YdAnchor, YdBreadcrumb, YdColorPicker, YdCountdown, YdImageCaptcha, YdInfiniteScroll, YdPopconfirm, YdResult, YdSegmented, YdSkeleton, YdTreeSelect, YdWatermark } from '@yudream/components'
import { computed, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()
const view = ref('table')
const color = ref('#4f46e5')
const selected = ref('engineering')
const captchaFeedback = ref('输入后按 Enter 触发验证事件')
const captchaToken = ref('')

// 预览用本地 mock：模拟服务端保存答案并签发一次性票据，真实项目必须替换为后端接口。
const mockCaptchaAnswers = new Map<string, string>()
const captchaAlphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
async function requestMockChallenge(): Promise<YdImageCaptchaChallenge> {
  await new Promise(resolve => setTimeout(resolve, 200))
  const code = Array.from({ length: 4 }, () => captchaAlphabet[Math.floor(Math.random() * captchaAlphabet.length)]).join('')
  const id = `mock-${Date.now()}`
  mockCaptchaAnswers.set(id, code)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="180" height="56"><rect width="180" height="56" fill="#f4f4f5"/><text x="90" y="38" font-size="30" font-family="monospace" font-weight="600" text-anchor="middle" fill="#3f3f46">${code}</text></svg>`
  return { id, image: `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`, expiresAt: Date.now() + 120_000 }
}
async function verifyMockChallenge({ challengeId, code }: { challengeId: string, code: string }): Promise<YdImageCaptchaResult> {
  await new Promise(resolve => setTimeout(resolve, 200))
  const expected = mockCaptchaAnswers.get(challengeId)
  mockCaptchaAnswers.delete(challengeId)
  const valid = Boolean(expected) && expected?.toUpperCase() === code.toUpperCase()
  return { valid, token: valid ? `demo-token-${challengeId}` : undefined }
}
function handleCaptchaVerify(result: YdImageCaptchaResult) {
  captchaFeedback.value = result.valid ? '校验通过' : '校验未通过'
  captchaToken.value = result.token ?? ''
}
const deadline = Date.now() + 90_000
const countdownText = computed(() => new Date(deadline).toISOString())
const tree = [{ key: 'engineering', label: '工程部', children: [{ key: 'platform', label: '平台组' }] }, { key: 'product', label: '产品部' }]
const crumbs = [{ label: '系统管理' }, { label: '成员' }]
const anchorItems = [{ id: 'new-component-overview', title: '概览' }, { id: 'new-component-controls', title: '控件' }]
const loading = ref(false)
const finished = ref(false)
const rows = ref(['第一条记录', '第二条记录'])
async function loadMore() {
  loading.value = true
  await new Promise(resolve => setTimeout(resolve, 600))
  rows.value.push(`追加记录 ${rows.value.length - 1}`)
  if (rows.value.length >= 6) {
    finished.value = true
  }
  loading.value = false
}
</script>

<template>
  <DemoCard title="YdPopconfirm" description="用于紧邻按钮的短确认；较长说明仍使用 useYdModal().confirm()">
    <YdPopconfirm title="移除该成员？" description="移除后将立即撤销其访问权限。" @confirm="toast.success('成员已移除')">
      <YdButton variant="destructive">
        移除成员
      </YdButton>
    </YdPopconfirm>
  </DemoCard>

  <DemoCard title="YdSkeleton" description="结构明确但数据尚未就绪时，保持内容布局稳定">
    <div class="flex gap-3 items-center">
      <YdSkeleton variant="circle" /><div class="flex-1 space-y-2">
        <YdSkeleton /><YdSkeleton width="60%" />
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdWatermark" description="页面追溯性标记，不替代权限控制或防截屏措施">
    <YdWatermark content="YuDream · 仅供内部使用" class="border rounded-md flex min-h-28 items-center justify-center">
      <span class="p-6">受保护的页面内容</span>
    </YdWatermark>
  </DemoCard>

  <DemoCard title="YdBreadcrumb" description="由结构化 items 渲染路径与当前页面">
    <YdBreadcrumb :items="crumbs" />
  </DemoCard>

  <DemoCard title="YdSegmented" description="紧凑切换同级视图，支持方向键导航">
    <YdSegmented v-model="view" :options="[{ label: '表格', value: 'table' }, { label: '卡片', value: 'cards' }, { label: '时间线', value: 'timeline' }]" aria-label="选择成员视图" />
  </DemoCard>

  <DemoCard title="YdColorPicker" description="可输入 HEX/RGB/HSL/OKLCH，颜色转换支持透明度">
    <YdColorPicker v-model="color" format="oklch" show-alpha :presets="['#2563eb', '#16a34a', '#dc2626', '#f59e0b']" />
  </DemoCard>

  <DemoCard title="YdTreeSelect" description="在表单字段中选择树节点，不用于权限树管理">
    <div class="max-w-sm">
      <YdTreeSelect v-model="selected" :data="tree" filterable />
    </div>
  </DemoCard>

  <DemoCard title="YdResult" description="展示一次任务的结果状态与下一步操作">
    <YdResult status="success" title="保存完成" description="成员配置已更新。">
      <template #action>
        <YdButton @click="toast('继续编辑')">
          继续编辑
        </YdButton>
      </template>
    </YdResult>
  </DemoCard>

  <DemoCard title="YdCountdown" description="基于目标时间戳计算，不受 interval 漂移累计影响">
    <YdCountdown :deadline="countdownText" format="HH:mm:ss" />
  </DemoCard>

  <DemoCard title="YdImageCaptcha" description="本地演示挑战；真实认证必须由服务端签发挑战并校验票据">
    <div class="flex flex-wrap gap-8 items-start">
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">mode="demo"（仅界面演示）</span>
        <YdImageCaptcha mode="demo" @verify="captchaFeedback = $event.valid ? '演示模式：校验通过' : '演示模式：校验未通过'" />
      </div>
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">mode="remote" + 本地 mock 提供器</span>
        <YdImageCaptcha mode="remote" :request-challenge="requestMockChallenge" :verify-challenge="verifyMockChallenge" @verify="handleCaptchaVerify" />
      </div>
    </div>
    <p class="text-sm text-muted-foreground mt-3">
      {{ captchaFeedback }}<template v-if="captchaToken">
        · 一次性票据：{{ captchaToken }}
      </template>
    </p>
  </DemoCard>

  <DemoCard title="YdInfiniteScroll" description="仅用于连续信息流；管理表格仍使用分页">
    <YdInfiniteScroll v-model:loading="loading" v-model:finished="finished" :load="loadMore" class="border rounded-md max-h-44 overflow-auto">
      <div v-for="row in rows" :key="row" class="px-3 py-2 border-b last:border-b-0">
        {{ row }}
      </div>
    </YdInfiniteScroll>
  </DemoCard>

  <DemoCard title="YdAnchor" description="页面目录滚动跟踪，链接可被复制并保持片段语义">
    <div class="gap-4 grid grid-cols-[9rem_1fr]">
      <YdAnchor :items="anchorItems" />
      <div class="max-h-48 overflow-auto space-y-5">
        <section id="new-component-overview" class="min-h-32">
          概览区
        </section><section id="new-component-controls" class="min-h-32">
          控件区
        </section>
      </div>
    </div>
  </DemoCard>
</template>
