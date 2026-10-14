<script setup lang="ts">
import eventBus from '@/utils/eventBus'

defineOptions({
  name: 'AppSystemInfo',
})

const isShow = ref(false)

const { pkg, lastBuildTime } = __SYSTEM_INFO__

onMounted(() => {
  eventBus.on('global-system-info-open', () => {
    isShow.value = true
  })
})

onUnmounted(() => {
  eventBus.off('global-system-info-open')
})
</script>

<template>
  <YdDrawer v-model="isShow" title="系统信息" :footer="false">
    <div v-if="pkg.version">
      <YdDivider>
        版本号
      </YdDivider>
      <div class="text-lg font-bold font-sans text-center">
        {{ pkg.version }}
      </div>
    </div>
    <div>
      <YdDivider>
        最后编译时间
      </YdDivider>
      <div class="text-lg font-bold font-sans text-center">
        {{ lastBuildTime }}
      </div>
    </div>
    <div>
      <YdDivider>
        生产环境依赖
      </YdDivider>
      <ul class="text-sm list-none">
        <li v-for="(val, key) in (pkg.dependencies as object)" :key="key" class="px-2 py-1.5 rounded-lg flex items-center justify-between hover-bg-secondary">
          <div class="font-bold">
            {{ key }}
          </div>
          <div class="font-sans">
            {{ val }}
          </div>
        </li>
      </ul>
    </div>
    <div>
      <YdDivider>
        开发环境依赖
      </YdDivider>
      <ul class="text-sm list-none">
        <li v-for="(val, key) in (pkg.devDependencies as object)" :key="key" class="px-2 py-1.5 rounded-lg flex items-center justify-between hover-bg-secondary">
          <div class="font-bold">
            {{ key }}
          </div>
          <div class="font-sans">
            {{ val }}
          </div>
        </li>
      </ul>
    </div>
  </YdDrawer>
</template>
