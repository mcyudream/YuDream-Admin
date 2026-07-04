<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { cn } from '@/utils'
import eventBus from '@/utils/eventBus'
import DeptRoleSwitch from './DeptRoleSwitch/index.vue'
import Profile from './profile.vue'

defineOptions({
  name: 'AppAccountButton',
})

const props = withDefaults(defineProps<{
  onlyAvatar?: boolean
  dropdownAlign?: 'start' | 'center' | 'end'
  dropdownSide?: 'left' | 'right' | 'top' | 'bottom'
  buttonVariant?: 'secondary' | 'ghost'
  class?: HTMLAttributes['class']
}>(), {
  dropdownAlign: 'end',
  dropdownSide: 'right',
  buttonVariant: 'ghost',
})

const router = useRouter()
const appSettingsStore = useAppSettingsStore()
const appAccountStore = useAppAccountStore()
const { generateTitle } = useAppMenu()

const profileModal = useFaModal().create({
  alignCenter: true,
  header: false,
  footer: false,
  closeOnClickOverlay: false,
  closeOnPressEscape: false,
  class: 'profile-account-modal overflow-hidden',
  contentClass: 'min-h-full p-0 flex',
  content: () => h(Profile),
})

const deptRoleSwitchModal = useFaModal().create({
  alignCenter: true,
  header: false,
  footer: false,
  class: 'overflow-hidden',
  contentClass: 'p-0',
  content: () => h(DeptRoleSwitch),
})

const dropdownItems = computed(() => [
  [
    ...(appAccountStore.isImpersonating
      ? [{
          label: `\u9000\u51fa\u4f2a\u88c5\uff08${appAccountStore.impersonatorAccount}\uff09`,
          icon: 'i-ri:logout-circle-r-line',
          handle: () => appAccountStore.exitImpersonation(),
        }]
      : []),
    ...(appSettingsStore.settings.app.home.enable
      ? [{ label: generateTitle(appSettingsStore.settings.app.home.title), icon: 'i-mdi:home', handle: () => router.push({ path: appSettingsStore.settings.app.home.fullPath }) }]
      : []),
    { label: '\u5207\u6362\u90e8\u95e8/\u89d2\u8272', icon: 'i-mdi:swap-horizontal', handle: () => deptRoleSwitchModal.open() },
    { label: '\u4e2a\u4eba\u8bbe\u7f6e', icon: 'i-mdi:account', handle: () => profileModal.open() },
  ],
  [
    ...(appSettingsStore.mode === 'pc'
      ? [{ label: '\u5feb\u6377\u952e', icon: 'i-mdi:keyboard', handle: () => eventBus.emit('global-hotkeys-intro-toggle') }]
      : []),
  ],
  [
    {
      label: '\u9000\u51fa\u767b\u5f55',
      icon: 'i-mdi:logout',
      handle: () => appAccountStore.logout(appSettingsStore.settings.app.home.fullPath),
    },
  ],
])
</script>

<template>
  <FaDropdown :align="dropdownAlign" :side="dropdownSide" :items="dropdownItems" class="flex-center">
    <template #header>
      <div class="flex-center-start gap-2">
        <FaAvatar :src="appAccountStore.avatar" :fallback="appAccountStore.account.slice(0, 2)" shape="square" />
        <div class="min-w-0 space-y-1">
          <div class="text-base lh-none truncate">
            {{ appAccountStore.account }}
          </div>
          <div class="text-xs text-secondary-foreground/50 font-normal truncate">
            {{ appAccountStore.currentDept?.name || '\u672a\u9009\u62e9\u90e8\u95e8' }} / {{ appAccountStore.currentRole?.name || '\u672a\u9009\u62e9\u89d2\u8272' }}
          </div>
          <div v-if="appAccountStore.isImpersonating" class="text-xs text-warning font-normal truncate">
            {{ '\u6b63\u5728\u4f2a\u88c5\u8bbf\u95ee' }}
          </div>
        </div>
      </div>
    </template>
    <FaButton
      :variant="buttonVariant" size="icon-sm" :class="cn('flex-center gap-1 p-2', {
        'p-1': onlyAvatar,
      }, props.class)"
    >
      <FaAvatar :src="appAccountStore.avatar" :class="cn('size-6', { 'size-full': onlyAvatar })">
        <FaIcon name="i-carbon:user-avatar-filled" class="text-secondary-foreground/50 size-6" />
      </FaAvatar>
      <div v-if="!onlyAvatar" class="flex-center-between flex-1 gap-2 min-w-0">
        <div class="text-start flex-1 truncate">
          {{ appAccountStore.account }}
        </div>
        <FaIcon name="i-material-symbols:expand-all-rounded" />
      </div>
    </FaButton>
  </FaDropdown>
</template>

<style scoped>
:global(.profile-account-modal) {
  width: min(1080px, calc(100vw - 48px)) !important;
  max-width: min(1080px, calc(100vw - 48px)) !important;
}

:global(.profile-account-modal [data-slot="dialog-content"]) {
  width: 100%;
}
</style>
