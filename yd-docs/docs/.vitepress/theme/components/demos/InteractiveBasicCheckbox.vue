<script setup lang="ts">
import { ref } from 'vue'
import { FaCheckbox } from '@yudream/components'

const checked = ref(false)
const indeterminate = ref<'indeterminate'>('indeterminate')
const changedValue = ref('false')
</script>

<template>
  <div class="demo-row">
    <FaCheckbox v-model="checked" @change="value => changedValue = String(value)">同意协议</FaCheckbox>
    <FaCheckbox v-model="indeterminate">部分选中</FaCheckbox>
    <FaCheckbox :model-value="true" disabled>禁用已选中</FaCheckbox>
    <span>change：{{ changedValue }}</span>
  </div>
</template>
