<script setup lang="ts">
import type { YdChatAttachment } from '@yudream/components'
import { useYdDrawer, useYdToast } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()
const drawer = useYdDrawer()

const drawerOpen = ref(false)
const drawerSide = ref<'top' | 'bottom' | 'left' | 'right'>('right')
const popoverOpen = ref(false)
const drawerConfirmLoading = ref(false)

function openDrawer(side: 'top' | 'bottom' | 'left' | 'right') {
  drawerSide.value = side
  drawerOpen.value = true
}

function onDrawerConfirm() {
  drawerConfirmLoading.value = true
  setTimeout(() => {
    drawerConfirmLoading.value = false
    drawerOpen.value = false
    toast.success('已保存')
  }, 800)
}

function openFunctionalDrawer() {
  drawer.create({
    title: '函数式抽屉',
    content: '通过 useYdDrawer().create() 创建，确认后自动关闭。',
    showCancelButton: true,
    onConfirm: () => toast.success('确认操作'),
    onCancel: () => toast('已取消'),
  }).open()
}

const dropdownItems = [
  [
    { label: '编辑', icon: 'morph:pen', handle: () => toast('编辑') },
    { label: '复制链接', icon: 'morph:copy', handle: () => toast('复制链接') },
    {
      label: '导出',
      icon: 'morph:download',
      items: [
        [{ label: '导出为 PDF', handle: () => toast('导出 PDF') }],
        [{ label: '导出为 Word', handle: () => toast('导出 Word') }],
      ],
    },
  ],
  [
    { label: '删除', icon: 'morph:trash', variant: 'destructive' as const, handle: () => toast.error('删除') },
  ],
]

const contextMenuItems = [
  [
    { label: '查看详情', icon: 'morph:eye', handle: () => toast('查看详情') },
    { label: '重命名', icon: 'morph:pencil', handle: () => toast('重命名') },
  ],
  [
    { label: '禁用项', disabled: true },
  ],
  [
    { label: '删除', icon: 'morph:trash', variant: 'destructive' as const, handle: () => toast.error('删除') },
  ],
]

const senderAttachments = ref<YdChatAttachment[]>([
  { fileName: '需求说明.pdf', contentType: 'application/pdf', size: 482_311, kind: 'DOCUMENT' },
  { fileName: '封面图.png', contentType: 'image/png', size: 122_880, kind: 'IMAGE' },
])
</script>

<template>
  <DemoCard title="YdDrawer" description="抽屉：side 支持四个方向，内置确认/取消按钮与加载态；useYdDrawer 提供函数式调用">
    <div class="flex flex-wrap gap-2">
      <YdButton @click="openDrawer('right')">
        右侧抽屉
      </YdButton>
      <YdButton variant="outline" @click="openDrawer('bottom')">
        底部抽屉
      </YdButton>
      <YdButton variant="outline" @click="openFunctionalDrawer">
        函数式调用
      </YdButton>

      <YdDrawer
        v-model="drawerOpen"
        :side="drawerSide"
        :title="`${({ top: '顶部', bottom: '底部', left: '左侧', right: '右侧' })[drawerSide]}抽屉`"
        description="表单短编辑或详情审阅放在抽屉里，保留列表上下文。"
        show-cancel-button
        :confirm-button-loading="drawerConfirmLoading"
        @confirm="onDrawerConfirm"
        @cancel="toast('已取消')"
      >
        <div class="gap-4 grid">
          <div class="gap-2 grid">
            <YdLabel>项目名称</YdLabel>
            <YdInput placeholder="请输入项目名称" />
          </div>
          <div class="gap-2 grid">
            <YdLabel>项目描述</YdLabel>
            <YdTextarea :rows="3" placeholder="请输入项目描述" />
          </div>
        </div>
      </YdDrawer>
    </div>
  </DemoCard>

  <DemoCard title="YdPopover" description="弹出框：触发元素放默认插槽，面板内容必须放 #panel 插槽，点击外部自动关闭">
    <div class="flex flex-wrap gap-2">
      <YdPopover v-model:open="popoverOpen">
        <YdButton variant="outline">
          筛选选项
          <YdIcon name="morph:chevron-down" />
        </YdButton>
        <template #panel>
          <div class="p-1 gap-3 grid w-72">
            <div>
              <div class="text-sm font-medium">
                时间范围
              </div>
              <div class="text-xs text-muted-foreground mt-1">
                选择要统计的数据区间
              </div>
            </div>
            <YdSelect
              :options="[{ label: '最近 7 天', value: '7' }, { label: '最近 30 天', value: '30' }, { label: '最近 90 天', value: '90' }]"
              model-value="30"
            />
            <YdCheckboxGroup
              :options="[{ label: '含子部门', value: 'children' }, { label: '含已停用', value: 'disabled' }]"
              :model-value="['children']"
            />
            <div class="flex gap-2 justify-end">
              <YdButton size="sm" variant="outline" @click="popoverOpen = false">
                取消
              </YdButton>
              <YdButton size="sm" @click="popoverOpen = false; toast.success('已应用')">
                应用
              </YdButton>
            </div>
          </div>
        </template>
      </YdPopover>
    </div>
  </DemoCard>

  <DemoCard title="YdHoverCard" description="悬浮卡片：悬停触发（移动端勿依赖），default 为触发元素，内容放 #card">
    <YdHoverCard>
      <YdButton variant="link" class="!px-0">
        @设计伙伴
      </YdButton>
      <template #card>
        <div class="gap-3 grid w-72">
          <div class="flex gap-3 items-start">
            <YdAvatar src="" fallback="设" />
            <div class="flex-1 gap-0.5 grid">
              <div class="text-sm font-medium">
                设计伙伴
              </div>
              <div class="text-xs text-muted-foreground">
                @designer · 平台体验组
              </div>
            </div>
          </div>
          <p class="text-xs text-muted-foreground leading-5">
            负责平台视觉规范与组件设计，最近在维护三套外观的动效节奏。
          </p>
        </div>
      </template>
    </YdHoverCard>
  </DemoCard>

  <DemoCard title="YdDropdown" description="下拉菜单：items 为二维分组数组，组间自动分割线；handle 处理点击，items 嵌套子菜单">
    <div class="flex flex-wrap gap-2">
      <YdDropdown :items="dropdownItems">
        <YdButton variant="outline">
          操作菜单
          <YdIcon name="morph:chevron-down" />
        </YdButton>
      </YdDropdown>

      <YdDropdown :items="dropdownItems" align="end">
        <YdButton size="icon" variant="ghost" aria-label="更多操作">
          <YdIcon name="morph:ellipsis" />
        </YdButton>
      </YdDropdown>
    </div>
  </DemoCard>

  <DemoCard title="YdContextMenu" description="右键菜单：items 契约与 Dropdown 相同，default 插槽为右键触发区域">
    <YdContextMenu :items="contextMenuItems">
      <div class="text-sm text-muted-foreground border rounded-lg border-dashed flex h-36 cursor-context-menu items-center justify-center">
        在此区域点击右键
      </div>
    </YdContextMenu>
  </DemoCard>

  <DemoCard title="YdAttachmentList" description="附件列表：removable 开启删除按钮（聊天发送器同款契约）">
    <YdAttachmentList :attachments="senderAttachments" removable @remove="item => toast(`移除 ${item.fileName}`)" />
  </DemoCard>
</template>
