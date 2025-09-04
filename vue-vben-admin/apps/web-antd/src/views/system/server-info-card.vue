<script setup lang="ts">
import type {ServerInfo} from "#/types/systemInfo";
import {Descriptions, DescriptionsItem} from "ant-design-vue";
import {$t} from "@vben/locales";

const props = defineProps({
  data: {
    type: Object as () => ServerInfo,
  }
})
</script>

<template>
  <Descriptions :column="1" >
    <DescriptionsItem :label="$t('page.system.serverInfo.osName')">
      {{ props.data?.osName }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.serverInfo.serverName')">
      {{ props.data?.serverName }}
    </DescriptionsItem>
    <DescriptionsItem :label="$t('page.system.serverInfo.serverIp')">
      {{ props.data?.serverIp }}
    </DescriptionsItem>
  </Descriptions>
</template>

<style scoped>

</style>
