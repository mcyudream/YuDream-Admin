<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const tags = ref([
  { id: 1, label: '标签一', variant: 'default' as const },
  { id: 2, label: '标签二', variant: 'destructive' as const },
  { id: 3, label: '标签三', variant: 'outline' as const },
  { id: 4, label: '标签四', variant: 'secondary' as const },
])

function closeTag(id: number) {
  tags.value = tags.value.filter(tag => tag.id !== id)
}
</script>

<template>
  <DemoCard title="YdButton" description="variant / size / loading / 图标按钮 / 禁用">
    <p class="demo-label">
      变体
    </p>
    <div class="demo-row">
      <YdButton>默认按钮</YdButton>
      <YdButton variant="destructive">
        危险按钮
      </YdButton>
      <YdButton variant="outline">
        边框按钮
      </YdButton>
      <YdButton variant="secondary">
        次要按钮
      </YdButton>
      <YdButton variant="ghost">
        幽灵按钮
      </YdButton>
      <YdButton variant="link">
        链接按钮
      </YdButton>
    </div>

    <p class="demo-label">
      尺寸
    </p>
    <div class="demo-row">
      <YdButton size="sm">
        小按钮
      </YdButton>
      <YdButton>默认尺寸</YdButton>
      <YdButton size="lg">
        大按钮
      </YdButton>
      <YdButton size="icon-sm" variant="outline">
        <YdIcon name="morph:search" />
      </YdButton>
      <YdButton size="icon" variant="outline">
        <YdIcon name="morph:settings" />
      </YdButton>
      <YdButton size="icon-lg" variant="outline">
        <YdIcon name="morph:plus" />
      </YdButton>
    </div>

    <p class="demo-label">
      加载 / 禁用
    </p>
    <div class="demo-row">
      <YdButton loading>
        加载中
      </YdButton>
      <YdButton variant="outline" loading>
        保存中
      </YdButton>
      <YdButton disabled>
        禁用按钮
      </YdButton>
    </div>
  </DemoCard>

  <DemoCard title="YdTag" description="variant / 图标 / 可关闭">
    <p class="demo-label">
      变体
    </p>
    <div class="demo-row">
      <YdTag>默认标签</YdTag>
      <YdTag variant="destructive">
        危险标签
      </YdTag>
      <YdTag variant="outline">
        边框标签
      </YdTag>
      <YdTag variant="secondary">
        次要标签
      </YdTag>
    </div>

    <p class="demo-label">
      带图标
    </p>
    <div class="demo-row">
      <YdTag icon="morph:check">
        成功
      </YdTag>
      <YdTag icon="morph:triangle-alert" variant="destructive">
        警告
      </YdTag>
      <YdTag icon="morph:info" variant="outline">
        信息
      </YdTag>
      <YdTag icon="morph:star" variant="secondary">
        收藏
      </YdTag>
    </div>

    <p class="demo-label">
      可关闭
    </p>
    <div class="demo-row">
      <YdTag
        v-for="tag in tags"
        :key="tag.id"
        :variant="tag.variant"
        closable
        @close="closeTag(tag.id)"
      >
        {{ tag.label }}
      </YdTag>
    </div>
  </DemoCard>

  <DemoCard title="YdBadge" description="圆点 / 数字 / 文本 / 变体">
    <div class="demo-row !gap-8">
      <YdBadge :value="true">
        <YdIcon name="morph:bell" class="text-xl" />
      </YdBadge>
      <YdBadge :value="99">
        <YdIcon name="morph:bell" class="text-xl" />
      </YdBadge>
      <YdBadge value="新">
        <YdIcon name="morph:mail" class="text-xl" />
      </YdBadge>
      <YdBadge value="9" variant="secondary">
        <YdIcon name="morph:message-square" class="text-xl" />
      </YdBadge>
      <YdBadge value="9" variant="destructive">
        <YdIcon name="morph:alarm-clock" class="text-xl" />
      </YdBadge>
    </div>
  </DemoCard>

  <DemoCard title="YdAvatar" description="图片 / 文字兜底">
    <div class="demo-row">
      <YdAvatar src="https://fantastic-admin.hurui.me/logo.svg" />
      <YdAvatar src="https://fantastic-admin.hurui.me/logo.svg" class="size-12" />
      <YdAvatar src="" fallback="雨梦" />
      <YdAvatar src="" fallback="Fa" class="size-12" />
    </div>
  </DemoCard>

  <DemoCard title="YdButtonGroup" description="按钮组：orientation 控制方向，separator 显示分割线">
    <div class="flex flex-wrap items-center gap-6">
      <YdButtonGroup>
        <YdButton variant="outline" size="sm">
          日
        </YdButton>
        <YdButton variant="outline" size="sm">
          周
        </YdButton>
        <YdButton size="sm">
          月
        </YdButton>
      </YdButtonGroup>
      <YdButtonGroup orientation="vertical" separator>
        <YdButton variant="ghost" size="icon-sm" aria-label="加粗">
          <YdIcon name="morph:bold" />
        </YdButton>
        <YdButton variant="ghost" size="icon-sm" aria-label="斜体">
          <YdIcon name="morph:italic" />
        </YdButton>
        <YdButton variant="ghost" size="icon-sm" aria-label="下划线">
          <YdIcon name="morph:underline" />
        </YdButton>
      </YdButtonGroup>
    </div>
  </DemoCard>

  <DemoCard title="YdLabel" description="表单标签：required 显示必填标识">
    <div class="gap-4 grid max-w-md">
      <div class="gap-2 grid">
        <YdLabel for="demo-username">用户名</YdLabel>
        <YdInput id="demo-username" placeholder="请输入用户名" />
      </div>
      <div class="gap-2 grid">
        <YdLabel for="demo-email" required>
          邮箱地址
        </YdLabel>
        <YdInput id="demo-email" type="email" placeholder="请输入邮箱" />
      </div>
    </div>
  </DemoCard>
</template>
