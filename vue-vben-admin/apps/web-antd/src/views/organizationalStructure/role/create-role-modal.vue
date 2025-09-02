<script setup lang="ts">
import { useVbenModal } from '@vben/common-ui';
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';
import {createRoleApi} from "#/api/core/role";
import {message} from "ant-design-vue";

const [CreateForm, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  handleSubmit: async (values) => {
    const res = await createRoleApi({
      ...values,
    })
    // @ts-ignore
    message.success(res.message)

  },
  layout: 'vertical',
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'id',
      label: $t('form.role.create.id.title'),
      help: $t('form.role.create.id.description'),
      componentProps: {
        placeholder: $t('form.role.create.id.placeholder'),
      },
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: $t('form.role.create.name.title'),
      help: $t('form.role.create.name.description'),
      componentProps: {
        placeholder: $t('form.role.create.name.placeholder'),
      },
      rules: 'required',
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: $t('form.role.create.description.title'),
      help: $t('form.role.create.description.description'),
      componentProps: {
        placeholder: $t('form.role.create.description.placeholder'),
      },
    },{
      component: 'InputNumber',
      fieldName: 'level',
      label: $t('form.role.create.level.title'),
      help: $t('form.role.create.level.description'),
      componentProps: {
        placeholder: $t('form.role.create.level.placeholder'),
      },
      defaultValue: 3,
      rules: 'required',
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
  <Modal title="创建角色">
    <CreateForm />
  </Modal>
</template>

<style scoped>
</style>
