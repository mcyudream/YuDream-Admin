<script setup lang="ts">
import type { LatLngExpression, Map as LeafletMap, LeafletMouseEvent, TileLayerOptions } from 'leaflet'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  tileUrl: string
  attribution: string
  center?: [number, number]
  zoom?: number
  minZoom?: number
  maxZoom?: number
  height?: string | number
  label?: string
  scrollWheelZoom?: boolean
}>(), { center: () => [0, 0], zoom: 2, minZoom: 1, maxZoom: 19, height: '24rem', label: '地图', scrollWheelZoom: false })
const emit = defineEmits<{ 'ready': [map: LeafletMap], 'click': [event: LeafletMouseEvent], 'error': [error: Error], 'update:center': [center: [number, number]], 'update:zoom': [zoom: number] }>()
const container = ref<HTMLElement>()
const loading = ref(false)
const errorMessage = ref('')
const center = defineModel<[number, number]>('center', { default: () => [0, 0] })
const zoom = defineModel<number>('zoom', { default: 2 })
let map: LeafletMap | undefined
let generation = 0
async function initialize() {
  if (!container.value || !props.tileUrl) {
    return
  }
  const current = ++generation
  map?.remove()
  map = undefined
  loading.value = true
  errorMessage.value = ''
  try {
    const [{ default: L }] = await Promise.all([import('leaflet'), import('leaflet/dist/leaflet.css')])
    if (current !== generation || !container.value) {
      return
    }
    map = L.map(container.value, { minZoom: props.minZoom, maxZoom: props.maxZoom, scrollWheelZoom: props.scrollWheelZoom }).setView(center.value as LatLngExpression, zoom.value)
    const options: TileLayerOptions = { attribution: props.attribution, minZoom: props.minZoom, maxZoom: props.maxZoom }
    L.tileLayer(props.tileUrl, options).addTo(map)
    map.on('click', (event: LeafletMouseEvent) => emit('click', event))
    map.on('moveend', () => {
      const value = map!.getCenter()
      center.value = [value.lat, value.lng]
      emit('update:center', center.value)
    })
    map.on('zoomend', () => {
      zoom.value = map!.getZoom()
      emit('update:zoom', zoom.value)
    })
    emit('ready', map)
  }
  catch (cause) {
    const error = cause instanceof Error ? cause : new Error(String(cause))
    if (current === generation) {
      errorMessage.value = error.message
      emit('error', error)
    }
  }
  finally {
    if (current === generation) {
      loading.value = false
    }
  }
}
watch(() => [props.tileUrl, props.attribution, props.minZoom, props.maxZoom, props.scrollWheelZoom], () => {
  if (map) {
    void initialize()
  }
})
watch(center, (value) => {
  if (map) {
    map.setView(value as LatLngExpression, map.getZoom())
  }
}, { deep: true })
watch(zoom, (value) => {
  if (map && map.getZoom() !== value) {
    map.setZoom(value)
  }
})
onMounted(() => void initialize())
onBeforeUnmount(() => {
  generation++
  map?.remove()
  map = undefined
})
defineExpose({ getMap: () => map, setView: (value: [number, number], level = zoom.value) => map?.setView(value, level), flyTo: (value: [number, number], level = zoom.value) => map?.flyTo(value, level), invalidateSize: () => map?.invalidateSize() })
</script>

<template>
  <div class="yd-map" :style="{ height: typeof height === 'number' ? `${height}px` : height }" :aria-label="label">
    <div ref="container" class="yd-map__canvas" />
    <div v-if="loading" class="yd-map__status" role="status">
      正在加载地图…
    </div>
    <div v-else-if="errorMessage" class="yd-map__status" role="alert">
      地图加载失败：{{ errorMessage }} <button type="button" @click="initialize">
        重试
      </button>
    </div>
    <slot name="overlay" />
  </div>
</template>

<style scoped>
.yd-map { position: relative; min-height: 12rem; overflow: hidden; border: 1px solid oklch(var(--border)); border-radius: var(--yd-surface-radius); }
.yd-map__canvas { width: 100%; height: 100%; min-height: inherit; }
.yd-map__status { position: absolute; inset: 0; z-index: 500; display: grid; place-content: center; gap: 0.5rem; color: oklch(var(--foreground)); background: oklch(var(--background) / 0.9); text-align: center; }
</style>
