<script setup lang="ts" generic="T extends object">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import FaButton from '../basic/button/index.vue'
import FaInput from '../basic/input/index.vue'
import FaPopover from '../basic/popover/index.vue'
import YdEmpty from './YdEmpty.vue'
import './extended.css'

const props = withDefaults(defineProps<{
  uniqueKey: string
  boxId: string
  data: T
  title?: string
  limit?: number
}>(), { title: '我的预设', limit: 20 })
const emit = defineEmits<{
  takeOut: [
        value: T,
  ]
  error: [
        error: unknown,
  ]
}>()
const items = shallowRef<{
  name: string
  data: T
}[]>([])
const name = ref('')
const storageKey = computed(() => `yd-storage:${encodeURIComponent(props.uniqueKey)}:${encodeURIComponent(props.boxId)}`)
function load(key: string) {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(key) || '[]')
    items.value = Array.isArray(value) ? value.filter(item => item && typeof item.name === 'string' && item.data && typeof item.data === 'object').slice(0, props.limit) : []
  }
  catch (error) {
    items.value = []
    emit('error', error)
  }
}
onMounted(() => load(storageKey.value))
watch(storageKey, load)
function save() {
  if (!name.value.trim() || items.value.length >= props.limit) {
    return
  }
  try {
    const next = [...items.value, { name: name.value.trim(), data: JSON.parse(JSON.stringify(props.data)) as T }]
    localStorage.setItem(storageKey.value, JSON.stringify(next))
    items.value = next
    name.value = ''
  }
  catch (error) {
    emit('error', error)
  }
}
function remove(index: number) {
  try {
    const next = items.value.filter((_, i) => i !== index)
    localStorage.setItem(storageKey.value, JSON.stringify(next))
    items.value = next
  }
  catch (error) {
    emit('error', error)
  }
}
</script>

<template>
  <FaPopover>
    <slot>
      <FaButton variant="outline" class="yd-control">
        {{ title }}
      </FaButton>
    </slot>
    <template #panel>
      <div class="yd-stack">
        <strong>{{ title }}</strong><div v-for="(item, index) in items" :key="index" class="yd-row">
          <FaButton variant="ghost" @click="emit('takeOut', JSON.parse(JSON.stringify(item.data)))">
            {{ item.name }}
          </FaButton><FaButton variant="ghost" size="sm" :aria-label="`删除预设 ${item.name}`" @click="remove(index)">
            删除
          </FaButton>
        </div><YdEmpty v-if="!items.length" title="尚无预设" /><form class="yd-row" @submit.prevent="save">
          <FaInput v-model="name" placeholder="预设名称" aria-label="预设名称" :maxlength="60" /><FaButton type="submit" :disabled="!name.trim() || items.length >= limit">
            保存
          </FaButton>
        </form><small class="yd-muted">{{ items.length }} / {{ limit }} · 当前账号本机保存</small>
      </div>
    </template>
  </FaPopover>
</template>
