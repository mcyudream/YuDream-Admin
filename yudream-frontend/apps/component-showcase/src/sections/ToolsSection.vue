<script setup lang="ts">
import type { YdLoaderStep, YdLoadingType } from '@yudream/components'
import { YdCarousel, YdCodePreview, YdCombobox, YdCopyButton, YdEmpty, YdIconPicker, YdLayoutContainer, YdLinkPreview, YdLoading, YdMention, YdMultiStepLoader, YdQrcode, YdScratchOff, YdSmoothSwipe, YdStorageBox, YdTimeAgo, YdTree } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const slide = ref(0)
const slides = [{ title: '灵动', description: '轻盈层次，灵动反馈', icon: 'morph:sparkles' }, { title: '雅致', description: '柔和留白，从容有序', icon: 'morph:feather' }, { title: '紧凑', description: '清晰高效，一览更多', icon: 'morph:layout-panel-top' }]
const loadingTypes: YdLoadingType[] = ['plane', 'chase', 'bounce', 'wave', 'pulse', 'flow', 'swing', 'circle', 'circle-fade', 'grid', 'fold', 'wander']
const selected = ref<string | number>()
const options = [{ label: '设计团队', options: [{ label: '主题设计', value: '9007199254740993' }, { label: '交互设计', value: 'interaction' }, { label: '暂不可选', value: 'disabled', disabled: true }] }, { label: '工程团队', options: [{ label: '组件开发', value: 'components' }, { label: '平台建设', value: 'platform' }] }]
const mention = ref('')
const mentioned = ref<string[]>([])
const mentionItems = [{ label: '设计伙伴', value: 'designer' }, { label: '开发伙伴', value: 'developer' }, { label: '审核伙伴', value: 'reviewer', disabled: true }]
const icon = ref('morph:sparkles')
const treeRef = ref<InstanceType<typeof YdTree>>()
const tree = [{ key: '9007199254740993', label: '余梦平台', children: [{ key: 'theme', label: '主题中心' }, { key: 'plugin', label: '插件生态' }, { key: 'wiki', label: '知识库' }, { key: 'disabled', label: '暂不可选', disabled: true }] }]
const account = ref('demo-a')
const preset = ref({ keyword: '我的灵感', pageSize: 20 })
const loading = ref(false)
const failStep = ref(false)
const outcome = ref('等待执行')
function delay(signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) {
      reject(new Error('已取消'))
      return
    }
    let timer: ReturnType<typeof setTimeout> | undefined
    const abort = () => {
      clearTimeout(timer)
      reject(new Error('已取消'))
    }
    timer = setTimeout(() => {
      signal.removeEventListener('abort', abort)
      resolve()
    }, 500)
    signal.addEventListener('abort', abort, { once: true })
  })
}
const steps: YdLoaderStep[] = [
  { text: '整理想法', afterText: '想法已整理', name: 'draft', action: async ({ signal }) => {
    await delay(signal)
    return 'YD'
  } },
  { text: '生成界面', afterText: '界面已生成', name: 'design', action: async ({ signal }) => {
    await delay(signal)
    if (failStep.value) {
      throw new Error('演示错误：取消失败开关后可重新执行')
    }
    return 'ready'
  } },
  { text: '检查效果', afterText: '效果已确认', action: ({ results }) => results.design },
]
const qrcode = ref<InstanceType<typeof YdQrcode>>()
const qrValue = ref('https://yudream.online')
const created = new Date(Date.now() - 5 * 60000)
</script>

<template>
  <div class="tools-gallery">
    <DemoCard title="滑动、轮播与分栏" description="触摸滑动、键盘导航，分栏手柄支持方向键调整">
      <YdCarousel v-model="slide" :items="slides" autoplay class="carousel-demo">
        <template #item="{ item }">
          <div class="slide">
            <YdIcon :name="item.icon" /><h3>{{ item.title }}</h3><p>{{ item.description }}</p>
          </div>
        </template>
      </YdCarousel>
      <p class="demo-label">
        当前轮播：{{ slide + 1 }} / {{ slides.length }}
      </p>
      <YdSmoothSwipe :items="slides" item-size="70%" :gap="16" label="滑动卡片示例">
        <template #item="{ item }">
          <YdCard :title="item.title" :description="item.description" class="swipe-card" />
        </template>
      </YdSmoothSwipe>
      <YdLayoutContainer :panels="[{ key: 'ideas', defaultSize: 35, minSize: 20 }, { key: 'canvas', minSize: 30 }]" class="layout-demo">
        <template #ideas>
          <div class="layout-content">
            <strong>灵感列表</strong><p class="muted">
              拖动右侧手柄调整宽度。
            </p>
          </div>
        </template>
        <template #canvas>
          <div class="layout-content">
            <strong>你的画布</strong><p class="muted">
              窗口与主题变化自然适配。
            </p>
          </div>
        </template>
      </YdLayoutContainer>
    </DemoCard>
    <DemoCard title="搜索、提及与图标" description="YD 输入与弹出层，保留长字符串 ID">
      <div class="demo-row">
        <YdCombobox v-model="selected" :options="options" clearable aria-label="选择团队职责" /><YdIconPicker v-model="icon" />
      </div>
      <p class="demo-label">
        当前值：{{ selected || '未选择' }} · 图标：{{ icon }}
      </p>
      <YdMention v-model="mention" v-model:value="mentioned" :items="mentionItems" label="提及伙伴" />
      <p class="demo-label">
        已提及：{{ mentioned.join('、') || '暂无' }}
      </p>
      <div class="demo-row">
        <YdButton variant="outline" @click="treeRef?.expand()">
          全部展开
        </YdButton><YdButton variant="outline" @click="treeRef?.collapse()">
          全部折叠
        </YdButton>
      </div>
      <YdTree ref="treeRef" :data="tree" filterable checkable default-expand-all />
    </DemoCard>
    <DemoCard title="任务与加载" description="顺序执行、可取消、失败重试；十二种加载状态">
      <div class="demo-row">
        <YdButton @click="loading = true; outcome = '执行中'">
          运行演示任务
        </YdButton><YdCheckbox v-model="failStep">
          模拟第二步失败
        </YdCheckbox><span class="muted">{{ outcome }}</span>
      </div>
      <YdMultiStepLoader v-model:loading="loading" :steps="steps" title="把灵感变成界面" @complete="outcome = '全部完成'" @error="outcome = '执行失败，可重试'" @close="outcome = outcome === '执行中' ? '已取消' : outcome" />
      <div class="loaders">
        <div v-for="type in loadingTypes" :key="type">
          <YdLoading :type="type" :size="32" :text="type" />
        </div>
      </div>
    </DemoCard>
    <DemoCard title="存储与常用工具" description="本机预设按账号隔离，二维码在本机生成">
      <div class="demo-row">
        <YdButton v-for="item in ['demo-a', 'demo-b']" :key="item" :variant="account === item ? 'default' : 'outline'" @click="account = item">
          {{ item }}
        </YdButton><YdStorageBox :unique-key="account" box-id="component-demo" :data="preset" @take-out="preset = $event" />
      </div>
      <YdInput v-model="preset.keyword" aria-label="预设关键词" placeholder="输入预设内容，再保存到当前账号" />
      <div class="utility-grid">
        <div class="qr-demo">
          <YdQrcode ref="qrcode" :value="qrValue" :size="160" label="余梦网址二维码" /><YdInput v-model="qrValue" aria-label="二维码内容" /><YdButton variant="outline" @click="qrcode?.download()">
            下载二维码
          </YdButton>
        </div><div class="yd-stack">
          <YdLinkPreview url="https://fantastic-admin.hurui.me/" title="Fantastic-admin" description="YD 组件沿用的框架视觉基线。" /><p class="muted">
            示例创建于 <YdTimeAgo :date="created" />
          </p><YdCopyButton text="Hello, YuDream" label="复制问候语" />
        </div>
      </div>
    </DemoCard>
    <DemoCard title="刮开一点惊喜" description="拖动刮开，或用按钮直接揭晓">
      <YdScratchOff>
        <div class="scratch-prize">
          <YdIcon name="morph:gift" /><h3>今天也有新灵感</h3><p class="muted">
            把喜欢的细节，变成自己的作品。
          </p>
        </div><template #completed="{ reset }">
          <YdButton variant="outline" @click="reset">
            再体验一次
          </YdButton>
        </template>
      </YdScratchOff>
    </DemoCard>
    <YdCodePreview title="用统一的 YD 入口接入" code="&lt;YdButton&gt;开始创造&lt;/YdButton&gt;">
      <YdEmpty title="留白，也是设计的一部分" description="空状态支持插图、说明和下一步操作。">
        <template #action>
          <YdButton variant="outline" @click="loading = true">
            开始创造
          </YdButton>
        </template>
      </YdEmpty>
    </YdCodePreview>
  </div>
</template>

<style scoped>
.tools-gallery {
  display: grid;
  gap: var(--yd-workspace-gap);
}

.muted {
  color: var(--color-text-3);
}

.slide {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 36px;
  background: var(--color-fill-1);
}

.slide :deep(svg) {
  width: 36px;
  height: 36px;
}

.slide h3,
.slide p {
  margin: 0;
}

.swipe-card {
  min-height: 120px;
}

.layout-demo {
  height: 160px;
}

.layout-content {
  padding: var(--yd-surface-padding);
}

.loaders {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 24px;
  padding-block: 16px;
}

.utility-grid {
  display: grid;
  grid-template-columns: minmax(180px, 260px) 1fr;
  gap: 24px;
}

.qr-demo {
  display: grid;
  gap: 12px;
  justify-items: center;
}

.scratch-prize {
  display: grid;
  place-content: center;
  justify-items: center;
  height: 100%;
  text-align: center;
}

.scratch-prize :deep(svg) {
  width: 32px;
  height: 32px;
}

.scratch-prize h3 {
  margin: 12px 0 0;
}

@media (width <= 768px) {
  .loaders {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .utility-grid {
    grid-template-columns: 1fr;
  }
}
</style>
