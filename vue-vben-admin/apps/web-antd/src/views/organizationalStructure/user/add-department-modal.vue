<script setup lang="ts">
import {useVbenModal} from "@vben/common-ui";
import {ref} from 'vue'
import DepartmentTree from "#/views/organizationalStructure/user/department-tree.vue";
import {message} from "ant-design-vue";
import {$t} from "@vben/locales";
const departmentId = ref<string>('');
const emits = defineEmits(['onSubmit'])

const [Modal, modalApi] = useVbenModal({
  onConfirm: () => {
    if (departmentId.value === '') {
      message.error($t('请选择一个部门'))
      return
    }
    emits("onSubmit", departmentId.value );
    modalApi.close()
  }
})



</script>

<template>
  <Modal>
    <department-tree  :show-all="false" v-model:current-id="departmentId"></department-tree>
  </Modal>
</template>

<style scoped>

</style>
