<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const collapsibleOpen = ref(false)
const searchFold = ref(false)
</script>

<template>
  <DemoCard title="YdPageHeader" description="页面级标题：左侧标题与描述，右侧操作区（default 插槽），小屏自动换行">
    <div class="page-header-demo">
      <YdPageHeader title="我的皮肤与衣柜" description="角色、收藏和掉落预览，都在同一个工作区。">
        <YdButton variant="outline" size="sm">
          <YdIcon name="morph:settings" />
          管理角色
        </YdButton>
        <YdButton size="sm">
          去皮肤库
        </YdButton>
      </YdPageHeader>
    </div>
  </DemoCard>

  <DemoCard title="YdPageMain" description="页面主内容容器：title 分节标题，collaspe 开启悬停折叠">
    <div class="page-main-demo">
      <YdPageMain title="基础信息">
        <p class="text-sm text-muted-foreground leading-6">
          这里是分节内容。YdPageMain 通常放在 YdPageHeader 下方，为页面提供统一的分节标题与留白。
        </p>
      </YdPageMain>
      <YdPageMain title="长内容（可折叠）" collaspe height="88px">
        <p class="text-sm text-muted-foreground leading-6">
          设置 collaspe 后，鼠标悬停会在底部显示展开/收起按钮，折叠时内容以渐变遮罩收拢到 height 指定的高度。
        </p>
        <p class="text-sm text-muted-foreground leading-6 mt-2">
          折叠能力适合「摘要 + 可展开详情」的信息区块，不替代手风琴类的多面板交互。
        </p>
        <p class="text-sm text-muted-foreground leading-6 mt-2">
          这一段是补充内容，用来让折叠效果更明显。展开后可以完整阅读全部文本。
        </p>
      </YdPageMain>
    </div>
  </DemoCard>

  <DemoCard title="YdDivider" description="分割线：默认居中文字，position 控制文字位置，无插槽时为整线">
    <div class="max-w-xl">
      <p class="text-sm text-muted-foreground">
        上方内容
      </p>
      <YdDivider>分隔文字</YdDivider>
      <p class="text-sm text-muted-foreground">
        中间内容
      </p>
      <YdDivider position="start">
        起始位置
      </YdDivider>
      <p class="text-sm text-muted-foreground">
        下方内容
      </p>
      <YdDivider />
    </div>
  </DemoCard>

  <DemoCard title="YdFixedBar" description="固定栏：position 为 top / bottom，自动适配侧边栏宽度">
    <div class="fixed-bar-demo">
      <YdFixedBar position="top" class="rounded-md">
        <div class="text-sm px-4 py-2 flex items-center justify-between">
          <span class="text-muted-foreground">顶部固定栏（演示容器内静态示意）</span>
          <YdButton size="sm" variant="outline">
            刷新
          </YdButton>
        </div>
      </YdFixedBar>
      <p class="text-sm text-muted-foreground px-1 py-6">
        在真实页面中，FixedBar 会固定在滚动容器顶部或底部，适合列表批量操作栏、表单保存栏。
      </p>
      <YdFixedBar position="bottom" class="rounded-md">
        <div class="px-4 py-2 flex gap-2 items-center justify-end">
          <YdButton size="sm" variant="outline">
            取消
          </YdButton>
          <YdButton size="sm">
            保存
          </YdButton>
        </div>
      </YdFixedBar>
    </div>
  </DemoCard>

  <DemoCard title="YdCollapsible" description="折叠面板：v-model 控制展开，trigger 插槽拿到 open 状态">
    <div class="max-w-xl">
      <YdCollapsible v-model="collapsibleOpen">
        <template #trigger="{ open }">
          <YdButton variant="outline" :aria-expanded="open">
            <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
            {{ open ? '收起高级选项' : '展开高级选项' }}
          </YdButton>
        </template>
        <div class="mt-3 p-4 border rounded-md">
          <p class="text-sm text-muted-foreground leading-6">
            这里是折叠内容。展开/收起带有平滑的高度动画；不提供 trigger 插槽时，组件只渲染可折叠内容本身。
          </p>
        </div>
      </YdCollapsible>
    </div>
  </DemoCard>

  <DemoCard title="YdSearchBar" description="可折叠筛选容器：fold 双向绑定，插槽内按 fold 控制字段显示">
    <YdSearchBar v-model:fold="searchFold" background class="max-w-2xl">
      <template #default="{ fold }">
        <div class="gap-3 grid sm:grid-cols-2" :class="fold ? 'sm:grid-cols-1' : ''">
          <YdInput placeholder="关键字" clearable />
          <YdInput v-if="!fold" placeholder="创建人" clearable />
          <YdSelect
            v-if="!fold"
            :options="[{ label: '全部状态', value: '' }, { label: '已启用', value: 'enabled' }, { label: '已停用', value: 'disabled' }]"
            placeholder="状态"
          />
        </div>
      </template>
    </YdSearchBar>
  </DemoCard>

  <DemoCard title="YdKbd / YdKbdGroup" description="按键尺寸、行内按键与组合键展示；仅提供视觉样式，不监听快捷键">
    <div class="flex flex-col gap-4">
      <div class="flex flex-wrap items-center gap-3">
        <YdKbd size="xs">Esc</YdKbd>
        <YdKbd size="sm">Ctrl</YdKbd>
        <YdKbd>Shift</YdKbd>
        <YdKbd size="lg">Enter</YdKbd>
        <YdKbd size="xl">Space</YdKbd>
      </div>
      <p class="text-sm">
        按 <YdKbd size="sm">F</YdKbd> 搜索，或使用
        <YdKbdGroup><YdKbd>Ctrl</YdKbd><span>+</span><YdKbd>Shift</YdKbd><span>+</span><YdKbd>Del</YdKbd></YdKbdGroup>
        删除选中项。
      </p>
      <div class="flex items-center gap-4">
        <span class="text-sm">移动选择</span>
        <YdKbdGroup><YdKbd>↑</YdKbd><YdKbd>←</YdKbd><YdKbd>↓</YdKbd><YdKbd>→</YdKbd></YdKbdGroup>
      </div>
    </div>
  </DemoCard>
</template>

<style scoped>
.page-header-demo {
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.page-main-demo {
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.fixed-bar-demo {
  display: grid;
  gap: 8px;
  padding: 12px;
  background: var(--color-fill-1);
  border-radius: 8px;
}
</style>
