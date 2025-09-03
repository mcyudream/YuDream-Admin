<script lang="ts" setup>
import { useVbenDrawer } from '@vben/common-ui';
import type {User} from "#/types/user";
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';
import { z } from '#/adapter/form';
import {message} from "ant-design-vue";
import {watch} from "vue";
import {editUserApi} from "#/api";

const props = defineProps({
  initialValue: {
    type: Object as () => User,
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
    const res = await editUserApi({
      ...props.initialValue,
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
      fieldName: 'nickname',
      label: $t('form.user.edit.nickname.title'),
      help: $t('form.user.edit.nickname.description'),
      componentProps: {
        placeholder: $t('form.user.edit.nickname.placeholder'),
      },
      rules: 'required',
      defaultValue: props.initialValue.nickname,
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: $t('form.user.edit.email.title'),
      help: $t('form.user.edit.email.description'),
      componentProps: {
        placeholder: $t('form.user.edit.email.placeholder'),
      },
      rules: z.string().email(),
      defaultValue: props.initialValue.email,
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: $t('form.user.edit.phone.title'),
      help: $t('form.user.edit.phone.description'),
      componentProps: {
        placeholder: $t('form.user.edit.phone.placeholder'),
      },
      rules: z.string().min(11).max(11),
      defaultValue: props.initialValue.phone,
    }
  ],
});

watch(()=> props.initialValue, () => {
  formApi.setValues(props.initialValue)
})
const emit = defineEmits(['onSubmit']);



const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: async () => {
    await formApi.validate().then(
       async () => {
          await formApi.submitForm()
          await drawerApi.close();
          emit('onSubmit');
        }
    ).catch(e => {
      message.error(e.message)
    })
  }
});
</script>
<template>
  <Drawer title="编辑用户">
    <edit-form/>
  </Drawer>
</template>
