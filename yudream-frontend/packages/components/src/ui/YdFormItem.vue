<script setup lang="ts">
import type { YdFormItemProps } from './types'
import { FormItem as AFormItem } from '@arco-design/web-vue'
import { formInjectionKey } from '@arco-design/web-vue/es/form/context'
import { computed, inject, useId } from 'vue'
import { FormControlBridge } from './form-control-bridge'
import { isYdFormLabelFallbackTarget } from './form-controls'

defineOptions({ name: 'YdFormItem', inheritAttrs: false })
const props = withDefaults(defineProps<YdFormItemProps>(), { disabled: undefined, validateTrigger: 'change', contentFlex: true })
const context = inject(formInjectionKey)
const automaticId = useId()
const controlId = computed(() => props.controlId ?? `${context?.id ?? 'yd-field'}-${props.field?.replace(/[^\w-]/g, '_') ?? automaticId}-control`)
const descriptionId = computed(() => `${controlId.value}-description`)
function focusCompositeControl() {
  const target = document.getElementById(controlId.value)
  if (target && isYdFormLabelFallbackTarget(target)) {
    target.focus()
  }
}
</script>

<template>
  <AFormItem
    v-bind="{ ...$attrs, ...props }"
    class="yd-form-item" :class="[props.class]"
    :label-attrs="{ for: controlId, onClick: focusCompositeControl }"
  >
    <FormControlBridge :control-id="controlId" :description-id="descriptionId">
      <template #default="{ fieldProps }">
        <slot :field-props="fieldProps" />
      </template>
    </FormControlBridge>
    <template v-if="$slots.label" #label>
      <slot name="label" />
    </template>
    <template v-if="$slots.help" #help>
      <slot name="help" />
    </template>
    <template #extra>
      <span :id="descriptionId"><slot name="extra">{{ props.extra }}</slot></span>
    </template>
  </AFormItem>
</template>

<style src="./ui.css"></style>
