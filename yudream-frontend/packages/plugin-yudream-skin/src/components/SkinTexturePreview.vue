<template>
  <div class="skin-texture-preview" :class="{ cape: isCape }">
    <template v-if="isCape">
      <canvas
        ref="capeCanvasRef"
        class="skin-texture-preview__canvas"
        :width="capeCanvasWidth"
        :height="capeCanvasHeight"
      />
    </template>
    <template v-else>
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">背面</span>
        <canvas
          ref="backCanvasRef"
          class="skin-texture-preview__canvas"
          :width="skinCanvasWidth"
          :height="skinCanvasHeight"
        />
      </span>
      <span class="skin-texture-preview__pane">
        <span class="skin-texture-preview__label">正面</span>
        <canvas
          ref="frontCanvasRef"
          class="skin-texture-preview__canvas"
          :width="skinCanvasWidth"
          :height="skinCanvasHeight"
        />
      </span>
    </template>
    <span v-if="failed" class="skin-texture-preview__empty">预览失败</span>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

const props = defineProps<{
  textureUrl: string
  type?: string
  model?: string
}>()

type SkinViewSide = 'front' | 'back'

const capeCanvasRef = ref<HTMLCanvasElement | null>(null)
const frontCanvasRef = ref<HTMLCanvasElement | null>(null)
const backCanvasRef = ref<HTMLCanvasElement | null>(null)
const failed = ref(false)
const isCape = computed(() => props.type === 'cape')
const skinCanvasWidth = computed(() => 72)
const skinCanvasHeight = computed(() => 132)
const capeCanvasWidth = computed(() => 88)
const capeCanvasHeight = computed(() => 132)

onMounted(() => {
  void drawPreview()
})

watch(() => [props.textureUrl, props.model, props.type], () => {
  void drawPreview()
}, { flush: 'post' })

async function drawPreview() {
  failed.value = false
  await nextTick()
  if (!props.textureUrl) {
    return
  }
  try {
    const image = await loadImage(props.textureUrl)
    if (isCape.value) {
      const canvas = capeCanvasRef.value
      const context = prepareCanvas(canvas)
      if (context && canvas) {
        drawCape(context, image, canvas)
      }
    }
    else {
      const backContext = prepareCanvas(backCanvasRef.value)
      const frontContext = prepareCanvas(frontCanvasRef.value)
      if (backContext && backCanvasRef.value) {
        drawSkinFigure(backContext, image, backCanvasRef.value, 'back')
      }
      if (frontContext && frontCanvasRef.value) {
        drawSkinFigure(frontContext, image, frontCanvasRef.value, 'front')
      }
    }
  }
  catch {
    failed.value = true
  }
}

function loadImage(src: string) {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = reject
    image.src = src
  })
}

function prepareCanvas(canvas: HTMLCanvasElement | null) {
  if (!canvas) {
    return null
  }
  const context = canvas.getContext('2d')
  if (!context) {
    return null
  }
  context.clearRect(0, 0, canvas.width, canvas.height)
  context.imageSmoothingEnabled = false
  return context
}

function drawCape(context: CanvasRenderingContext2D, image: HTMLImageElement, canvas: HTMLCanvasElement) {
  const padding = 10
  const sourceUnitX = image.width / 64
  const sourceUnitY = image.height / 32
  const sourceX = sourceUnitX
  const sourceY = sourceUnitY
  const sourceWidth = 10 * sourceUnitX
  const sourceHeight = 16 * sourceUnitY
  const targetHeight = canvas.height - padding * 2
  const targetWidth = targetHeight / 16 * 10
  context.drawImage(
    image,
    sourceX,
    sourceY,
    sourceWidth,
    sourceHeight,
    (canvas.width - targetWidth) / 2,
    padding,
    targetWidth,
    targetHeight,
  )
}

function drawSkinFigure(context: CanvasRenderingContext2D, image: HTMLImageElement, canvas: HTMLCanvasElement, side: SkinViewSide) {
  const slim = props.model === 'slim'
  const scale = 4
  const armWidth = slim ? 3 : 4
  const unit = image.width / 64
  const hasModernLayout = image.height >= 64 * unit
  const figureWidth = (armWidth * 2 + 8) * scale
  const originX = Math.round((canvas.width - figureWidth) / 2)
  const headX = originX + armWidth * scale
  const bodyX = headX
  const leftArmX = originX
  const rightArmX = originX + (armWidth + 8) * scale
  const leftLegX = headX
  const rightLegX = headX + 4 * scale

  if (side === 'front') {
    drawPart(context, image, unit, 8, 8, 8, 8, headX, 2, 8, 8, scale)
    drawPart(context, image, unit, 20, 20, 8, 12, bodyX, 34, 8, 12, scale)
    drawPart(context, image, unit, 44, 20, armWidth, 12, leftArmX, 34, armWidth, 12, scale)
    drawPart(context, image, unit, hasModernLayout ? 36 : 44, hasModernLayout ? 52 : 20, armWidth, 12, rightArmX, 34, armWidth, 12, scale, !hasModernLayout)
    drawPart(context, image, unit, 4, 20, 4, 12, leftLegX, 82, 4, 12, scale)
    drawPart(context, image, unit, hasModernLayout ? 20 : 4, hasModernLayout ? 52 : 20, 4, 12, rightLegX, 82, 4, 12, scale, !hasModernLayout)

    drawPart(context, image, unit, 40, 8, 8, 8, headX, 2, 8, 8, scale)
    if (hasModernLayout) {
      drawPart(context, image, unit, 20, 36, 8, 12, bodyX, 34, 8, 12, scale)
      drawPart(context, image, unit, 44, 36, armWidth, 12, leftArmX, 34, armWidth, 12, scale)
      drawPart(context, image, unit, 52, 52, armWidth, 12, rightArmX, 34, armWidth, 12, scale)
      drawPart(context, image, unit, 4, 36, 4, 12, leftLegX, 82, 4, 12, scale)
      drawPart(context, image, unit, 4, 52, 4, 12, rightLegX, 82, 4, 12, scale)
    }
    return
  }

  drawPart(context, image, unit, 24, 8, 8, 8, headX, 2, 8, 8, scale, true)
  drawPart(context, image, unit, 32, 20, 8, 12, bodyX, 34, 8, 12, scale, true)
  drawPart(context, image, unit, hasModernLayout ? (slim ? 43 : 44) : 52, hasModernLayout ? 52 : 20, armWidth, 12, leftArmX, 34, armWidth, 12, scale, true)
  drawPart(context, image, unit, slim ? 48 : 52, 20, armWidth, 12, rightArmX, 34, armWidth, 12, scale, true)
  drawPart(context, image, unit, hasModernLayout ? 28 : 12, hasModernLayout ? 52 : 20, 4, 12, leftLegX, 82, 4, 12, scale, true)
  drawPart(context, image, unit, 12, 20, 4, 12, rightLegX, 82, 4, 12, scale, true)

  drawPart(context, image, unit, 56, 8, 8, 8, headX, 2, 8, 8, scale, true)
  if (hasModernLayout) {
    drawPart(context, image, unit, 32, 36, 8, 12, bodyX, 34, 8, 12, scale, true)
    drawPart(context, image, unit, 60, 52, armWidth, 12, leftArmX, 34, armWidth, 12, scale, true)
    drawPart(context, image, unit, 52, 36, armWidth, 12, rightArmX, 34, armWidth, 12, scale, true)
    drawPart(context, image, unit, 12, 52, 4, 12, leftLegX, 82, 4, 12, scale, true)
    drawPart(context, image, unit, 12, 36, 4, 12, rightLegX, 82, 4, 12, scale, true)
  }
}

function drawPart(
  context: CanvasRenderingContext2D,
  image: HTMLImageElement,
  unit: number,
  sourceX: number,
  sourceY: number,
  sourceWidth: number,
  sourceHeight: number,
  targetX: number,
  targetY: number,
  targetWidth: number,
  targetHeight: number,
  scale: number,
  flipX = false,
) {
  const normalizedSourceX = sourceX * unit
  const normalizedSourceY = sourceY * unit
  const normalizedSourceWidth = sourceWidth * unit
  const normalizedSourceHeight = sourceHeight * unit
  if (normalizedSourceX + normalizedSourceWidth > image.width || normalizedSourceY + normalizedSourceHeight > image.height) {
    return
  }
  context.save()
  if (flipX) {
    context.translate(targetX + targetWidth * scale, targetY)
    context.scale(-1, 1)
    targetX = 0
    targetY = 0
  }
  context.drawImage(
    image,
    normalizedSourceX,
    normalizedSourceY,
    normalizedSourceWidth,
    normalizedSourceHeight,
    targetX,
    targetY,
    targetWidth * scale,
    targetHeight * scale,
  )
  context.restore()
}
</script>
