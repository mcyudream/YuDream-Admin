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
  <DemoCard title="FaButton" description="variant / size / loading / 图标按钮 / 禁用">
    <p class="demo-label">
      变体
    </p>
    <div class="demo-row">
      <FaButton>默认按钮</FaButton>
      <FaButton variant="destructive">
        危险按钮
      </FaButton>
      <FaButton variant="outline">
        边框按钮
      </FaButton>
      <FaButton variant="secondary">
        次要按钮
      </FaButton>
      <FaButton variant="ghost">
        幽灵按钮
      </FaButton>
      <FaButton variant="link">
        链接按钮
      </FaButton>
    </div>

    <p class="demo-label">
      尺寸
    </p>
    <div class="demo-row">
      <FaButton size="sm">
        小按钮
      </FaButton>
      <FaButton>默认尺寸</FaButton>
      <FaButton size="lg">
        大按钮
      </FaButton>
      <FaButton size="icon-sm" variant="outline">
        <FaIcon name="i-ri:search-line" />
      </FaButton>
      <FaButton size="icon" variant="outline">
        <FaIcon name="i-ri:settings-3-line" />
      </FaButton>
      <FaButton size="icon-lg" variant="outline">
        <FaIcon name="i-ri:add-line" />
      </FaButton>
    </div>

    <p class="demo-label">
      加载 / 禁用
    </p>
    <div class="demo-row">
      <FaButton loading>
        加载中
      </FaButton>
      <FaButton variant="outline" loading>
        保存中
      </FaButton>
      <FaButton disabled>
        禁用按钮
      </FaButton>
    </div>
  </DemoCard>

  <DemoCard title="FaTag" description="variant / 图标 / 可关闭">
    <p class="demo-label">
      变体
    </p>
    <div class="demo-row">
      <FaTag>默认标签</FaTag>
      <FaTag variant="destructive">
        危险标签
      </FaTag>
      <FaTag variant="outline">
        边框标签
      </FaTag>
      <FaTag variant="secondary">
        次要标签
      </FaTag>
    </div>

    <p class="demo-label">
      带图标
    </p>
    <div class="demo-row">
      <FaTag icon="i-ri:check-line">
        成功
      </FaTag>
      <FaTag icon="i-ri:alert-line" variant="destructive">
        警告
      </FaTag>
      <FaTag icon="i-ri:information-line" variant="outline">
        信息
      </FaTag>
      <FaTag icon="i-ri:star-line" variant="secondary">
        收藏
      </FaTag>
    </div>

    <p class="demo-label">
      可关闭
    </p>
    <div class="demo-row">
      <FaTag
        v-for="tag in tags"
        :key="tag.id"
        :variant="tag.variant"
        closable
        @close="closeTag(tag.id)"
      >
        {{ tag.label }}
      </FaTag>
    </div>
  </DemoCard>

  <DemoCard title="FaBadge" description="圆点 / 数字 / 文本 / 变体">
    <div class="demo-row !gap-8">
      <FaBadge :value="true">
        <FaIcon name="i-ri:notification-3-line" class="text-xl" />
      </FaBadge>
      <FaBadge :value="99">
        <FaIcon name="i-ri:notification-3-line" class="text-xl" />
      </FaBadge>
      <FaBadge value="新">
        <FaIcon name="i-ri:mail-line" class="text-xl" />
      </FaBadge>
      <FaBadge value="9" variant="secondary">
        <FaIcon name="i-ri:message-3-line" class="text-xl" />
      </FaBadge>
      <FaBadge value="9" variant="destructive">
        <FaIcon name="i-ri:alarm-warning-line" class="text-xl" />
      </FaBadge>
    </div>
  </DemoCard>

  <DemoCard title="FaAvatar" description="图片 / 文字兜底">
    <div class="demo-row">
      <FaAvatar src="https://fantastic-admin.hurui.me/logo.svg" />
      <FaAvatar src="https://fantastic-admin.hurui.me/logo.svg" class="size-12" />
      <FaAvatar src="" fallback="雨梦" />
      <FaAvatar src="" fallback="Fa" class="size-12" />
    </div>
  </DemoCard>
</template>
