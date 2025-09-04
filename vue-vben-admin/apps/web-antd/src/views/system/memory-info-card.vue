<script setup lang="ts">
import type {MemoryInfo} from "#/types/systemInfo";
import {$t} from "@vben/locales";
import {Col, Row, Statistic} from "ant-design-vue";

const props = defineProps({
  data: {
    type: Object as () => MemoryInfo,
  }
})
</script>

<template>
  <Row>
    <Col :span="6">
      <Statistic :title="$t('page.system.memoryInfo.totalMemory')"  :value="props.data?.totalMemory??0" :precision="1" suffix="G"></Statistic>

    </Col>
    <Col :span="6">
      <Statistic :title="$t('page.system.memoryInfo.usedMemory')" :value="props.data?.usedMemory??0" :precision="1" suffix="G"></Statistic>

    </Col>
    <Col :span="6">
      <Statistic :title="$t('page.system.memoryInfo.freeMemory')" :value="props.data?.freeMemory??0" :precision="1" suffix="G"></Statistic>
    </Col>
    <Col :span="6">
      <Statistic :title="$t('page.system.memoryInfo.usageRate')" :value="props.data?.usageRate??0" :precision="1" suffix="%"></Statistic>
    </Col>
  </Row>

</template>

<style scoped>

</style>
