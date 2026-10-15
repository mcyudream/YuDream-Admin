<script setup lang="ts">
import { useYdImagePreview, useYdModal, useYdToast } from '@yudream/components'
import { h, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()
const modal = useYdModal()
const imagePreview = useYdImagePreview()

const modalOpen = ref(false)

const { open: openFunctionalModal } = modal.create({
  title: '函数式调用',
  description: '通过 useYdModal().create() 创建弹窗。',
  content: h('div', { class: 'text-sm text-muted-foreground leading-6' }, '这里是函数式调用渲染的内容。'),
  showCancelButton: true,
  onConfirm: () => toast.success('确认操作'),
  onCancel: () => toast('取消操作'),
})

function openConfirm() {
  modal.confirm({
    title: '确认删除？',
    content: '删除后不可恢复，请谨慎操作。',
    onConfirm: () => toast.success('已确认'),
    onCancel: () => toast('已取消'),
  })
}

const demoImages = [
  'https://fantastic-admin.hurui.me/logo.svg',
  'https://fantastic-mobile.hurui.me/logo.png',
]

const stateScenes = [
  { name: 'empty', label: '初始为空', hint: '等待第一条内容' },
  { name: 'no-result', label: '筛选无结果', hint: '提示调整条件' },
  { name: 'error', label: '执行失败', hint: '原因与重试' },
  { name: 'forbidden', label: '无权限', hint: '说明所需权限' },
  { name: 'success', label: '成功完成', hint: '结果与下一步' },
  { name: 'warning', label: '需要关注', hint: '保留现场与选择' },
] as const

const resultStates = ['success', 'info', 'warning', 'error', '403', '404', '500'] as const
</script>

<template>
  <DemoCard title="YdTooltip" description="不同方位的气泡提示">
    <div class="feedback-layout">
      <div class="demo-row !gap-6">
        <YdTooltip text="上方提示">
          <YdButton variant="outline">
            上方
          </YdButton>
        </YdTooltip>
        <YdTooltip text="右侧提示" side="right">
          <YdButton variant="outline">
            右侧
          </YdButton>
        </YdTooltip>
        <YdTooltip text="下方提示" side="bottom">
          <YdButton variant="outline">
            下方
          </YdButton>
        </YdTooltip>
        <YdTooltip text="注意噢！">
          <YdIcon name="morph:circle-question-mark" class="text-lg cursor-help" />
        </YdTooltip>
      </div>
      <aside class="feedback-note">
        <div class="feedback-note__heading">
          <YdTag variant="secondary">悬停 / 聚焦</YdTag>
          <span>短说明</span>
        </div>
        <p>Tooltip 只承载简短提示；键盘聚焦按钮或问号图标也能触发同一说明。</p>
      </aside>
    </div>
  </DemoCard>

  <DemoCard title="YdModal" description="模板方式 / useYdModal 函数式 / confirm 确认框">
    <div class="feedback-layout">
      <div class="demo-row">
        <YdButton @click="modalOpen = true">
          模板弹窗
        </YdButton>
        <YdButton variant="outline" @click="openFunctionalModal">
          函数式弹窗
        </YdButton>
        <YdButton variant="destructive" @click="openConfirm">
          确认框
        </YdButton>
      </div>
      <aside class="feedback-note">
        <div class="feedback-note__heading">
          <YdTag variant="outline">三种入口</YdTag>
          <span>聚焦任务</span>
        </div>
        <p>模板弹窗保留页面状态，函数式调用适合独立动作，确认框用于需要明确决策的危险操作。</p>
      </aside>
    </div>
    <YdModal v-model="modalOpen" title="弹窗标题" description="通过 v-model 控制显隐。">
      <div class="text-sm text-muted-foreground">
        这里是弹窗内容区域，可放置任意内容。
      </div>
    </YdModal>
  </DemoCard>

  <DemoCard title="YdImagePreview" description="useYdImagePreview 预览单张 / 多张">
    <div class="feedback-layout">
      <div class="demo-row">
        <YdButton @click="imagePreview.open(demoImages[0])">
          预览单张
        </YdButton>
        <YdButton variant="outline" @click="imagePreview.open(demoImages)">
          预览多张
        </YdButton>
        <YdButton variant="outline" @click="imagePreview.open(demoImages, 1)">
          预览多张（初始第 2 张）
        </YdButton>
      </div>
      <aside class="feedback-note">
        <div class="feedback-note__heading">
          <YdTag variant="secondary">图片预览</YdTag>
          <span>单张 / 多张</span>
        </div>
        <p>多图入口保留当前索引，适合连续浏览素材或从列表上下文打开详情。</p>
      </aside>
    </div>
  </DemoCard>

  <DemoCard title="YdAlert" description="持续可见的提示：icon / title / description，variant destructive 表达风险与错误">
    <div class="gap-3 grid max-w-2xl">
      <YdAlert
        icon="morph:info"
        title="平台能力未启用"
        description="当前工作区未启用备份能力，请先在「能力管理」中开启后再试。"
      />
      <YdAlert
        icon="morph:circle-check"
        title="配置已保存"
      >
        <template #description>
          变更将在下一次发布时生效，可前往
          <YdButton variant="link" size="sm" class="!h-auto !px-0.5">
            发布记录
          </YdButton>
          查看进度。
        </template>
      </YdAlert>
      <YdAlert
        icon="morph:circle-alert"
        variant="destructive"
        title="删除后不可恢复"
        description="该操作会同时清理关联的离线副本，请先确认备份已完成。"
      />
    </div>
  </DemoCard>

  <DemoCard title="YdStateIllustration" description="六个命名状态场景，颜色随明暗与三套外观变化；插画是装饰元素，不替代状态文案">
    <div class="state-grid">
      <div v-for="scene in stateScenes" :key="scene.name" class="state-tile">
        <YdStateIllustration :name="scene.name" size="sm" />
        <strong>{{ scene.label }}</strong>
        <span>{{ scene.hint }}</span>
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdEmpty" description="默认 56px 图标盒保持既有外观；传 illustration 或 #illustration 插槽才换成插画">
    <div class="feedback-layout">
      <div class="state-empties">
        <YdEmpty title="还没有成员" description="邀请第一位成员后，这里会显示成员列表。" />
        <YdEmpty illustration="empty" title="还没有成员" description="邀请第一位成员后，这里会显示成员列表。">
          <template #action>
            <YdButton size="sm">
              邀请成员
            </YdButton>
          </template>
        </YdEmpty>
      </div>
      <aside class="feedback-note">
        <div class="feedback-note__heading">
          <YdTag variant="secondary">两种写法</YdTag>
          <span>默认 / 插画</span>
        </div>
        <p>初始为空用 empty，筛选无结果用 no-result；失败与无权限属于 YdResult，不要用空态掩盖请求失败。</p>
      </aside>
    </div>
  </DemoCard>

  <DemoCard title="YdResult" description="整页结果与 403 / 404 / 500 状态；illustration 默认按状态映射，info 回落图标盒">
    <div class="result-grid">
      <YdResult v-for="state in resultStates" :key="state" :status="state" illustration-size="sm" />
    </div>
  </DemoCard>
</template>

<style scoped>
.feedback-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(240px, 0.75fr);
  gap: var(--yd-content-gap);
  align-items: center;
}

.feedback-note {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: var(--yd-surface-padding);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.feedback-note__heading {
  display: flex;
  gap: 8px;
  align-items: center;
  color: var(--color-text-3);
  font-size: 12px;
}

.feedback-note p {
  margin: 0;
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.65;
}

.state-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: var(--yd-content-gap);
}

.state-tile {
  display: grid;
  gap: 6px;
  justify-items: center;
  padding: var(--yd-content-gap);
  text-align: center;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.state-tile strong {
  font-size: 13px;
  color: var(--color-text-1);
}

.state-tile span {
  font-size: 12px;
  color: var(--color-text-3);
}

.state-empties {
  display: grid;
  gap: var(--yd-content-gap);
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: var(--yd-content-gap);
}

.result-grid :deep(.yd-result) {
  display: grid;
  align-content: center;
  min-height: 208px;
  padding: var(--yd-content-gap);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

@media (width <= 720px) {
  .feedback-layout {
    grid-template-columns: 1fr;
  }
}
</style>
