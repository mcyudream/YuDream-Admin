<script setup lang="ts">
import { YdCode, YdFab, YdList, YdListRow, YdMockupBrowser, YdMockupWindow, YdStat, YdStats, YdSteps, YdTimeline } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const currentStep = ref(1)
const browserUrl = ref('https://admin.example.com/overview')
const windowState = ref<'normal' | 'minimized' | 'maximized' | 'closed'>('normal')
const windowMessages = ref<string[]>([])
function recordWindowState(state: string) {
  windowMessages.value = [...windowMessages.value, `窗口状态：${state}`].slice(-2)
}
const browserMessages = ref<string[]>([])
function recordNavigation(url: string) {
  browserUrl.value = url
  browserMessages.value = [...browserMessages.value, `已切换到 ${url}`].slice(-3)
}
const steps = [
  { id: 'cart', title: '购物车', status: 'finish' as const },
  { id: 'details', title: '订单信息', status: 'process' as const },
  { id: 'payment', title: '付款', status: 'wait' as const },
  { id: 'done', title: '完成', status: 'error' as const, icon: 'morph:triangle-alert' },
]
const events = [
  { id: 'e1', datetime: '2026-10-15 10:20', title: '已创建工作区', description: '项目空间与初始权限已就绪。', icon: 'morph:check', side: 'start' as const },
  { id: 'e2', datetime: '2026-10-15 11:05', title: '连接数据源', description: '数据库连接通过检查。', icon: 'morph:database', side: 'end' as const },
  { id: 'e3', datetime: '2026-10-15 13:40', title: '发布预览', description: '生成可分享的预览地址。', icon: 'morph:globe', side: 'start' as const },
]
const code = 'pnpm install\npnpm --filter component-showcase dev\nready on http://localhost:5188'
const codePrefixes = ['$ ', '> ', '✓ ']
const fabOpen = ref(false)
const flowerOpen = ref(false)
const lastFabAction = ref('尚未选择操作')
const fabActions = [
  { id: 'new', label: '新建文档', icon: 'morph:file-plus' },
  { id: 'upload', label: '上传文件', icon: 'morph:upload' },
  { id: 'share', label: '分享链接', icon: 'morph:share-2' },
  { id: 'archive', label: '归档（暂不可用）', icon: 'morph:archive', disabled: true },
]
const flowerActions = fabActions.slice(0, 3)
</script>

<template>
  <DemoCard title="YdList / YdListRow" description="通用内容行布局；管理数据仍应使用 YdResponsiveTable 或 YdEnhancedTable">
    <YdList divided class="max-w-2xl">
      <YdListRow grow>
        <YdAvatar src="" fallback="MC" />
        <div class="flex-1 min-w-0">
          <strong>生存服务器周报</strong>
          <p class="text-sm text-muted-foreground">
            本周新增 4 个活动、修复 2 个已知问题
          </p>
        </div>
        <YdButton size="icon-sm" variant="ghost" aria-label="打开周报">
          <YdIcon name="morph:arrow-up-right" />
        </YdButton>
      </YdListRow>
      <YdListRow grow>
        <YdAvatar src="" fallback="AI" />
        <div class="flex-1 min-w-0">
          <strong>知识库同步</strong>
          <p class="text-sm text-muted-foreground">
            最近同步于今天 09:30
          </p>
        </div>
        <YdTag variant="secondary">
          已完成
        </YdTag>
      </YdListRow>
    </YdList>
  </DemoCard>

  <DemoCard title="YdMockupBrowser" description="纯展示浏览器外框，地址栏与内容由调用方提供">
    <YdMockupBrowser v-model:url="browserUrl" class="max-w-3xl" @navigate="recordNavigation" @refresh="browserMessages = [...browserMessages, '已刷新预览内容'].slice(-3)">
      <div class="p-8 text-center gap-2 grid min-h-48 place-items-center">
        <YdIcon name="morph:chart-no-axes-combined" class="text-3xl text-primary" />
        <strong>项目总览</strong>
        <span class="text-sm text-muted-foreground">地址栏只驱动本地演示状态，不会访问实际网站</span>
        <span v-for="message in browserMessages" :key="message" class="text-xs text-muted-foreground">{{ message }}</span>
      </div>
    </YdMockupBrowser>
  </DemoCard>

  <DemoCard title="YdMockupWindow" description="纯展示桌面窗口外框，可嵌入任意预览内容">
    <YdMockupWindow v-model="windowState" class="max-w-2xl" @update:model-value="recordWindowState">
      <template #title>
        <strong class="text-sm">预览窗口</strong>
      </template>
      <div class="p-6">
        <p class="text-sm text-muted-foreground">
          部署状态
        </p>
        <div class="mt-2 flex gap-2 items-center">
          <YdBadge value="运行中" variant="secondary" /><span>v2.8.1 · 最近更新 4 分钟前</span>
        </div>
        <p v-for="message in windowMessages" :key="message" class="text-xs text-muted-foreground mt-2">
          {{ message }}
        </p>
      </div>
    </YdMockupWindow>
  </DemoCard>

  <DemoCard title="YdSteps" description="逐项状态、图标和水平/垂直方向；v-model 与 change 保持原受控契约">
    <div class="gap-6 grid">
      <YdSteps v-model="currentStep" :steps="steps" clickable />
      <YdSteps :steps="steps" vertical />
    </div>
  </DemoCard>

  <DemoCard title="YdTimeline" description="日期、标记图标、两侧内容与横向/纵向排列">
    <div class="gap-8 grid lg:grid-cols-2">
      <YdTimeline :data="events" />
      <YdTimeline :data="events" direction="horizontal" compact />
    </div>
  </DemoCard>

  <DemoCard title="YdCode" description="代码高亮/复制，支持终端前缀与行号">
    <YdCode :code="code" language="bash" title="开发环境" :prefixes="codePrefixes" />
  </DemoCard>

  <DemoCard title="YdStats / YdStat" description="指标标题、数值、趋势说明、图标与操作槽；窄屏自动换行">
    <YdStats class="max-w-4xl">
      <YdStat title="页面访问" value="89,400" description="较上月增长 21%">
        <template #figure>
          <YdIcon name="morph:eye" />
        </template>
      </YdStat>
      <YdStat title="新注册用户" value="1,248" description="较上月增长 8.2%">
        <template #figure>
          <YdTrend value="8.2%" type="up" />
        </template>
        <template #actions>
          <YdButton size="sm" variant="outline">
            查看报告
          </YdButton>
        </template>
      </YdStat>
      <YdStat title="待处理事项" value="12" description="3 项即将到期">
        <template #figure>
          <YdIcon name="morph:bell-ring" />
        </template>
      </YdStat>
    </YdStats>
  </DemoCard>

  <DemoCard title="YdFab" description="图标浮动操作：纵向标签、扇形布局、禁用项、Escape 与外点关闭">
    <div class="gap-4 grid sm:grid-cols-2">
      <div class="border rounded-md h-96 relative">
        <span class="text-sm text-muted-foreground left-4 top-4 absolute">纵向快捷操作</span>
        <YdFab v-model:open="fabOpen" :actions="fabActions" position="absolute" trigger-icon="morph:plus" @select="lastFabAction = $event.label">
          <template #main-action="{ close }">
            <YdButton size="sm" variant="outline" @click="lastFabAction = '主操作'; close()">
              主操作
            </YdButton>
          </template>
        </YdFab>
      </div>
      <div class="border rounded-md h-96 relative">
        <span class="text-sm text-muted-foreground left-4 top-4 absolute">图标扇形布局</span>
        <YdFab v-model:open="flowerOpen" :actions="flowerActions" layout="flower" position="absolute" trigger-icon="morph:wand-sparkles" @select="lastFabAction = $event.label" />
      </div>
    </div>
    <p class="text-sm mt-3" role="status">
      最近操作：{{ lastFabAction }}
    </p>
  </DemoCard>
</template>
