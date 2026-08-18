<script setup lang="ts">
import { primaryColorPresets, themeColorSchemeOptions, useYdTheme } from '@/composables/useYdTheme'

const open = ref(false)

const { colorScheme, primaryColor, radius, colorAmblyopia, setPrimaryColor, reset } = useYdTheme()

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
  <FaButton size="icon-sm" variant="outline" title="主题定制" @click="open = true">
    <FaIcon name="i-ri:palette-line" />
  </FaButton>

  <FaDrawer v-model="open" title="主题定制" description="与主应用主题机制对齐，实时预览，选择保存在本地" :footer="false" content-class="w-[320px]">
    <div class="theme-panel">
      <section class="theme-panel__group">
        <p class="theme-panel__label">
          明暗模式
        </p>
        <FaRadioGroup v-model="colorScheme" :options="themeColorSchemeOptions" />
      </section>

      <section class="theme-panel__group">
        <p class="theme-panel__label">
          主色
        </p>
        <div class="theme-panel__swatches">
          <button
            v-for="preset in primaryColorPresets"
            :key="preset.name"
            type="button"
            class="theme-panel__swatch"
            :class="{ 'is-active': primaryColor === preset.value }"
            :style="{ background: preset.value }"
            :title="preset.label"
            @click="setPrimaryColor(preset.value)"
          >
            <FaIcon v-if="primaryColor === preset.value" name="i-ri:check-line" />
          </button>
        </div>
        <div class="theme-panel__custom">
          <FaInput v-model="customColor" placeholder="#165DFF" class="flex-1">
            <template #start>
              <span class="theme-panel__custom-preview" :style="{ background: primaryColor }" />
            </template>
          </FaInput>
        </div>
        <p v-if="customColorInvalid" class="theme-panel__error">
          请输入合法的十六进制色值，如 #165DFF
        </p>
      </section>

      <section class="theme-panel__group">
        <div class="flex items-center justify-between gap-4">
          <p class="theme-panel__label">
            圆角
          </p>
          <span class="theme-panel__value">{{ radius.toFixed(2) }}</span>
        </div>
        <FaSlider v-model="radiusArray" :min="0" :max="1" :step="0.05" />
      </section>

      <section class="theme-panel__group">
        <div class="flex items-center justify-between gap-4">
          <p class="theme-panel__label">
            色弱模式
          </p>
          <FaSwitch v-model="colorAmblyopia" />
        </div>
      </section>

      <FaButton variant="outline" size="sm" class="theme-panel__reset" @click="reset">
        <FaIcon name="i-ri:restart-line" />
        恢复默认
      </FaButton>
    </div>
  </FaDrawer>
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
  color: var(--color-text-2);
  font-size: 13px;
  font-weight: 500;
}

.theme-panel__value {
  color: var(--color-text-3);
  font-family: ui-monospace, 'SFMono-Regular', 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.theme-panel__swatches {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.theme-panel__swatch {
  display: grid;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid var(--color-border-2);
  border-radius: var(--border-radius-medium);
  color: #fff;
  cursor: pointer;
  font-size: 14px;
  place-items: center;
  transition: transform 0.15s, box-shadow 0.15s;
}

.theme-panel__swatch:hover {
  transform: scale(1.1);
}

.theme-panel__swatch.is-active {
  box-shadow: 0 0 0 2px var(--color-bg-1), 0 0 0 4px rgb(var(--primary-6));
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
  color: rgb(var(--danger-6));
  font-size: 12px;
}

.theme-panel__reset {
  justify-self: start;
}
</style>
