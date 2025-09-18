<script setup lang="ts">


import {useVbenModal} from "@vben/common-ui";
import {onMounted, reactive, ref, watch} from "vue";
import {RadioGroup, Radio} from "ant-design-vue";
const props = defineProps({
  dataRange: {
    type: String,
    default: "SELF",
  }
})
const emit = defineEmits(['onSubmit']);
const dataRange = ref('SELF')
const dataRangeList = [
  {label: "个人", value: "SELF"},
  {label: "部门", value: "DEPARTMENT"},
  {label: "部门和子部门", value: "DEPARTMENT_AND_CHILDREN"},
  {label: "全部", value: "ALL"},
]
const [DataRangeSelectModal,dataRangeSelectApi] = useVbenModal({
  onConfirm: () => {
    emit("onSubmit", dataRange.value);
    dataRangeSelectApi.close()
  }
});
const radioStyle = reactive({
  display: 'flex',
  height: '30px',
  lineHeight: '30px',
});
watch(()=>props.dataRange, ()=>{
  dataRange.value = props.dataRange;
})
onMounted(() => {
  dataRange.value = props.dataRange;
})
</script>

<template>
  <DataRangeSelectModal title="修改角色数据范围">
    <RadioGroup v-model:value="dataRange">
      <Radio v-for="item in dataRangeList" :style="radioStyle" :value="item.value">{{item.label}}</Radio>
    </RadioGroup>
  </DataRangeSelectModal>
</template>

<style scoped>

</style>
