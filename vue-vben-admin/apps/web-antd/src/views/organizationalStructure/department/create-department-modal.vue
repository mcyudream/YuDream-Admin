<script setup lang="ts">
import { useVbenModal } from '@vben/common-ui';
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';

import { createDepartmentApi } from '#/api/core/departmentApi';
import type { CreateDepartment, Department } from '#/types/organizationalStructure/department';
import {computed} from "vue";

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
    const department: CreateDepartment = {
      ...values,
    };
    const res = await createDepartmentApi(department);
    console.log(res);
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
    },
    {
      component: 'Select',
      fieldName: 'parentId',
      label: $t('form.department.create.parentId.title'),
      componentProps: {
        options: getDataLabel,
      },
    },
  ],
});

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
  <Modal title="创建组织">
    <CreateForm />
  </Modal>
</template>

<style scoped>
</style>
