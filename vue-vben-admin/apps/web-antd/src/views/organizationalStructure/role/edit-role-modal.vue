<script setup lang="ts">
import { useVbenModal } from '@vben/common-ui';
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';
import {editRoleApi} from "#/api/core/role";
import {message} from "ant-design-vue";
import type {Role} from "#/types/organizationalStructure/role";
import {watch} from "vue";

const props = defineProps({
  initialValue: {
    type: Object as () => Role,
    required: true,
  }
})

const [EditForm, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  handleSubmit: async (values) => {
    const res = await editRoleApi({
      ...values
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
      defaultValue: props.initialValue.id,

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
      defaultValue: props.initialValue.name,

    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: $t('form.role.create.description.title'),
      help: $t('form.role.create.description.description'),
      componentProps: {
        placeholder: $t('form.role.create.description.placeholder'),
      },
      defaultValue: props.initialValue.description,

    },{
      component: 'InputNumber',
      fieldName: 'level',
      label: $t('form.role.create.level.title'),
      help: $t('form.role.create.level.description'),
      componentProps: {
        placeholder: $t('form.role.create.level.placeholder'),
      },
      defaultValue: props.initialValue.level,
      rules: 'required',
    }
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
  <Modal title="编辑角色">
    <edit-form />
  </Modal>
</template>

<style scoped>
</style>
