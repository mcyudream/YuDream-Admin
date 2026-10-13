<script setup lang="ts">
import { computed } from 'vue'
import './extended.css'

export type YdLoadingType = 'plane' | 'chase' | 'bounce' | 'wave' | 'pulse' | 'flow' | 'swing' | 'circle' | 'circle-fade' | 'grid' | 'fold' | 'wander'
const props = withDefaults(defineProps<{
  type?: YdLoadingType
  size?: number
  text?: string
  fullscreen?: boolean
}>(), { type: 'chase', size: 40, text: '加载中' })
const count = computed(() => ['chase', 'circle', 'circle-fade'].includes(props.type) ? 8 : props.type === 'grid' ? 9 : props.type === 'wave' ? 5 : ['plane', 'pulse'].includes(props.type) ? 1 : props.type === 'fold' ? 4 : 3)
</script>

<template>
  <div class="yd-loading" :class="{ fullscreen }" role="status" aria-live="polite">
    <div class="yd-spinner" :class="type" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
      <i v-for="n in count" :key="n" class="yd-animated" :style="{ '--i': n - 1, '--n': count }" />
    </div><span>{{ text }}</span>
  </div>
</template>

<style scoped>
.yd-loading {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  color: oklch(var(--foreground));
}

.fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2200;
  background: oklch(var(--background) / 85%);
  backdrop-filter: blur(8px);
}

.yd-spinner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-around;
  color: var(--yd-emphasis);
}

i {
  display: block;
  width: 22%;
  height: 22%;
  background: currentcolor;
  border-radius: 50%;
  animation: yd-loader-bounce calc(1.2s * var(--yd-motion-loop-scale)) ease-in-out infinite;
  animation-delay: calc(var(--i) * -0.12s);
}

.plane i,
.pulse i {
  width: 80%;
  height: 80%;
}

.plane i {
  border-radius: var(--yd-control-radius);
  animation-name: yd-loader-flip;
}

.pulse i {
  animation-name: yd-loader-pulse;
}

.wave i {
  width: 12%;
  height: 90%;
  border-radius: 4px;
  animation-name: yd-loader-wave;
}

.chase i,
.circle i,
.circle-fade i {
  position: absolute;
  top: 40%;
  left: 40%;
  width: 20%;
  height: 20%;
  transform: rotate(calc(var(--i) * 45deg)) translateY(-180%);
  animation-name: yd-loader-fade;
}

.circle i {
  border-radius: 2px;
}

.circle-fade i {
  animation-duration: calc(1.8s * var(--yd-motion-loop-scale));
}

.grid,
.fold {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 3px;
}

.grid i,
.fold i {
  width: 100%;
  height: 100%;
  border-radius: 3px;
  animation-name: yd-loader-pulse;
}

.fold {
  grid-template-columns: repeat(2, 1fr);
}

.fold i {
  animation-name: yd-loader-flip;
}

.swing i {
  transform-origin: center -100%;
  animation-name: yd-loader-swing;
}

.wander i {
  animation-name: yd-loader-wander;
}

.flow i {
  animation-name: yd-loader-wave;
}

@keyframes yd-loader-bounce {
  0%,
  80%,
  100% {
    translate: 0 0;
  }

  40% {
    translate: 0 -70%;
  }
}

@keyframes yd-loader-wave {
  0%,
  100% {
    scale: 1 0.45;
  }

  50% {
    scale: 1 1;
  }
}

@keyframes yd-loader-pulse {
  0%,
  100% {
    opacity: 0.35;
    scale: 0.5;
  }

  50% {
    opacity: 1;
    scale: 1;
  }
}

@keyframes yd-loader-flip {
  50% {
    transform: perspective(100px) rotateY(180deg);
  }

  100% {
    transform: perspective(100px) rotateY(360deg);
  }
}

@keyframes yd-loader-fade {
  50% {
    opacity: 0.15;
  }
}

@keyframes yd-loader-swing {
  50% {
    rotate: 30deg;
  }
}

@keyframes yd-loader-wander {
  50% {
    rotate: 180deg;
    translate: 50% -50%;
  }
}
</style>
