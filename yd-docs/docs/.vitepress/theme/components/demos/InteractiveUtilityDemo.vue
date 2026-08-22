<script setup lang="ts">
import { ref } from 'vue'
import {
  FaAlert,
  FaAvatar,
  FaBadge,
  FaButton,
  FaCollapsible,
  FaContextMenu,
  FaDescriptions,
  FaDivider,
  FaDropdown,
  FaFixedBar,
  FaInput,
  FaKbd,
  FaKbdGroup,
  FaNumberField,
  FaPageHeader,
  FaPageMain,
  FaProgress,
  FaSearchBar,
  FaSlider,
} from '@yudream/components'

const props = defineProps<{ type: string; variant?: string }>()
const progress = ref(33)
const slider = ref([30])
const range = ref([20, 80])
const number = ref(8080)
const fold = ref(true)
const open = ref(false)
const notice = ref(true)
const action = ref('尚未选择操作')
const dropdownItems = [[
  { label: '查看详情', handle: () => action.value = '已选择：查看详情' },
  { label: '编辑', handle: () => action.value = '已选择：编辑' },
], [{ label: '删除', variant: 'destructive' as const, handle: () => action.value = '已选择：删除' }]]
const contextItems = [[
  { label: '打开', handle: () => action.value = '已选择：打开' },
  { label: '复制', handle: () => action.value = '已选择：复制' },
], [{ label: '导出为', items: [[{ label: 'PDF', handle: () => action.value = '已导出 PDF' }]] }]]
const descriptions = [
  { key: 'id', label: '用户 ID', value: '1932574829104' },
  { key: 'name', label: '用户名', value: 'husky' },
  { key: 'role', label: '角色', value: '管理员' },
  { key: 'status', label: '状态', span: 2, value: '启用' },
]
</script>

<template>
  <div class="interactive-utility-demo">
    <template v-if="props.type === 'progress'">
      <FaProgress v-model="progress" />
      <p>当前进度：{{ progress }}%</p>
    </template>
    <template v-else-if="props.type === 'slider'">
      <FaSlider v-if="props.variant !== 'range'" v-model="slider" />
      <FaSlider v-else v-model="range" :step="10" />
      <p>当前值：{{ props.variant === 'range' ? range.join(' - ') : slider[0] }}</p>
    </template>
    <template v-else-if="props.type === 'number-field'">
      <FaNumberField v-model="number" :min="1" :max="65535" :step="1" />
      <p>端口：{{ number }}</p>
    </template>
    <template v-else-if="props.type === 'search-bar'">
      <FaSearchBar v-model:fold="fold" show-toggle background>
        <template #default="{ fold: collapsed }">
          <div class="demo-fields"><FaInput placeholder="用户名" /><FaInput placeholder="手机号" /><FaButton>搜索</FaButton><FaInput v-if="!collapsed" placeholder="邮箱" /></div>
        </template>
      </FaSearchBar>
    </template>
    <template v-else-if="props.type === 'avatar'">
      <div class="demo-row"><FaAvatar src="" fallback="YuDream" /><FaAvatar src="" fallback="Admin" /></div>
    </template>
    <template v-else-if="props.type === 'alert'">
      <FaAlert icon="i-lucide:info" title="提示信息" description="这是一条真实组件渲染的提示。" />
      <FaAlert v-if="props.variant === 'destructive'" icon="i-lucide:circle-alert" title="危险提示" description="删除后数据将无法恢复。" variant="destructive" />
    </template>
    <template v-else-if="props.type === 'badge'">
      <div class="demo-row"><FaBadge :value="notice"><FaButton variant="outline" @click="notice = !notice">通知</FaButton></FaBadge><FaBadge :value="99"><span>消息</span></FaBadge></div>
    </template>
    <template v-else-if="props.type === 'divider'">
      <span>上方内容</span><FaDivider>分割文字</FaDivider><FaDivider v-if="props.variant === 'position'" position="start">靠左文字</FaDivider><span>下方内容</span>
    </template>
    <template v-else-if="props.type === 'collapsible'">
      <FaCollapsible v-model="open"><template #trigger="{ open: expanded }"><FaButton variant="outline">{{ expanded ? '收起' : '展开' }}</FaButton></template><div class="collapsible-content">可展开和收起的真实内容。</div></FaCollapsible>
    </template>
    <template v-else-if="props.type === 'context-menu'">
      <FaContextMenu :items="contextItems"><div class="context-target">请在此区域点击右键</div></FaContextMenu><p>{{ action }}</p>
    </template>
    <template v-else-if="props.type === 'descriptions'">
      <FaDescriptions :items="descriptions" :column="3" border />
    </template>
    <template v-else-if="props.type === 'fixed-bar'">
      <div id="fixed-content-before-area" class="fixed-target" /><div id="fixed-content-after-area" class="fixed-target" /><FaFixedBar position="top"><FaButton>顶部操作</FaButton></FaFixedBar><FaFixedBar position="bottom" class="fixed-actions"><FaButton variant="outline">取消</FaButton><FaButton>保存</FaButton></FaFixedBar>
    </template>
    <template v-else-if="props.type === 'dropdown'">
      <FaDropdown :items="dropdownItems"><FaButton>操作</FaButton></FaDropdown><p>{{ action }}</p>
    </template>
    <template v-else-if="props.type === 'page-main'">
      <FaPageMain title="长文本说明" collaspe height="96px" class="m-0"><p v-for="i in 5" :key="i">可折叠的页面主体内容 {{ i }}</p></FaPageMain>
    </template>
    <template v-else-if="props.type === 'page-header'">
      <FaPageHeader title="用户管理" description="管理系统账号、角色与权限"><FaButton size="sm">新增用户</FaButton></FaPageHeader>
    </template>
    <template v-else-if="props.type === 'kbd'">
      <div class="demo-row"><FaKbd>Ctrl</FaKbd><FaKbd>Alt</FaKbd><FaKbd>Shift</FaKbd></div>
    </template>
    <template v-else-if="props.type === 'kbd-group'">
      <div class="demo-row"><FaKbdGroup><FaKbd>Ctrl</FaKbd><FaKbd>Alt</FaKbd><FaKbd>Delete</FaKbd></FaKbdGroup></div>
    </template>
  </div>
</template>

<style scoped>
.interactive-utility-demo { display: grid; gap: 12px; max-width: 640px; }
.interactive-utility-demo p { margin: 0; color: var(--vp-c-text-2); font-size: 13px; }
.demo-row, .demo-fields, .fixed-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.context-target { display: grid; min-height: 100px; place-items: center; border: 1px dashed var(--vp-c-divider); border-radius: 8px; color: var(--vp-c-text-2); user-select: none; }
.collapsible-content { margin-top: 8px; padding: 12px; border: 1px solid var(--vp-c-divider); border-radius: 8px; }
.fixed-target { min-height: 52px; border: 1px dashed var(--vp-c-divider); border-radius: 8px; }
</style>
