<script setup lang="ts">
import type { YdFormErrors, YdFormFieldData, YdFormProps, YdFormSubmitData } from './types'
import { Form as AForm } from '@arco-design/web-vue'
import { computed, useId, useTemplateRef } from 'vue'

defineOptions({ name: 'YdForm', inheritAttrs: false })
const props = withDefaults(defineProps<YdFormProps>(), {
  layout: 'vertical',
  labelAlign: 'left',
  disabled: undefined,
})
const emits = defineEmits<{
  submit: [data: YdFormSubmitData, event: Event]
  submitSuccess: [values: Record<string, unknown>, event: Event]
  submitFailed: [data: YdFormSubmitData, event: Event]
}>()
const formRef = useTemplateRef<InstanceType<typeof AForm>>('formRef')
const automaticId = useId()
const formId = computed(() => props.id ?? `yd-form-${automaticId}`)

defineExpose({
  validate: (callback?: (errors: YdFormErrors | undefined) => void): Promise<YdFormErrors | undefined> => {
    if (!formRef.value) {
      return Promise.reject(new Error('YD 表单尚未挂载'))
    }
    return formRef.value.validate(callback)
  },
  validateField: (field: string | string[], callback?: (errors: YdFormErrors | undefined) => void): Promise<YdFormErrors | undefined> => {
    if (!formRef.value) {
      return Promise.reject(new Error('YD 表单尚未挂载'))
    }
    return formRef.value.validateField(field, callback)
  },
  resetFields: (field?: string | string[]) => formRef.value?.resetFields(field),
  clearValidate: (field?: string | string[]) => formRef.value?.clearValidate(field),
  setFields: (data: Record<string, YdFormFieldData>) => formRef.value?.setFields(data),
  scrollToField: (field: string) => formRef.value?.scrollToField(field),
})
</script>

<template>
  <AForm
    v-bind="{ ...$attrs, ...props }"
    :id="formId"
    ref="formRef"
    class="yd-form" :class="[props.class]"
    @submit="(data, event) => emits('submit', data, event)"
    @submit-success="(values, event) => emits('submitSuccess', values, event)"
    @submit-failed="(data, event) => emits('submitFailed', data, event)"
  >
    <slot />
  </AForm>
</template>

<style src="./ui.css"></style>
