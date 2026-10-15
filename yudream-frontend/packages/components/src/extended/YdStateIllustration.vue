<script setup lang="ts">
import './extended.css'

export type YdStateIllustrationName = 'empty' | 'no-result' | 'error' | 'forbidden' | 'success' | 'warning'

withDefaults(defineProps<{
  name: YdStateIllustrationName
  size?: 'sm' | 'md' | 'lg'
  animated?: boolean
}>(), { size: 'md', animated: true })
</script>

<template>
  <span
    class="yd-state-illustration"
    :class="{ 'is-animated': animated }"
    :data-name="name"
    :data-size="size"
    aria-hidden="true"
  >
    <svg viewBox="0 0 120 120" role="presentation" focusable="false">
      <g v-if="name === 'empty'">
        <path class="blob" d="M28 62 38 98h44l10-36Z" />
        <path class="blob-line" d="M28 62 38 98h44l10-36Z" />
        <path class="line" d="M28 62 18 48M92 62 102 48" />
        <g class="float">
          <circle class="dot" cx="52" cy="44" r="3.5" />
          <circle class="dot" cx="68" cy="34" r="2.5" />
        </g>
      </g>

      <g v-else-if="name === 'no-result'">
        <rect class="surface" x="16" y="30" width="56" height="12" rx="6" />
        <rect class="surface" x="16" y="50" width="44" height="12" rx="6" />
        <rect class="surface" x="16" y="70" width="50" height="12" rx="6" />
        <g class="accent">
          <g class="float">
            <circle class="cut" cx="76" cy="66" r="22" />
            <circle class="stroke" cx="76" cy="66" r="22" />
            <path class="stroke" d="M92 82 103 93" />
          </g>
        </g>
      </g>

      <g v-else-if="name === 'error'">
        <rect class="surface" x="28" y="24" width="60" height="60" rx="16" />
        <path class="line" d="M58 24 50 47l13 9-11 28" />
        <g class="accent">
          <g class="float">
            <circle class="fill" cx="92" cy="88" r="17" />
            <path class="on-accent" d="M92 79v8" />
            <path class="on-accent" d="M92 93v.5" />
          </g>
        </g>
      </g>

      <g v-else-if="name === 'forbidden'">
        <rect class="surface" x="32" y="54" width="56" height="42" rx="13" />
        <path class="line" d="M46 54V42a14 14 0 0 1 28 0v12" />
        <g class="accent">
          <g class="float">
            <circle class="fill" cx="60" cy="72" r="5.5" />
            <path class="stroke" d="M60 78v7" />
          </g>
        </g>
      </g>

      <g v-else-if="name === 'success'">
        <circle class="surface" cx="60" cy="62" r="32" />
        <g class="accent">
          <path class="stroke thick" d="M46 63 57 74 76 50" />
        </g>
        <g class="float">
          <circle class="dot" cx="24" cy="34" r="3.5" />
          <circle class="dot" cx="98" cy="44" r="2.5" />
          <circle class="dot" cx="92" cy="94" r="2.5" />
        </g>
      </g>

      <g v-else>
        <path class="blob" d="M60 26 102 96H18Z" />
        <path class="blob-line" d="M60 26 102 96H18Z" />
        <g class="accent">
          <g class="float">
            <path class="stroke thick" d="M60 50v20" />
            <circle class="fill" cx="60" cy="80" r="3.5" />
          </g>
        </g>
      </g>
    </svg>
  </span>
</template>

<style scoped>
.yd-state-illustration {
  display: block;
  width: 120px;
  max-width: 100%;
  color: oklch(var(--muted-foreground));
}

.yd-state-illustration svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

.yd-state-illustration[data-size="sm"] {
  width: 80px;
}

.yd-state-illustration[data-size="lg"] {
  width: 168px;
}

.yd-state-illustration .surface {
  fill: oklch(var(--muted));
  stroke: oklch(var(--border));
  stroke-width: 2;
}

.yd-state-illustration .blob {
  fill: oklch(var(--muted));
  stroke: oklch(var(--muted));
  stroke-width: 10;
  stroke-linejoin: round;
}

.yd-state-illustration .blob-line {
  fill: none;
  stroke: oklch(var(--border));
  stroke-width: 2;
  stroke-linejoin: round;
}

.yd-state-illustration .line {
  fill: none;
  stroke: currentcolor;
  stroke-width: 2.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.yd-state-illustration .dot {
  fill: currentcolor;
}

.yd-state-illustration .cut {
  fill: oklch(var(--card));
}

.yd-state-illustration .accent {
  color: var(--yd-emphasis);
}

.yd-state-illustration[data-name="error"] .accent,
.yd-state-illustration[data-name="forbidden"] .accent {
  color: var(--yd-status-danger);
}

.yd-state-illustration[data-name="warning"] .accent {
  color: var(--yd-status-warning);
}

.yd-state-illustration[data-name="success"] .accent {
  color: var(--yd-status-success);
}

.yd-state-illustration .accent .stroke {
  fill: none;
  stroke: currentcolor;
  stroke-width: 3.5;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.yd-state-illustration .accent .stroke.thick {
  stroke-width: 5.5;
}

.yd-state-illustration .accent .fill {
  fill: currentcolor;
}

.yd-state-illustration .accent .on-accent {
  fill: none;
  stroke: oklch(var(--card));
  stroke-width: 2.5;
  stroke-linecap: round;
}

.yd-state-illustration.is-animated .float {
  animation: yd-state-illustration-float calc(var(--yd-decoration-duration) * var(--yd-motion-loop-scale, 1)) ease-in-out infinite;
}

@keyframes yd-state-illustration-float {
  0%,
  100% {
    transform: translate(0, 0);
  }

  50% {
    transform: translate(0, -3px);
  }
}
</style>
