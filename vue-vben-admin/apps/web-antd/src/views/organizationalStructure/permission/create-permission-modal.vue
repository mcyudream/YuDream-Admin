<script setup lang="ts">
import { useVbenModal } from '@vben/common-ui';
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';
import {message} from "ant-design-vue";
import {addPermissionApi} from "#/api/core/permission";

const [CreateForm, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  handleSubmit: async (values) => {
    const res = await addPermissionApi({
      ...values,
    })
    // @ts-ignore
    message.success(res.message)
  },
  layout: 'vertical',
  showDefaultActions: false,
  schema: [
    {
      component:"Input",
      fieldName:"id",
      label: $t('form.permission.create.id.title'),
      help: $t('form.permission.create.id.description'),
      componentProps: {
        placeholder: $t('form.permission.create.id.placeholder'),
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('form.permission.create.name.title'),
      help: $t('form.permission.create.name.description'),
      componentProps: {
        placeholder: $t('form.permission.create.name.placeholder'),
      },
      rules: 'required',
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: $t('form.permission.create.description.title'),
      help: $t('form.permission.create.description.description'),
      componentProps: {
        placeholder: $t('form.permission.create.description.placeholder'),
      },
    }
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
  <Modal title="新增权限">
    <CreateForm />
  </Modal>
</template>

<style scoped>
</style>
