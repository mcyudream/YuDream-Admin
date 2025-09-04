<script setup lang="ts">
import type {JavaVmInfo} from "#/types/systemInfo";
import {Descriptions, DescriptionsItem} from "ant-design-vue";
import {$t} from "@vben/locales";

const props = defineProps({
  data: {
    type: Object as () => JavaVmInfo,
  }
})
</script>

<template>
  <Descriptions :column="1" >
    <DescriptionsItem :label="$t('page.system.javaVmInfo.javaName')">
      {{ props.data?.javaName }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.javaVmInfo.javaVersion')">
      {{ props.data?.javaVersion }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.javaVmInfo.startTime')">
      {{ props.data?.startTime }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.javaVmInfo.installPath')">
      {{ props.data?.installPath }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.javaVmInfo.runParameters')">
      {{ props.data?.runParameters }}
    </DescriptionsItem>
  </Descriptions>
</template>

<style scoped>

</style>
