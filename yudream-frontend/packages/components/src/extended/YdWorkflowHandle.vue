<script setup lang="ts" generic="TData = Record<string, unknown>">
import type { HandleConnectable } from '@vue-flow/core'
import type { YdWorkflowHandleConnectable, YdWorkflowHandleType, YdWorkflowPosition } from './workflow-canvas-model'
import { Position as FlowPosition, Handle } from '@vue-flow/core'

defineOptions({ name: 'YdWorkflowHandle', inheritAttrs: false })

const props = withDefaults(defineProps<{
  id?: string
  type?: YdWorkflowHandleType
  position?: YdWorkflowPosition
  connectable?: YdWorkflowHandleConnectable<TData>
  connectableStart?: boolean
  connectableEnd?: boolean
}>(), {
  type: 'source',
  position: 'right',
  connectable: true,
})
const positionMap: Record<YdWorkflowPosition, FlowPosition> = {
  left: FlowPosition.Left,
  top: FlowPosition.Top,
  right: FlowPosition.Right,
  bottom: FlowPosition.Bottom,
}
</script>

<template>
  <Handle v-bind="$attrs" :id="id" aria-hidden="true" :type="type" :position="positionMap[props.position || 'right']" :connectable="props.connectable as HandleConnectable" :connectable-start="props.connectableStart" :connectable-end="props.connectableEnd" />
</template>
