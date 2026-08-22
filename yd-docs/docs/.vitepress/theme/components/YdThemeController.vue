<script setup lang="ts">
import { computed } from 'vue'
import { primaryColorPresets, themeColorSchemeOptions, useYdDocsTheme } from '../composables/useYdDocsTheme'

const { colorScheme, primaryColor, radius, setPrimaryColor, reset } = useYdDocsTheme({ initialize: false })
const radiusPercent = computed({
  get: () => Math.round(radius.value * 100),
  set: value => radius.value = Math.min(1, Math.max(0, value / 100)),
})

function updatePrimaryColor(event: Event) {
  setPrimaryColor((event.target as HTMLInputElement).value)
}
</script>

<template>
  <aside class="yd-theme-controller" aria-label="文档主题设置">
    <details>
      <summary>主题设置</summary>
      <div class="yd-theme-controller__content">
        <label>
          <span>外观</span>
          <select v-model="colorScheme">
            <option v-for="option in themeColorSchemeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <fieldset>
          <legend>主色</legend>
          <div class="yd-theme-controller__presets">
            <button
              v-for="preset in primaryColorPresets"
              :key="preset.name"
              :aria-label="preset.label"
              :aria-pressed="primaryColor === preset.value"
              :class="{ 'is-active': primaryColor === preset.value }"
              :style="{ '--yd-theme-swatch': preset.value }"
              type="button"
              @click="setPrimaryColor(preset.value)"
            />
          </div>
          <input :value="primaryColor" aria-label="自定义主色" type="color" @input="updatePrimaryColor">
        </fieldset>
        <label>
          <span>圆角 {{ radiusPercent }}%</span>
          <input v-model.number="radiusPercent" min="0" max="100" step="10" type="range">
        </label>
        <button class="yd-theme-controller__reset" type="button" @click="reset">恢复默认</button>
      </div>
    </details>
  </aside>
</template>
