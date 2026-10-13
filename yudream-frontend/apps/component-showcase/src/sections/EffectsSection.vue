<script setup lang="ts">
import { YdAnimatedBeam, YdAnimatedCountTo, YdAnimatedCountToGroup, YdBlurReveal, YdBorderBeam, YdCountTo, YdFlipCard, YdFlipWords, YdGlowyCard, YdGlowyCardWrapper, YdGradientButton, YdInteractiveButton, YdMarquee, YdParticlesBg, YdPatternBg, YdScrollingText, YdSparklesText, YdSparkline, YdSpotlightCard, YdTextHighlight, YdTextShiny } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const beamContainer = ref<HTMLElement>()
const beamStart = ref<HTMLElement>()
const beamEnd = ref<HTMLElement>()
const revealKey = ref(0)
const number = ref(1286)
const tags = ['设计', '灵感', '代码', '动效', '体验', '创造']
</script>

<template>
  <div class="effects-gallery">
    <section class="effect-hero">
      <YdPatternBg variant="dots" size="md" class="hero-pattern" />
      <YdParticlesBg :quantity="36" />
      <YdBlurReveal :key="revealKey">
        <p class="eyebrow">
          YUDREAM / MOTION STUDIO
        </p>
        <h2><YdSparklesText>让灵感</YdSparklesText><br><YdTextShiny>流动起来。</YdTextShiny></h2>
        <p>为 <YdFlipWords :words="['年轻的想法', '鲜活的界面', '日常的工作']" /> 留一点惊喜。</p>
        <div class="demo-row">
          <YdGradientButton @click="revealKey++">
            重播入场
          </YdGradientButton><YdInteractiveButton @click="number += 137">
            让数字动起来
          </YdInteractiveButton>
        </div>
      </YdBlurReveal>
    </section>
    <DemoCard title="文字与数字" description="流光、翻词、强调、滚动计数，颜色跟随主题">
      <div class="word-row">
        <YdTextHighlight>值得被看见的好点子</YdTextHighlight><YdTextShiny>保持好奇，持续创造</YdTextShiny>
      </div>
      <YdAnimatedCountToGroup class="numbers">
        <YdAnimatedCountTo :value="number" /><span class="muted"> / </span><YdCountTo :end-val="number * 3" />
      </YdAnimatedCountToGroup>
      <YdScrollingText text="YUDREAM · MAKE IT PLAYFUL · " :duration="20" />
    </DemoCard>
    <DemoCard title="卡片的互动细节" description="鼠标聚光、边缘流光、翻转与柔和辉光">
      <div class="card-grid">
        <YdSpotlightCard>
          <h3>光随你动</h3><p class="muted">
            移动指针，发现细腻的反馈。
          </p><YdSparkline :data="[12, 18, 14, 24, 20, 31, 35]" label="体验趋势" />
        </YdSpotlightCard>
        <YdFlipCard>
          <h3>换个角度看</h3><p class="muted">
            悬停或点击右上角翻转。
          </p><template #back>
            <h3>另一面，也精彩</h3><p class="muted">
              键盘和触屏都能轻松操作。
            </p>
          </template>
        </YdFlipCard>
        <div class="beam-card yd-surface yd-pad">
          <YdBorderBeam /><h3>给边界一点生命力</h3><p class="muted">
            同一主色，三种工作节奏。
          </p>
        </div>
      </div>
      <YdGlowyCardWrapper>
        <YdGlowyCard>
          <h3>轻盈的辉光</h3><p class="muted">
            沿用 Fa 的视觉基线，加入 YD 的细节。
          </p>
        </YdGlowyCard><YdGlowyCard>
          <h3>共享主题</h3><p class="muted">
            主色、深浅模式与插件变量自然继承。
          </p>
        </YdGlowyCard>
      </YdGlowyCardWrapper>
    </DemoCard>
    <DemoCard title="连接与流动" description="自适应连线和可暂停滚动，悬停滚动内容即可暂停">
      <div ref="beamContainer" class="beam-stage">
        <YdAnimatedBeam :container-ref="beamContainer" :from-ref="beamStart" :to-ref="beamEnd" :curvature="40" /><div ref="beamStart" class="beam-node">
          <YdIcon name="morph:lightbulb" /><span>灵感</span>
        </div><div ref="beamEnd" class="beam-node">
          <YdIcon name="morph:box" /><span>组件</span>
        </div>
      </div>
      <YdMarquee :duration="18">
        <span v-for="tag in tags" :key="tag" class="marquee-chip">{{ tag }} · YD</span>
      </YdMarquee>
    </DemoCard>
    <p class="muted preference-note">
      三套外观都保留功能动画；减少动态效果只柔化雅致与紧凑的动作、速度和装饰密度。
    </p>
  </div>
</template>

<style scoped>
.effects-gallery {
  display: grid;
  gap: var(--yd-workspace-gap);
}

.effect-hero {
  position: relative;
  padding: clamp(24px, 5vw, 56px);
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-surface-radius);
}

.effect-hero :deep(.yd-reveal) {
  position: relative;
}

.hero-pattern {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.eyebrow {
  font-size: 11px;
  color: var(--color-text-3);
  letter-spacing: 0.18em;
}

h2 {
  margin: 18px 0;
  font-size: clamp(2.25rem, 5vw, 4rem);
  line-height: 1.14;
  letter-spacing: -0.05em;
}

h3 {
  margin: 0 0 14px;
  font-size: 18px;
}

p {
  line-height: 1.7;
}

.muted {
  color: var(--color-text-3);
}

.word-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  font-size: 20px;
}

.numbers {
  font-size: 32px;
  font-weight: 650;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--yd-content-gap);
}

.beam-card {
  position: relative;
  overflow: hidden;
}

.beam-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 140px;
  padding: 24px 8%;
}

.beam-node {
  position: relative;
  display: grid;
  gap: 8px;
  place-items: center;
  width: 72px;
  height: 72px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.beam-node :deep(svg) {
  width: 24px;
  height: 24px;
}

.marquee-chip {
  display: inline-block;
  padding: 12px 24px;
  font-weight: 500;
  white-space: nowrap;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.preference-note {
  margin: 0;
  font-size: 12px;
}

@media (width <= 768px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
