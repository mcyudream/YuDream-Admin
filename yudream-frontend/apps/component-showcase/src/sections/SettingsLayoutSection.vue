<script setup lang="ts">
import { useYdToast, YdSettingsLayout, YdSettingsSection } from '@yudream/components'
import { reactive, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

const activeTab = ref('profile')
const tabList = [
  { icon: 'morph:user', label: '个人资料', value: 'profile' },
  { icon: 'morph:shield-check', label: '账号安全', value: 'account' },
  { icon: 'morph:palette', label: '外观', value: 'appearance' },
  { icon: 'morph:bell', label: '通知偏好', value: 'notifications' },
]

const profileForm = reactive({
  username: 'yudream',
  email: '',
  bio: '',
})
const emailOptions = [
  { label: 'admin@yudream.online', value: 'admin@yudream.online' },
  { label: 'dev@yudream.online', value: 'dev@yudream.online' },
]

const accountForm = reactive({
  email: 'admin@yudream.online',
  twoFactor: false,
})

const appearanceForm = reactive({
  theme: 'auto',
  compact: false,
})
const themeOptions = [
  { label: '跟随系统', value: 'auto' },
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' },
]

const notifyForm = reactive({
  comment: true,
  announce: true,
})

const surfaceTab = ref('basic')
const surfaceTabs = [
  { icon: 'morph:settings', label: '基础信息', value: 'basic' },
  { icon: 'morph:bell', label: '通知偏好', value: 'notify' },
]
const surfaceSiteForm = reactive({ siteName: 'YuDream Admin' })
const surfaceNotifyForm = reactive({ announce: true })

function save(title: string) {
  toast.success(`${title}已保存`)
}

const layoutCode = `<YdSettingsLayout
  v-model="active"
  title="个人设置"
  description="管理你的资料、账号安全与界面偏好。"
  :list="[
    { icon: 'morph:user', label: '个人资料', value: 'profile' },
    { icon: 'morph:shield-check', label: '账号安全', value: 'account' },
    { icon: 'morph:palette', label: '外观', value: 'appearance' },
  ]"
>
  <template #profile>
    <YdSettingsSection title="个人资料" description="这些信息会展示给其他用户。">
      <YdForm layout="vertical"><!-- 表单字段 --></YdForm>
      <template #footer>
        <YdButton>保存修改</YdButton>
      </template>
    </YdSettingsSection>
  </template>
  <!-- #account / #appearance 同理 -->
</YdSettingsLayout>`

// surface 模式演示：宿主设置页的真实形态（固定高度外壳 + surface + scroll）
const surfaceCode = `<div class="settings-page-shell"><!-- 或任意固定高度外壳，供 scroll 模式独立滚动 -->
  <YdSettingsLayout
    v-model="active"
    title="系统偏好"
    description="surface 铺白底并自带水平内边距，页头与导航固定，内容区滚动。"
    :list="[
      { icon: 'morph:settings', label: '基础信息', value: 'basic' },
      { icon: 'morph:bell', label: '通知偏好', value: 'notify' },
    ]"
    surface
    scroll
  >
    <template #basic>
      <YdSettingsSection title="基础信息"><!-- 表单 --></YdSettingsSection>
    </template>
    <template #notify>
      <YdSettingsSection title="通知偏好"><!-- 表单 --></YdSettingsSection>
    </template>
  </YdSettingsLayout>
</div>`

// 分区选择器在窄屏使用；见组件 README 的「响应式行为」
const mobileHint = '窄屏（lg 以下）渲染为分区选择器，而不是横向标签；title/description 不传时头部不渲染。宿主设置页用 surface 模式铺满白底替代 YdPageMain，surface 自带 px-5 lg:px-6 水平内边距，窄屏内容不贴边；保存类按钮统一放页头。'
</script>

<template>
  <DemoCard title="YdSettingsLayout" description="默认整块居中限宽：宽屏左侧吸顶分区导航 + 右侧内容，窄屏改为分区选择器（不是横向标签）；分区内部用 YdSettingsSection 排版" :code="layoutCode">
    <YdSettingsLayout
      v-model="activeTab"
      title="个人设置"
      description="管理你的资料、账号安全与界面偏好。"
      :list="tabList"
    >
      <template #profile>
        <YdSettingsSection title="个人资料" description="这些信息会展示给其他用户。">
          <YdForm :model="profileForm" layout="vertical" class="max-w-md">
            <YdFormItem label="用户名" required>
              <YdInput v-model="profileForm.username" />
            </YdFormItem>
            <YdFormItem label="展示邮箱">
              <YdSelect v-model="profileForm.email" :options="emailOptions" placeholder="选择要展示的邮箱" />
              <template #extra>
                <span class="text-xs text-muted-foreground">可以在邮箱设置中管理已验证的邮箱地址。</span>
              </template>
            </YdFormItem>
            <YdFormItem label="简介">
              <YdTextarea v-model="profileForm.bio" :rows="3" placeholder="介绍一下自己" />
            </YdFormItem>
          </YdForm>
          <template #footer>
            <YdButton @click="save('个人资料')">
              <YdIcon name="morph:save" />
              保存修改
            </YdButton>
          </template>
        </YdSettingsSection>
      </template>

      <template #account>
        <YdSettingsSection title="账号安全" description="管理登录邮箱与两步验证。">
          <YdForm :model="accountForm" layout="vertical" class="max-w-md">
            <YdFormItem label="登录邮箱">
              <YdInput v-model="accountForm.email" />
            </YdFormItem>
            <YdFormItem label="两步验证">
              <YdSwitch v-model="accountForm.twoFactor" />
              <template #extra>
                <span class="text-xs text-muted-foreground">开启后登录需要输入动态验证码。</span>
              </template>
            </YdFormItem>
          </YdForm>
          <template #footer>
            <YdButton @click="save('账号安全')">
              保存修改
            </YdButton>
          </template>
        </YdSettingsSection>
      </template>

      <template #appearance>
        <YdSettingsSection title="外观" description="界面主题与显示密度。">
          <YdForm :model="appearanceForm" layout="vertical" class="max-w-md">
            <YdFormItem label="主题">
              <YdSelect v-model="appearanceForm.theme" :options="themeOptions" />
            </YdFormItem>
            <YdFormItem label="紧凑密度">
              <YdSwitch v-model="appearanceForm.compact" />
            </YdFormItem>
          </YdForm>
          <template #footer>
            <YdButton @click="save('外观')">
              保存修改
            </YdButton>
          </template>
        </YdSettingsSection>
      </template>

      <template #notifications>
        <YdSettingsSection title="通知偏好" description="选择你希望收到的通知类型。">
          <YdForm :model="notifyForm" layout="vertical" class="max-w-md">
            <YdFormItem label="评论与回复">
              <YdSwitch v-model="notifyForm.comment" />
            </YdFormItem>
            <YdFormItem label="系统公告">
              <YdSwitch v-model="notifyForm.announce" />
            </YdFormItem>
          </YdForm>
          <template #footer>
            <YdButton @click="save('通知偏好')">
              保存修改
            </YdButton>
          </template>
        </YdSettingsSection>
      </template>
    </YdSettingsLayout>
  </DemoCard>

  <DemoCard title="YdSettingsLayout surface" description="页面级铺满（宿主设置页形态）：surface 铺白底并自带水平内边距，窄屏内容不再贴可视区边缘；scroll 模式页头与分区导航固定、内容区独立滚动" :code="surfaceCode">
    <div class="border rounded-lg flex flex-col h-[400px] overflow-hidden">
      <YdSettingsLayout
        v-model="surfaceTab"
        title="系统偏好"
        description="surface + scroll 模式的页面形态。"
        :list="surfaceTabs"
        surface
        scroll
      >
        <template #basic>
          <YdSettingsSection title="基础信息" description="白底与水平留白都由 surface 模式自带。">
            <YdForm :model="surfaceSiteForm" layout="vertical" class="max-w-md">
              <YdFormItem label="站点名称">
                <YdInput v-model="surfaceSiteForm.siteName" />
              </YdFormItem>
            </YdForm>
          </YdSettingsSection>
        </template>

        <template #notify>
          <YdSettingsSection title="通知偏好" description="选择你希望收到的系统通知。">
            <YdForm :model="surfaceNotifyForm" layout="vertical" class="max-w-md">
              <YdFormItem label="系统公告">
                <YdSwitch v-model="surfaceNotifyForm.announce" />
              </YdFormItem>
            </YdForm>
          </YdSettingsSection>
        </template>
      </YdSettingsLayout>
    </div>
  </DemoCard>

  <DemoCard title="YdSettingsSection" description="分区排版可独立使用：标题 + 说明 + 分隔线 + 内容 + 底部操作栏">
    <YdSettingsSection title="站点公告" description="不依赖设置页骨架，任意页面都能用。">
      <div class="text-sm text-muted-foreground">
        任意内容，比如一段说明文字或一个表单。
      </div>
      <template #footer>
        <YdButton variant="outline">
          取消
        </YdButton>
        <YdButton>
          确认
        </YdButton>
      </template>
    </YdSettingsSection>
  </DemoCard>

  <p class="text-xs text-muted-foreground -mt-2">
    {{ mobileHint }}
  </p>
</template>
