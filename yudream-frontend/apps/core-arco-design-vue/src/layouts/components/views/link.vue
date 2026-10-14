<script setup lang="ts">
import { useClipboard } from '@vueuse/core'
import { safeExternalLink } from '@/utils/menu-link'

defineOptions({
  name: 'LinkView',
})

const route = useRoute()
const { copy, copied } = useClipboard()
watch(copied, (val) => {
  val && useYdToast().success('复制成功')
})

function open() {
  const target = safeExternalLink(route.meta.link)
  if (target) {
    window.open(target, '_blank', 'noopener,noreferrer')
  }
}
</script>

<template>
  <div class="flex flex-col h-full w-full absolute">
    <Transition name="fade" mode="out-in" appear>
      <YdPageMain :key="route.meta.link" class="flex flex-1 flex-col justify-center">
        <div class="flex flex-col items-center">
          <YdIcon name="morph:earth" class="text-primary/80 size-30" />
          <div class="text-xl text-dark my-2 dark-text-white">
            是否访问此链接
          </div>
          <YdAction type="button" class="block w-full text-[14px] text-secondary-foreground/50 my-2 text-center max-w-[300px] cursor-pointer" @click="route.meta.link && copy(route.meta.link)">
            <YdTooltip text="复制链接">
              <div class="line-clamp-3">
                {{ route.meta.link }}
              </div>
            </YdTooltip>
          </YdAction>
          <YdButton class="my-4" @click="open">
            <YdIcon name="morph:external-link" />
            立即访问
          </YdButton>
        </div>
      </YdPageMain>
    </Transition>
  </div>
</template>

<style scoped>
.fade-enter-active {
  transition: 0.2s;
}

.fade-leave-active {
  transition: 0.15s;
}

.fade-enter-from {
  opacity: 0;
}

.fade-leave-to {
  opacity: 0;
}
</style>
