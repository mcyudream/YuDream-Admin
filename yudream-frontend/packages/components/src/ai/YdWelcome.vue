<script setup lang="ts">
import FaIcon from '../basic/icon/index.vue'

withDefaults(defineProps<{
  /** 主问候语，如“下午好，Husky” */
  title?: string
  /** 副标题描述 */
  description?: string
  /** 推荐提问 */
  suggestions?: string[]
  /** 中央品牌标识图标 */
  icon?: string
}>(), {
  title: '今天想让我帮你做什么？',
  description: '',
  suggestions: () => [],
  icon: 'i-ri:sparkling-2-fill',
})

const emits = defineEmits<{
  select: [text: string]
}>()
</script>

<template>
  <section class="yd-welcome">
    <div class="yd-welcome__mark">
      <FaIcon :name="icon" />
    </div>
    <h1 class="yd-welcome__title">
      {{ title }}
    </h1>
    <p v-if="description" class="yd-welcome__desc">
      {{ description }}
    </p>

    <!-- 推荐提问网格 -->
    <div v-if="suggestions.length" class="yd-welcome__grid">
      <button
        v-for="item in suggestions"
        :key="item"
        type="button"
        class="yd-welcome__prompt"
        @click="emits('select', item)"
      >
        <FaIcon name="i-ri:sparkling-line" class="yd-welcome__prompt-icon" />
        <span>{{ item }}</span>
        <FaIcon name="i-ri:arrow-right-up-line" class="yd-welcome__prompt-arrow" />
      </button>
    </div>
  </section>
</template>

<style scoped>
.yd-welcome {
  display: grid;
  justify-items: center;
  padding: 8vh 24px 40px;
  text-align: center;
}

.yd-welcome__mark {
  display: grid;
  width: 68px;
  height: 68px;
  margin-bottom: 22px;
  place-items: center;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(var(--primary-6), 0.16), rgba(var(--primary-6), 0.06));
  color: rgb(var(--primary-6));
  font-size: 34px;
  box-shadow: 0 8px 24px rgba(var(--primary-6), 0.12);
}

.yd-welcome__title {
  margin: 0;
  color: var(--color-text-1);
  font-size: 28px;
  font-weight: 600;
  letter-spacing: -0.01em;
}

.yd-welcome__desc {
  max-width: 560px;
  margin: 12px 0 0;
  color: var(--color-text-3);
  font-size: 14px;
  line-height: 1.7;
}

.yd-welcome__grid {
  display: grid;
  width: 100%;
  max-width: 640px;
  gap: 10px;
  margin-top: 36px;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}

.yd-welcome__prompt {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 13px 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-bg-1);
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  font-size: 13.5px;
  text-align: left;
  transition: border-color 0.15s, background 0.15s, color 0.15s, transform 0.15s;
}

.yd-welcome__prompt:hover {
  border-color: rgba(var(--primary-6), 0.4);
  background: rgba(var(--primary-6), 0.04);
  color: var(--color-text-1);
  transform: translateY(-1px);
}

.yd-welcome__prompt-icon {
  flex-shrink: 0;
  color: rgb(var(--primary-6));
  font-size: 16px;
}

.yd-welcome__prompt span {
  flex: 1;
  min-width: 0;
}

.yd-welcome__prompt-arrow {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 14px;
  opacity: 0;
  transition: opacity 0.15s;
}

.yd-welcome__prompt:hover .yd-welcome__prompt-arrow {
  opacity: 1;
}
</style>
