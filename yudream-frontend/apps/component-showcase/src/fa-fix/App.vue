<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const drawerOpen = ref(true)
const modalOpen = ref(true)
const popoverOpen = ref(true)

const dropdownItems = [
  [{ label: '个人资料' }, { label: '偏好设置' }],
  [{ label: '退出登录', danger: true }],
]
const contextItems = [
  [{ label: '打开' }, { label: '重命名' }],
  [{ label: '删除', danger: true }],
]
</script>

<template>
  <div class="fa-fix">
    <DemoCard title="YdDrawer" description="抽屉（展开态）">
      <div class="demo-row">
        <YdDrawer v-model:open="drawerOpen" title="抽屉标题" description="侧滑抽屉的描述文案">
          <p>抽屉正文内容占位。</p>
        </YdDrawer>
      </div>
    </DemoCard>

    <DemoCard title="YdModal" description="对话框（展开态）">
      <div class="demo-row">
        <YdModal v-model:open="modalOpen" title="对话框标题" description="对话框描述文案" confirm-button-text="知道了">
          <p>对话框正文内容占位。</p>
        </YdModal>
      </div>
    </DemoCard>

    <DemoCard title="YdDropdown" description="下拉菜单（展开态）">
      <div class="demo-row">
        <YdDropdown :items="dropdownItems" :open="true">
          <YdButton variant="outline">
            打开下拉菜单
          </YdButton>
        </YdDropdown>
      </div>
    </DemoCard>

    <DemoCard title="YdContextMenu" description="右键菜单（展开态）">
      <div class="demo-row">
        <YdContextMenu :items="contextItems" :open="true">
          <div>在此区域右键</div>
        </YdContextMenu>
      </div>
    </DemoCard>

    <DemoCard title="YdPopover" description="气泡卡片">
      <div class="demo-row">
        <YdPopover v-model:open="popoverOpen">
          <YdButton variant="outline">
            打开气泡
          </YdButton>
          <template #panel>
            <p>气泡卡片内容占位。</p>
          </template>
        </YdPopover>
      </div>
    </DemoCard>

    <DemoCard title="YdCollapsible" description="折叠面板（展开态）">
      <div class="demo-row">
        <YdCollapsible title="展开查看详情" :model-value="true">
          折叠面板的正文内容，用于收纳次要信息。
        </YdCollapsible>
      </div>
    </DemoCard>

    <DemoCard title="YdHoverCard" description="悬停卡片">
      <div class="demo-row">
        <YdHoverCard>
          悬停我查看卡片
          <template #content>
            <div>
              悬停卡片的内容占位，用于展示补充信息。
            </div>
          </template>
        </YdHoverCard>
      </div>
    </DemoCard>
  </div>
</template>

<style scoped>
.fa-fix {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1096px;
  padding: 24px;
  margin: 0 auto;
}
</style>
