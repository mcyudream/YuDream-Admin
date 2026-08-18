<script setup lang="ts">
import { useFaImagePreview, useFaModal, useFaToast } from '@yudream/components'
import { h, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useFaToast()
const modal = useFaModal()
const imagePreview = useFaImagePreview()

const modalOpen = ref(false)

const { open: openFunctionalModal } = modal.create({
  title: '函数式调用',
  description: '通过 useFaModal().create() 创建弹窗。',
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
</script>

<template>
  <DemoCard title="FaTooltip" description="不同方位的气泡提示">
    <div class="demo-row !gap-6">
      <FaTooltip text="上方提示">
        <FaButton variant="outline">
          上方
        </FaButton>
      </FaTooltip>
      <FaTooltip text="右侧提示" side="right">
        <FaButton variant="outline">
          右侧
        </FaButton>
      </FaTooltip>
      <FaTooltip text="下方提示" side="bottom">
        <FaButton variant="outline">
          下方
        </FaButton>
      </FaTooltip>
      <FaTooltip text="注意噢！">
        <FaIcon name="i-ri:question-line" class="cursor-help text-lg" />
      </FaTooltip>
    </div>
  </DemoCard>

  <DemoCard title="FaModal" description="模板方式 / useFaModal 函数式 / confirm 确认框">
    <div class="demo-row">
      <FaButton @click="modalOpen = true">
        模板弹窗
      </FaButton>
      <FaButton variant="outline" @click="openFunctionalModal">
        函数式弹窗
      </FaButton>
      <FaButton variant="destructive" @click="openConfirm">
        确认框
      </FaButton>
    </div>
    <FaModal v-model="modalOpen" title="弹窗标题" description="通过 v-model 控制显隐。">
      <div class="text-sm text-muted-foreground">
        这里是弹窗内容区域，可放置任意内容。
      </div>
    </FaModal>
  </DemoCard>

  <DemoCard title="FaImagePreview" description="useFaImagePreview 预览单张 / 多张">
    <div class="demo-row">
      <FaButton @click="imagePreview.open(demoImages[0])">
        预览单张
      </FaButton>
      <FaButton variant="outline" @click="imagePreview.open(demoImages)">
        预览多张
      </FaButton>
      <FaButton variant="outline" @click="imagePreview.open(demoImages, 1)">
        预览多张（初始第 2 张）
      </FaButton>
    </div>
  </DemoCard>
</template>
