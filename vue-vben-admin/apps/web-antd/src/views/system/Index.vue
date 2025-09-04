<script setup lang="ts">
import CpuInfoCard from "#/views/system/cpu-info-card.vue";

import {AnalysisChartCard} from '@vben/common-ui';
import type {SystemInfo} from "#/types/systemInfo";
import {onMounted, ref} from "vue";
import {getSystemInfoApi} from "#/api/core/system";
import {$t} from "@vben/locales";
import MemoryInfoCard from "#/views/system/memory-info-card.vue";
import ServerInfoCard from "#/views/system/server-info-card.vue";
import JavaVmInfoCard from "#/views/system/java-vm-info-card.vue";
import DiskInfoCard from "#/views/system/disk-info-card.vue";

const systemInfo = ref<SystemInfo>({})

const getSystemInfo = async () => {
  systemInfo.value = await getSystemInfoApi()

}

onMounted(() => {
  getSystemInfo()
})


</script>

<template>
  <div class="p-5">

    <div class="mt-5 w-full md:flex flex-wrap gap-5">
      <AnalysisChartCard class="md:mr-4 flex-1" :title="$t('page.system.cpuInfo.title')">
        <cpu-info-card :data="systemInfo.cpuInfo"/>
      </AnalysisChartCard>
      <AnalysisChartCard class="md:mr-4 flex-1" :title="$t('page.system.memoryInfo.title')">
        <memory-info-card :data="systemInfo.memoryInfo"/>
      </AnalysisChartCard>
      <AnalysisChartCard class="md:mr-4" :title="$t('page.system.serverInfo.title')">
        <server-info-card :data="systemInfo.serverInfo"/>
      </AnalysisChartCard>
      <AnalysisChartCard class="md:mr-4" :title="$t('page.system.javaVmInfo.title')">
        <java-vm-info-card :data="systemInfo.javaVmInfo"/>
      </AnalysisChartCard>
      <AnalysisChartCard class="md:mr-4" :title="$t('page.system.javaVmInfo.title')">
        <disk-info-card :data="systemInfo.diskInfo"/>
      </AnalysisChartCard>
    </div>
  </div>
</template>

<style scoped>

</style>
