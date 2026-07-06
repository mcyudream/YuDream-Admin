import type { EChartsOption } from 'echarts'
import type { Ref } from 'vue'
import type { ChartTheme } from '../types'

type MaybeRef<T> = Ref<T> | T
import * as echarts from 'echarts'
import { nextTick, onUnmounted, unref, watch, watchEffect } from 'vue'
import { useResizeObserver } from '@vueuse/core'

/**
 * ECharts 组合式函数
 * @param elRef - 挂载图表的 DOM 元素引用
 * @param options - ECharts 配置项
 * @param theme - 图表主题
 * @returns 实例访问与重绘方法
 */
export function useECharts(
  elRef: Ref<HTMLElement | null | undefined>,
  options: MaybeRef<EChartsOption>,
  theme: MaybeRef<ChartTheme> = 'light',
) {
  let chartInstance: ReturnType<typeof echarts.init> | null = null
  let resizeFrame = 0

  function initChart() {
    const el = elRef.value
    if (!el) {
      return
    }

    chartInstance?.dispose()
    chartInstance = echarts.init(el, unref(theme) as unknown as string | object)
    chartInstance.setOption(unref(options), true)
    scheduleResize()
  }

  function updateChart() {
    if (!chartInstance) {
      initChart()
      return
    }
    chartInstance.setOption(unref(options), true)
    scheduleResize()
  }

  function resize() {
    scheduleResize()
  }

  async function scheduleResize() {
    if (!chartInstance) {
      return
    }
    await nextTick()
    if (resizeFrame) {
      cancelAnimationFrame(resizeFrame)
    }
    resizeFrame = requestAnimationFrame(() => {
      resizeFrame = 0
      chartInstance?.resize()
    })
  }

  useResizeObserver(elRef, resize)

  watchEffect(() => {
    const el = elRef.value
    if (!el) {
      return
    }
    initChart()
  })

  watch(() => unref(options), updateChart, { deep: true })

  onUnmounted(() => {
    if (resizeFrame) {
      cancelAnimationFrame(resizeFrame)
    }
    chartInstance?.dispose()
    chartInstance = null
  })

  return {
    instance: () => chartInstance,
    resize,
  }
}
