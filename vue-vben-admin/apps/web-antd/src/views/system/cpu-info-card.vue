<script lang="ts" setup>
import type {CpuInfo} from "#/types/systemInfo";
import {Statistic, Row, Col} from "ant-design-vue";
import {$t} from "@vben/locales";

const props = defineProps({
  data: {
    type: Object as () => CpuInfo,

  }
})
</script>

<template>
  <Row>
    <Col :span="6">
      <Statistic :title="$t('page.system.cpuInfo.coreCount')"  :value="props.data?.coreCount??0"></Statistic>

    </Col>
    <Col :span="6">
    <Statistic :title="$t('page.system.cpuInfo.userUsageRate')" :value="props.data?.userUsageRate??0" :precision="1" suffix="%"></Statistic>

    </Col>
    <Col :span="6">
    <Statistic :title="$t('page.system.cpuInfo.systemUsageRate')" :value="props.data?.systemUsageRate??0" :precision="1" suffix="%"></Statistic>
    </Col>
    <Col :span="6">
      <Statistic :title="$t('page.system.cpuInfo.free')" :value="props.data?.free??0" :precision="1" suffix="%"></Statistic>
    </Col>
  </Row>

</template>
