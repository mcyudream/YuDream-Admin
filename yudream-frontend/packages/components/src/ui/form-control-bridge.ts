import type { YdFormFieldBindings } from './types'
import { formItemInjectionKey } from '@arco-design/web-vue/es/form/context'
import { computed, defineComponent, inject, nextTick } from 'vue'
import { bindYdFormControls } from './form-controls'

/** Runs inside the engine's FormItem so its reactive context is available. */
export const FormControlBridge = defineComponent({
  name: 'YdFormControlBridge',
  props: { controlId: { type: String, required: true }, descriptionId: { type: String, required: true } },
  setup(props, { slots }) {
    const context = inject(formItemInjectionKey)
    const bindings = computed<YdFormFieldBindings>(() => ({
      'id': props.controlId,
      'disabled': context?.disabled ?? false,
      'aria-invalid': context?.error ?? false,
      'aria-describedby': props.descriptionId,
      'onUpdate:modelValue': () => nextTick(() => context?.eventHandlers.onChange?.()),
      'onInput': () => context?.eventHandlers.onInput?.(),
      'onChange': () => nextTick(() => context?.eventHandlers.onChange?.()),
      'onBlur': () => context?.eventHandlers.onBlur?.(),
      'onFocus': () => context?.eventHandlers.onFocus?.(),
    }))
    return () => {
      const children = slots.default?.({ fieldProps: bindings.value }) ?? []
      // Direct YD controls receive validation events, accessible labels and state.
      // Nested layouts explicitly use #default="{ fieldProps }" and v-bind.
      return bindYdFormControls(children, bindings.value)
    }
  },
})
