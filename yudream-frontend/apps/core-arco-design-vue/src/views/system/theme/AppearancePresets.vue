<script setup lang="ts">
import type { AdminAppearance, AdminStylePreset } from '@/utils/admin-appearance'
import { useActivePluginTheme } from '@/plugins/theme-runtime'

const props = defineProps<{ modelValue: AdminAppearance }>()
const emit = defineEmits<{ 'update:modelValue': [value: AdminAppearance] }>()
const adminTheme = useActivePluginTheme('ADMIN')

const presets: { value: AdminStylePreset, name: string, icon: string, description: string, detail: string }[] = [
  { value: 'vibrant', name: '灵动', icon: 'morph:sparkles', description: '轻盈层次，灵动反馈', detail: '舒展的卡片、轻弹交互与依次入场。' },
  { value: 'calm', name: '雅致', icon: 'morph:feather', description: '柔和留白，从容有序', detail: '柔和的层次与克制的过渡，专注内容。' },
  { value: 'compact', name: '紧凑', icon: 'morph:panels-top-left', description: '清晰高效，一览更多', detail: '精简间距与利落反馈，适合密集工作。' },
]

const reducedMotion = computed({
  get: () => props.modelValue.motion === 'reduced',
  set: (value: boolean) => emit('update:modelValue', { ...props.modelValue, motion: value ? 'reduced' : 'system' }),
})

function selectPreset(preset: AdminStylePreset) {
  emit('update:modelValue', { ...props.modelValue, preset })
}
</script>

<template>
  <section class="appearance-section" aria-labelledby="admin-appearance-title">
    <div class="flex flex-wrap gap-3 items-start justify-between">
      <div class="space-y-1.5">
        <h2 id="admin-appearance-title" class="text-base font-semibold m-0">
          选择你的工作节奏
        </h2>
        <p class="text-sm text-muted-foreground leading-relaxed m-0">
          点击预设即刻预览，保存设置后持续生效。主色与明暗模式自由搭配。
        </p>
      </div>
      <YdTag variant="outline">
        管理后台
      </YdTag>
    </div>

    <div class="gap-4 grid grid-cols-1 md:grid-cols-3">
      <article v-for="preset in presets" :key="preset.value" class="appearance-card">
        <div class="appearance-preview" :data-preset="preset.value" aria-hidden="true">
          <div class="preview-shell">
            <div class="preview-sidebar">
              <span class="preview-logo" />
              <span v-for="n in 4" :key="n" class="preview-nav" />
            </div>
            <div class="preview-main">
              <div class="preview-topbar">
                <span />
                <span />
              </div>
              <div class="preview-content">
                <div class="preview-heading" />
                <div class="preview-tiles">
                  <div v-for="n in 3" :key="n" class="preview-tile">
                    <span />
                    <span />
                  </div>
                </div>
                <div class="preview-panel">
                  <div v-for="n in 3" :key="n" class="preview-row">
                    <span /><span /><span />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="appearance-card__body">
          <div class="text-sm font-semibold flex gap-2 items-center">
            <YdIcon :name="preset.icon" />
            <span>{{ preset.description }}</span>
          </div>
          <p class="text-xs text-muted-foreground leading-relaxed m-0">
            {{ preset.detail }}
          </p>
          <YdButton
            :variant="modelValue.preset === preset.value ? 'default' : 'outline'"
            :aria-pressed="modelValue.preset === preset.value"
            class="mt-auto w-full"
            @click="selectPreset(preset.value)"
          >
            <YdIcon v-if="modelValue.preset === preset.value" name="morph:check" />
            {{ modelValue.preset === preset.value ? `已选择${preset.name}` : `选择${preset.name}` }}
          </YdButton>
        </div>
      </article>
    </div>

    <div class="appearance-motion">
      <div class="space-y-1">
        <label for="admin-reduced-motion" class="text-sm font-medium">减少动态效果</label>
        <p id="admin-motion-description" class="text-xs text-muted-foreground leading-relaxed m-0">
          降低位移与弹性反馈；关闭时也会尊重系统的减少动态效果偏好。
        </p>
      </div>
      <YdSwitch id="admin-reduced-motion" v-model="reducedMotion" aria-describedby="admin-motion-description" />
    </div>

    <div v-if="adminTheme" class="appearance-plugin-note" role="status">
      <YdIcon name="morph:puzzle" class="mt-0.5 shrink-0" />
      <p class="text-sm leading-relaxed m-0">
        当前管理后台使用「{{ adminTheme.name }}」主题。插件定义的风格优先生效，内置预设保留为回落样式。
      </p>
    </div>
  </section>
</template>

<style scoped>
.appearance-section {
  display: grid;
  gap: 20px;
  margin-bottom: 28px;
}

.appearance-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 16px;
}

.appearance-card__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.appearance-preview {
  --preview-radius: 9px;
  --preview-gap: 9px;

  padding: 20px 18px 0;
  overflow: hidden;
  background: var(--color-fill-1);
}

.preview-shell {
  display: grid;
  grid-template-columns: 30px 1fr;
  height: 146px;
  overflow: hidden;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-bottom: 0;
  border-radius: 10px 10px 0 0;
  box-shadow: 0 6px 18px color-mix(in srgb, var(--color-text-1) 7%, transparent);
}

.preview-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  padding-top: 12px;
  border-right: 1px solid var(--color-border-1);
}

.preview-logo {
  width: 12px;
  height: 12px;
  margin-bottom: 2px;
  background: var(--color-text-2);
  border-radius: 4px;
}

.preview-nav {
  width: 11px;
  height: 4px;
  background: var(--color-fill-3);
  border-radius: 2px;
}

.preview-main {
  min-width: 0;
}

.preview-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 25px;
  padding: 0 10px;
  border-bottom: 1px solid var(--color-border-1);
}

.preview-topbar span,
.preview-heading,
.preview-tile span,
.preview-row span {
  display: block;
  height: 4px;
  background: var(--color-fill-3);
  border-radius: 2px;
}

.preview-topbar span:first-child {
  width: 28px;
}

.preview-topbar span:last-child {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.preview-content {
  display: grid;
  gap: var(--preview-gap);
  padding: var(--preview-gap);
  background: var(--color-fill-1);
}

.preview-heading {
  width: 40%;
  height: 5px;
  background: var(--color-text-3);
}

.preview-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--preview-gap);
}

.preview-tile,
.preview-panel {
  padding: 8px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-1);
  border-radius: var(--preview-radius);
}

.preview-tile span:first-child {
  width: 60%;
  margin-bottom: 6px;
}

.preview-tile span:last-child {
  width: 40%;
  height: 7px;
  background: var(--color-text-3);
}

.preview-panel {
  display: grid;
  gap: 8px;
}

.preview-row {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 12px;
}

[data-preset="vibrant"] .preview-tile {
  box-shadow: 0 3px 6px color-mix(in srgb, var(--color-text-1) 5%, transparent);
}

[data-preset="vibrant"] .preview-tile:nth-child(2) {
  translate: 0 -3px;
}

[data-preset="calm"] {
  --preview-radius: 6px;
  --preview-gap: 12px;
}

[data-preset="compact"] {
  --preview-radius: 3px;
  --preview-gap: 6px;
}

[data-preset="compact"] .preview-shell {
  grid-template-columns: 43px 1fr;
}

[data-preset="compact"] .preview-tile,
[data-preset="compact"] .preview-panel {
  padding: 6px;
}

.appearance-motion,
.appearance-plugin-note {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.appearance-plugin-note {
  align-items: flex-start;
  justify-content: flex-start;
  color: var(--color-text-2);
  background: var(--color-fill-1);
}
</style>
