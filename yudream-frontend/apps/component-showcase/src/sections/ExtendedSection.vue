<script setup lang="ts">
import type { YdTourStep } from '@yudream/components'
import { YdAnimatedCountTo, YdCalendarDate, YdCalendarMonthRange, YdCalendarYear, YdCascader, YdCodePreview, YdCombobox, YdDigitalCard, YdEmpty, YdLoading, YdSteps, YdTimeline, YdTour, YdTree } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const count = ref(1286)
const step = ref(1)
const choice = ref<string | number>()
const cascade = ref<string | number>()
const tree = [{ key: 'platform', label: '余梦平台', children: [{ key: 'theme', label: '主题中心' }, { key: 'plugin', label: '插件生态' }, { key: 'wiki', label: '知识库' }] }]
const checkedTreeKeys = ref<string[]>([])
const locations = [{ value: 'design', label: '设计系统', children: [{ value: 'tokens', label: '主题令牌' }, { value: 'components', label: '共享组件' }] }, { value: 'build', label: '工程平台', children: [{ value: 'plugins', label: '插件开发' }] }]
const tourOpen = ref(false)
const tourSteps: YdTourStep[] = [
  { target: '[data-tour="tour-demo-start"]', title: '从这里开始', content: '点击“重新播放引导”可以再次查看这段演示。', placement: 'bottom' },
  { target: '[data-tour="tour-demo-target"]', title: '聚光灯目标', content: '引导会滚动到目标并高亮 data-tour 登记的元素。', placement: 'right' },
  { title: '居中步骤', content: '没有目标的步骤自动居中展示，适合开场与收尾说明。' },
]
</script>

<template>
  <div class="extended-demo">
    <div class="demo-hero">
      <span class="demo-eyebrow">YUDREAM / COMPONENT LAB</span><h2>有温度的界面，<br>有节奏的交互。</h2><p>同一套组件，三种工作节奏。颜色随主题，细节随外观。</p>
    </div>
    <div class="metric-grid">
      <YdDigitalCard title="今日访问" :digital="count" icon="morph:users" description="每一次变化，都清晰可见" trend="up">
        <template #value>
          <YdAnimatedCountTo :value="count" />
        </template>
      </YdDigitalCard><YdDigitalCard title="组件体验" digital="持续更新" icon="morph:sparkles" description="在这里预览正在实现的组件" /><YdDigitalCard title="设计原则" digital="轻盈 · 鲜活" icon="morph:swatch-book" description="与 Fa 一脉相承的 YD 设计语言" />
    </div>
    <YdButton variant="outline" @click="count += 137">
      让数字动起来 +137
    </YdButton>
    <DemoCard title="日历与选择" description="日期、月份范围、年份面板">
      <div class="calendar-demo">
        <YdCalendarDate /><YdCalendarMonthRange /><YdCalendarYear />
      </div>
    </DemoCard>
    <DemoCard title="数据录入" description="搜索、级联和树结构">
      <div class="demo-row">
        <YdCombobox v-model="choice" :options="[{ label: '灵动', value: 'vibrant' }, { label: '雅致', value: 'calm' }, { label: '紧凑', value: 'compact' }]" clearable style="width: 220px;" /><YdCascader v-model="cascade" :options="locations" clearable style="width: 220px;" />
      </div><YdTree v-model:checked-keys="checkedTreeKeys" :data="tree" checkable filterable default-expand-all />
      <p class="tree-selection-feedback" role="status">
        已勾选 {{ checkedTreeKeys.length }} 个节点
      </p>
    </DemoCard>
    <DemoCard title="进度与状态" description="清晰的过程、温和的反馈">
      <YdSteps v-model="step" :steps="[{ id: 'create', title: '创建项目', description: '让想法开始生长' }, { id: 'design', title: '设计界面', description: '找到舒服的节奏' }, { id: 'ship', title: '发布应用', description: '与大家见面' }]" clickable /><div class="demo-row">
        <YdLoading type="chase" /><YdLoading type="wave" /><YdLoading type="bounce" /><YdLoading type="grid" />
      </div><YdTimeline :data="[{ datetime: '2026-10-14', title: '共用主题令牌', description: '三套外观与插件主题沿用同一套变量。' }, { datetime: '2026-10-13', title: '开始新的体验', description: '从功能到交互，建立自己的组件语言。' }]" />
    </DemoCard>
    <DemoCard title="分步引导" description="聚光灯高亮、居中步骤与键盘导航">
      <div class="tour-demo">
        <YdButton data-tour="tour-demo-start" variant="outline" @click="tourOpen = true">
          重新播放引导
        </YdButton>
        <YdButton data-tour="tour-demo-target" variant="ghost" @click="count += 1">
          被高亮的目标（点击 +1）
        </YdButton>
      </div>
      <YdTour v-model:open="tourOpen" :steps="tourSteps" />
    </DemoCard>
    <YdCodePreview title="让接入更简单" code="&lt;YdAnimatedCountTo :value=&quot;1286&quot; /&gt;">
      <YdEmpty title="等待你的下一个灵感" description="通用组件正在逐步接入，预览会持续更新。" />
    </YdCodePreview>
  </div>
</template>

<style scoped>
.extended-demo {
  display: grid;
  gap: var(--yd-workspace-gap);
}

.demo-hero {
  padding: 12px 0;
}

.demo-eyebrow {
  font-size: 11px;
  color: var(--color-text-3);
  letter-spacing: 0.18em;
}

h2 {
  margin: 16px 0;
  font-size: clamp(1.8rem, 4vw, 3.25rem);
  line-height: 1.2;
  letter-spacing: -0.04em;
}

p {
  font-size: 14px;
  color: var(--color-text-3);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--yd-content-gap);
}

.calendar-demo {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yd-content-gap);
  align-items: flex-start;
}

.tree-selection-feedback {
  margin: -0.5rem 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.tour-demo {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yd-content-gap);
  align-items: center;
}

@media (width <= 768px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
