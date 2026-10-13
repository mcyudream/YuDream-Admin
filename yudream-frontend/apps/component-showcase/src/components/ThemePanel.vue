<script setup lang="ts">
import { YdAction } from '@yudream/components'
import { useShowcaseAppearance } from '@/composables/useShowcaseAppearance'
import { primaryColorPresets, themeColorSchemeOptions, useYdTheme } from '@/composables/useYdTheme'

const open = ref(false)

const { colorScheme, primaryColor, radius, colorAmblyopia, setPrimaryColor, reset } = useYdTheme()
const { motion } = useShowcaseAppearance()

const customColor = ref(primaryColor.value)
const customColorInvalid = ref(false)

const radiusArray = computed<number[]>({
  get: () => [radius.value],
  set: value => (radius.value = value[0]),
})

watch(primaryColor, (value) => {
  customColor.value = value
  customColorInvalid.value = false
})

watch(customColor, (value) => {
  if (!value.trim()) {
    customColorInvalid.value = false
    return
  }
  customColorInvalid.value = !setPrimaryColor(value)
})
</script>

<template>
  <YdButton size="icon-sm" variant="outline" title="主题定制" @click="open = true">
    <YdIcon name="morph:palette" />
  </YdButton>

  <YdDrawer v-model="open" title="主题定制" description="与主应用主题机制对齐，实时预览，选择保存在本地" :footer="false" content-class="w-[320px]">
    <div class="theme-panel">
      <section class="theme-panel__group">
        <p class="theme-panel__label">
          明暗模式
        </p>
        <YdRadioGroup v-model="colorScheme" :options="themeColorSchemeOptions" />
      </section>

      <section class="theme-panel__group">
        <p class="theme-panel__label">
          主色
        </p>
        <div class="theme-panel__swatches">
          <YdAction
            v-for="preset in primaryColorPresets"
            :key="preset.name"
            type="button"
            class="theme-panel__swatch"
            :class="{ 'is-active': primaryColor.toLowerCase() === preset.value.toLowerCase() }"
            :style="{ background: preset.value }"
            :title="preset.label"
            :aria-label="preset.label"
            :aria-pressed="primaryColor.toLowerCase() === preset.value.toLowerCase()"
            @click="setPrimaryColor(preset.value)"
          >
            <YdIcon v-if="primaryColor.toLowerCase() === preset.value.toLowerCase()" name="morph:check" />
          </YdAction>
        </div>
        <div class="theme-panel__custom">
          <YdInput v-model="customColor" placeholder="#18181b" class="flex-1">
            <template #start>
              <span class="theme-panel__custom-preview" :style="{ background: primaryColor }" />
            </template>
          </YdInput>
        </div>
        <p v-if="customColorInvalid" class="theme-panel__error">
          请输入合法的十六进制色值，如 #18181b
        </p>
      </section>

      <section class="theme-panel__group">
        <div class="flex gap-4 items-center justify-between">
          <p class="theme-panel__label">
            圆角
          </p>
          <span class="theme-panel__value">{{ radius.toFixed(2) }}</span>
        </div>
        <YdSlider v-model="radiusArray" :min="0" :max="1" :step="0.05" />
      </section>

      <section class="theme-panel__group">
        <div class="flex gap-4 items-center justify-between">
          <p class="theme-panel__label">
            色弱模式
          </p>
          <YdSwitch v-model="colorAmblyopia" />
        </div>
      </section>

      <section class="theme-panel__group">
        <p class="theme-panel__label">
          动态效果
        </p>
        <YdRadioGroup v-model="motion" :options="[{ label: '跟随系统', value: 'system' }, { label: '减少动态效果', value: 'reduced' }]" />
        <p class="theme-panel__value">
          所有外观都保留功能动画；此偏好让雅致与紧凑的动作更轻、循环更慢。
        </p>
      </section>
      <YdButton variant="outline" size="sm" class="theme-panel__reset" @click="reset">
        <YdIcon name="morph:rotate-cw" />
        恢复默认
      </YdButton>
    </div>
  </YdDrawer>
</template>

<style scoped>
.theme-panel {
  display: grid;
  gap: 24px;
}

.theme-panel__group {
  display: grid;
  gap: 10px;
}

.theme-panel__label {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-2);
}

.theme-panel__value {
  font-family: ui-monospace, SFMono-Regular, "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-3);
}

.theme-panel__swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.theme-panel__swatch {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  font-size: 14px;
  color: #fff;
  cursor: pointer;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium);
  transition: transform 0.15s, box-shadow 0.15s;
}

.theme-panel__swatch:hover {
  transform: scale(1.1);
}

.theme-panel__swatch.is-active {
  box-shadow: 0 0 0 2px var(--color-bg-1), 0 0 0 4px oklch(var(--ring));
}

.theme-panel__custom {
  display: flex;
  align-items: center;
}

.theme-panel__custom-preview {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: var(--border-radius-small);
}

.theme-panel__error {
  margin: 0;
  font-size: 12px;
  color: rgb(var(--danger-6));
}

.theme-panel__reset {
  justify-self: start;
}
</style>
