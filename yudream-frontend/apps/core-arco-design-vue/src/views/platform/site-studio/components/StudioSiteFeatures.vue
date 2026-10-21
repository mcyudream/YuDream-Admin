<script setup lang="ts">
// 站点功能：背景音乐、点击音效与氛围粒子。行为由宿主运行时实现（文档不含脚本），这里只编辑声明，
// 每次修改经 set-features 命令整体替换。音频只能选文档里已有的音频资产（在「资源」页上传）。
import type { StudioFeatures, StudioParticlePreset } from '@/types/site-studio'
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { STUDIO_PARTICLE_PRESETS } from '@/types/site-studio'

defineOptions({ name: 'StudioSiteFeatures' })

const store = useSiteStudioStore()
const toast = useYdToast()

type StudioAudioFeature = NonNullable<StudioFeatures['audio']>

const PARTICLE_LABELS: Record<StudioParticlePreset, string> = {
  none: '无',
  dust: '金色微光',
  snow: '飘雪',
  petals: '花瓣',
  spores: '幽光孢子',
  embers: '余烬',
  stars: '星屑',
}
const particleOptions = STUDIO_PARTICLE_PRESETS.map(value => ({ label: PARTICLE_LABELS[value], value }))

const features = computed<StudioFeatures>(() => store.doc?.features ?? {})
const audio = computed<StudioAudioFeature>(() => features.value.audio ?? { tracks: [], musicVolume: 20, effectVolume: 30, effectsDefaultOn: true })
const audioOptions = computed(() => (store.doc?.assets ?? []).filter(asset => asset.kind === 'audio').map(asset => ({ label: asset.name, value: asset.id })))
const soundOptions = computed(() => [{ label: '无', value: '' }, ...audioOptions.value])

function apply(next: StudioFeatures) {
  try {
    store.execute({ type: 'set-features', features: next })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '站点功能保存失败')
  }
}

function setParticles(value: unknown) {
  apply({ ...features.value, particles: value as StudioParticlePreset })
}

function setAudio(patch: Partial<StudioAudioFeature>) {
  const next = { ...audio.value, ...patch }
  for (const key of ['clickSoundAssetId', 'buttonSoundAssetId', 'toastSoundAssetId'] as const) {
    if (!next[key]) {
      delete next[key]
    }
  }
  apply({ ...features.value, audio: next })
}

function addTrack() {
  const first = audioOptions.value[0]
  if (!first) {
    toast.error('先在「资源」页上传音频文件')
    return
  }
  setAudio({ tracks: [...audio.value.tracks, { title: first.label, assetId: first.value }] })
}

function updateTrack(index: number, patch: Partial<StudioAudioFeature['tracks'][number]>) {
  setAudio({ tracks: audio.value.tracks.map((track, position) => position === index ? { ...track, ...patch } : track) })
}

function removeTrack(index: number) {
  setAudio({ tracks: audio.value.tracks.filter((_, position) => position !== index) })
}

function onTitleChange(index: number, event: Event) {
  const value = (event.target as HTMLInputElement | null)?.value.trim()
  if (value) {
    updateTrack(index, { title: value })
  }
}

function removeAudio() {
  const next = { ...features.value }
  delete next.audio
  apply(next)
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="站点功能">
    <h2 class="text-sm font-semibold m-0">
      站点功能
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      由平台在公开站实现的受控效果，不需要也不允许写脚本。音乐开关：给按钮加属性 <code class="font-mono">data-yd-action="toggle-music"</code>，
      音效开关为 <code class="font-mono">toggle-sound</code>；开启状态会写入 <code class="font-mono">aria-pressed</code>，可用样式类的 pressed 状态做高亮。
      页面级粒子与进入提示在「页面」设置中配置。
    </p>

    <div class="flex flex-wrap gap-2 items-center">
      <span class="text-sm w-28">站点默认粒子</span>
      <YdSelect
        v-auth="'platform:cms:edit'"
        class="w-48"
        :model-value="features.particles ?? 'none'"
        :options="particleOptions"
        aria-label="站点默认粒子"
        @update:model-value="setParticles"
      />
    </div>

    <div class="pt-3 border-t flex flex-col gap-2">
      <div class="flex flex-wrap gap-2 items-center">
        <span class="text-sm font-medium flex-1">背景音乐（按顺序循环）</span>
        <YdButton v-auth="'platform:cms:edit'" variant="outline" size="sm" @click="addTrack">
          <YdIcon name="morph:plus" /> 添加曲目
        </YdButton>
        <YdButton v-if="features.audio" v-auth="'platform:cms:edit'" variant="ghost" size="sm" @click="removeAudio">
          关闭音频功能
        </YdButton>
      </div>
      <p v-if="!audioOptions.length" class="text-xs text-muted-foreground m-0">
        文档中还没有音频资产。到「资源」页上传 mp3 / ogg / wav 后再回来选择。
      </p>
      <div v-for="(track, index) in audio.tracks" :key="index" class="flex flex-wrap gap-2 items-center">
        <YdInput :model-value="track.title" class="w-48" :aria-label="`第 ${index + 1} 首曲名`" @change="onTitleChange(index, $event)" />
        <YdSelect
          class="flex-1 min-w-40"
          :model-value="track.assetId"
          :options="audioOptions"
          :aria-label="`第 ${index + 1} 首音频`"
          @update:model-value="(value: unknown) => updateTrack(index, { assetId: String(value) })"
        />
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="移除曲目" @click="removeTrack(index)">
          <YdIcon name="morph:trash-2" />
        </YdButton>
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <span class="text-sm w-28">音乐音量</span>
        <YdSlider class="flex-1 min-w-40" :model-value="[audio.musicVolume]" :min="0" :max="100" aria-label="音乐音量" @update:model-value="(value: unknown) => setAudio({ musicVolume: Math.round(Number((value as number[])[0])) })" />
        <span class="text-xs font-mono w-10">{{ audio.musicVolume }}</span>
      </div>
    </div>

    <div class="pt-3 border-t flex flex-col gap-2">
      <span class="text-sm font-medium">音效</span>
      <div class="flex flex-wrap gap-2 items-center">
        <span class="text-sm w-28">默认开启音效</span>
        <YdSwitch :model-value="audio.effectsDefaultOn" aria-label="默认开启音效" @update:model-value="(value: unknown) => setAudio({ effectsDefaultOn: Boolean(value) })" />
      </div>
      <div class="flex flex-wrap gap-2 items-center">
        <span class="text-sm w-28">音效音量</span>
        <YdSlider class="flex-1 min-w-40" :model-value="[audio.effectVolume]" :min="0" :max="100" aria-label="音效音量" @update:model-value="(value: unknown) => setAudio({ effectVolume: Math.round(Number((value as number[])[0])) })" />
        <span class="text-xs font-mono w-10">{{ audio.effectVolume }}</span>
      </div>
      <div v-for="[key, label] in ([['clickSoundAssetId', '链接点击'], ['buttonSoundAssetId', '按钮点击'], ['toastSoundAssetId', '进入提示']] as const)" :key="key" class="flex flex-wrap gap-2 items-center">
        <span class="text-sm w-28">{{ label }}</span>
        <YdSelect
          class="flex-1 min-w-40"
          :model-value="audio[key] ?? ''"
          :options="soundOptions"
          :aria-label="`${label}音效`"
          @update:model-value="(value: unknown) => setAudio({ [key]: String(value || '') })"
        />
      </div>
    </div>
  </section>
</template>
