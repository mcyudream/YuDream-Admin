<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const activeTab = ref('profile')
const tabList = [
  { label: '资料', value: 'profile' },
  { label: '账号', value: 'account' },
  { label: '通知', value: 'notice' },
]

const page = ref(1)
const size = ref(10)
const total = ref(100)

const progress = ref(33)
</script>

<template>
  <DemoCard title="FaCard" description="标题 / 描述 / 自定义头尾">
    <div class="demo-row !items-start">
      <FaCard title="卡片标题" description="这是一段卡片描述文字" class="w-72">
        卡片内容区域，可放置任意内容。
      </FaCard>
      <FaCard class="w-96">
        <template #header>
          <div class="flex items-center justify-between gap-4">
            <div>
              <div class="text-base font-semibold">
                自定义头部
              </div>
              <div class="text-sm text-muted-foreground">
                header slot 会覆盖 title 和 description
              </div>
            </div>
            <FaIcon name="i-ri:badge-check-line" class="size-5 text-primary" />
          </div>
        </template>

        卡片内容区域

        <template #footer>
          <div class="flex w-full justify-end gap-2">
            <FaButton variant="outline">
              取消
            </FaButton>
            <FaButton>确定</FaButton>
          </div>
        </template>
      </FaCard>
    </div>
  </DemoCard>

  <DemoCard title="FaTabs" description="list + 插槽面板">
    <FaTabs v-model="activeTab" :list="tabList" class="max-w-xl">
      <template #profile>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示用户基础资料。
        </div>
      </template>
      <template #account>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示账号安全设置。
        </div>
      </template>
      <template #notice>
        <div class="rounded-md bg-muted/50 p-4 text-sm">
          这里展示消息通知偏好。
        </div>
      </template>
    </FaTabs>
  </DemoCard>

  <DemoCard title="FaPagination" description="页码 / 每页条数 / 总数">
    <FaPagination v-model:page="page" v-model:size="size" :total="total" />
  </DemoCard>

  <DemoCard title="FaProgress" description="不同进度值">
    <div class="grid max-w-xl gap-3">
      <FaProgress v-model="progress" />
      <FaProgress :model-value="66" />
      <FaProgress :model-value="100" />
    </div>
    <div class="demo-row">
      <FaButton size="sm" variant="outline" @click="progress = Math.max(0, progress - 10)">
        -10%
      </FaButton>
      <FaButton size="sm" variant="outline" @click="progress = Math.min(100, progress + 10)">
        +10%
      </FaButton>
    </div>
  </DemoCard>

  <DemoCard title="FaScrollArea" description="固定高度纵向滚动">
    <FaScrollArea class="h-56 w-64 border rounded-md">
      <div v-for="item in 20" :key="item" class="p-3 text-sm">
        滚动内容 {{ item }}
      </div>
    </FaScrollArea>
  </DemoCard>
</template>
