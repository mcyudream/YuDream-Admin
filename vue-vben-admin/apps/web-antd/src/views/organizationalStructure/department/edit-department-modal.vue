<script setup lang="ts">
import { useVbenModal } from '@vben/common-ui';
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';

import type {  Department } from '#/types/organizationalStructure/department';
import {computed, watch} from "vue";
import {updateDepartmentApi} from "#/api/core/departmentApi";
import {message} from "ant-design-vue";

const props = defineProps({
  initialValue: {
    type: Object as () => Department,
    required: true,
  }
})

const data = defineModel<Department[]>('department',{default: []});
const getDataLabel = computed(() => {
  return data.value.map((item: Department) => {
    return {
      value: item.id,
      label: item.name
    }
  });
})
const [CreateForm, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  handleSubmit: async (values) => {
    let res = await updateDepartmentApi({
      ...values,
      id: props.initialValue.id
    })
    message.success(res.message)
  },
  layout: 'vertical',
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('form.department.create.name.title'),
      help: $t('form.department.create.name.description'),
      componentProps: {
        placeholder: $t('form.department.create.name.placeholder'),
      },
      defaultValue: props.initialValue.name,
      rules: 'required',
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: $t('form.department.create.description.title'),
      help: $t('form.department.create.description.description'),
      componentProps: {
        placeholder: $t('form.department.create.description.placeholder'),
      },
      defaultValue: props.initialValue.description,

    },
    {
      component: 'Select',
      fieldName: 'parentId',
      label: $t('form.department.create.parentId.title'),
      componentProps: {
        options: getDataLabel,
      },
      defaultValue: props.initialValue.parentId

    },
  ],
});

watch(()=> props.initialValue, () => {
  formApi.setValues(props.initialValue)
})

const emit = defineEmits(['onSubmit']);

const [Modal, modalApi] = useVbenModal<{}>({
  onConfirm: async () => {
    await formApi.submitForm();
    await modalApi.close();
    emit('onSubmit');
  },
});
</script>

<template>
  <Modal title="编辑组织">
    <CreateForm />
  </Modal>
</template>

<style scoped>
</style>
