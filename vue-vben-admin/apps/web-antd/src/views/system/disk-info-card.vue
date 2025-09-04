<script setup lang="ts">
import type {DiskInfo} from "#/types/systemInfo";
import {Descriptions, DescriptionsItem} from "ant-design-vue";
import {$t} from "@vben/locales";

const props = defineProps({
  data: Array as () => Array<DiskInfo>,
})
</script>

<template>
  <Descriptions  :title="item.diskPath" v-for="item in data">
    <DescriptionsItem :label="$t('page.system.diskInfo.diskType')">
        {{item.diskType}}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.diskInfo.fileSystem')">
        {{item.fileSystem}}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.diskInfo.availableSize')">
      {{item.availableSize}}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.diskInfo.totalSize')">
        {{item.totalSize}}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.diskInfo.usedPercentage')">
      {{item.usedPercentage.toFixed(2)}}%
    </DescriptionsItem>
  </Descriptions>
</template>

<style scoped>

</style>
