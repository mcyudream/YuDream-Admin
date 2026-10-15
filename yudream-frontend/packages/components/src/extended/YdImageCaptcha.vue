<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import YdButton from '../basic/button/index.vue'

/** 服务端签发的图形验证码挑战；答案只在服务端，前端不可见。 */
export interface YdImageCaptchaChallenge {
  /** 挑战标识，校验时随输入一起提交 */
  id: string
  /** 服务端渲染的图片，data URL 或图片地址 */
  image: string
  /** 可选过期时间，供调用方提示或自动刷新 */
  expiresAt?: number | string
}

export interface YdImageCaptchaResult {
  /** 校验是否通过 */
  valid: boolean
  /** 通过后由服务端签发的一次性票据，随后续受保护动作提交 */
  token?: string
}

const props = withDefaults(defineProps<{
  /** `remote` 走调用方提供的后端挑战与校验；`demo` 仅用于本地演示，不具备安全能力。 */
  mode?: 'demo' | 'remote'
  length?: number
  disabled?: boolean
  noise?: number
  caseSensitive?: boolean
  /** 请求新的服务端挑战，`mode="remote"` 时必填 */
  requestChallenge?: () => Promise<YdImageCaptchaChallenge>
  /** 提交服务端校验，`mode="remote"` 时必填 */
  verifyChallenge?: (input: { challengeId: string, code: string }) => Promise<YdImageCaptchaResult>
}>(), { mode: 'demo', length: 5, noise: 32, caseSensitive: false })
const emit = defineEmits<{
  verify: [result: YdImageCaptchaResult]
  refresh: []
  error: [error: unknown]
}>()
const canvas = ref<HTMLCanvasElement>()
const answer = ref('')
const input = ref('')
const challenge = ref<YdImageCaptchaChallenge>()
const loading = ref(false)
const failedHint = ref(false)
const failureMessage = ref('')
const size = computed(() => Math.max(4, Math.min(8, Math.floor(props.length))))
const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const isRemote = computed(() => props.mode === 'remote')
const remoteReady = computed(() => Boolean(props.requestChallenge && props.verifyChallenge))
const blocked = computed(() => props.disabled || loading.value || failureMessage.value !== '' || (isRemote.value && !remoteReady.value))

function draw() {
  const element = canvas.value
  if (!element) {
    return
  }
  const ctx = element.getContext('2d')
  if (!ctx) {
    return
  }
  const width = 180
  const height = 56
  element.width = width
  element.height = height
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = 'rgba(127,127,127,.08)'
  ctx.fillRect(0, 0, width, height)
  for (let i = 0; i < props.noise; i++) {
    ctx.fillStyle = `rgba(${Math.floor(Math.random() * 180)},${Math.floor(Math.random() * 180)},${Math.floor(Math.random() * 180)},.35)`
    ctx.fillRect(Math.random() * width, Math.random() * height, 1 + Math.random() * 2, 1 + Math.random() * 2)
  }
  ctx.font = '600 30px sans-serif'
  ctx.textBaseline = 'middle'
  answer.value.split('').forEach((char, index) => {
    ctx.save()
    ctx.translate(18 + index * (144 / size.value), 28)
    ctx.rotate((Math.random() - 0.5) * 0.2)
    ctx.fillStyle = `hsl(${Math.floor(Math.random() * 360)} 35% 38%)`
    ctx.fillText(char, 0, 0)
    ctx.restore()
  })
}

function refreshDemo() {
  answer.value = Array.from({ length: size.value }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('')
  input.value = ''
  draw()
}

async function refresh() {
  failureMessage.value = ''
  input.value = ''
  if (!isRemote.value) {
    refreshDemo()
    emit('refresh')
    return
  }
  if (!props.requestChallenge) {
    failureMessage.value = '未接入后端挑战，无法获取图形校验码。'
    return
  }
  loading.value = true
  try {
    challenge.value = await props.requestChallenge()
    emit('refresh')
  }
  catch (error) {
    // 失败时清空挑战，避免用过期图片继续提交。
    challenge.value = undefined
    failureMessage.value = '图形校验码获取失败，请重试。'
    emit('error', error)
  }
  finally {
    loading.value = false
  }
}

async function verify(): Promise<boolean> {
  const code = input.value.trim()
  if (!code || blocked.value) {
    return false
  }
  failedHint.value = false
  if (!isRemote.value) {
    const valid = props.caseSensitive ? code === answer.value : code.toUpperCase() === answer.value
    failedHint.value = !valid
    emit('verify', { valid })
    return valid
  }
  if (!challenge.value || !props.verifyChallenge) {
    return false
  }
  loading.value = true
  try {
    const result = await props.verifyChallenge({ challengeId: challenge.value.id, code })
    emit('verify', result)
    if (!result.valid) {
      // 服务端通常在校验后作废挑战，失败时主动换一张。
      failedHint.value = true
      await refresh()
    }
    return result.valid
  }
  catch (error) {
    failureMessage.value = '图形校验码校验失败，请重试。'
    emit('error', error)
    return false
  }
  finally {
    loading.value = false
  }
}

function retry() {
  failureMessage.value = ''
  failedHint.value = false
  void refresh()
}

watch(() => [props.length, props.noise], () => {
  if (!isRemote.value) {
    refreshDemo()
  }
})
onMounted(() => {
  void refresh()
})
onBeforeUnmount(() => {
  if (canvas.value) {
    canvas.value.width = 0
  }
})
defineExpose({ verify, refresh })
</script>

<template>
  <div class="yd-image-captcha">
    <div class="yd-image-captcha__challenge">
      <img v-if="isRemote && challenge" class="yd-image-captcha__image" :src="challenge.image" alt="图形校验码">
      <canvas v-else-if="!isRemote" ref="canvas" aria-label="图形校验码" role="img" />
      <div v-else class="yd-image-captcha__placeholder">
        {{ loading ? '加载中' : '暂无校验码' }}
      </div>
      <YdButton variant="ghost" size="icon" aria-label="刷新图形校验码" :disabled="disabled || loading" @click="refresh">
        ↻
      </YdButton>
    </div>
    <input
      v-model="input"
      class="yd-image-captcha__input"
      inputmode="text"
      autocomplete="off"
      :disabled="blocked"
      :aria-invalid="failedHint || undefined"
      aria-label="输入图形校验码"
      @keydown.enter="verify"
    >
    <p v-if="failureMessage" class="yd-image-captcha__feedback" role="alert">
      {{ failureMessage }}
      <button type="button" @click="retry">
        重试
      </button>
    </p>
    <p v-else-if="isRemote && !remoteReady" class="yd-image-captcha__feedback" role="alert">
      remote 模式需要提供 requestChallenge 与 verifyChallenge，当前不可用于校验。
    </p>
    <p v-else-if="failedHint" class="yd-image-captcha__feedback">
      {{ isRemote ? '校验未通过，已刷新挑战。' : '校验未通过。' }}
    </p>
  </div>
</template>

<style scoped>
.yd-image-captcha { display: grid; gap: 0.5rem; width: fit-content; }
.yd-image-captcha__challenge { display: flex; align-items: center; gap: 0.25rem; }
.yd-image-captcha__challenge canvas, .yd-image-captcha__image { display: block; width: 180px; height: 56px; border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); }
.yd-image-captcha__image { object-fit: contain; background: oklch(var(--muted) / 40%); }
.yd-image-captcha__placeholder { display: grid; width: 180px; height: 56px; place-items: center; color: oklch(var(--muted-foreground)); font-size: 0.8125rem; background: oklch(var(--muted) / 40%); border: 1px dashed oklch(var(--border)); border-radius: var(--yd-control-radius); }
.yd-image-captcha__input { width: 100%; min-height: var(--yd-control-height); padding: 0.5rem 0.7rem; color: oklch(var(--foreground)); background: oklch(var(--background)); border: 1px solid oklch(var(--border)); border-radius: var(--yd-control-radius); }
.yd-image-captcha__feedback { margin: 0; color: oklch(var(--muted-foreground)); font-size: 0.8125rem; }
.yd-image-captcha__feedback button { padding: 0; color: var(--yd-emphasis); background: transparent; border: 0; cursor: pointer; }
</style>
