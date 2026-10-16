import type { Component } from 'vue'
import { useForwardExpose } from 'reka-ui'
import { defineComponent, h } from 'vue'

/**
 * 不声明或重建 props/emits，保持原组件的默认值、布尔转换、model 和事件。
 * 返回原类型，因此泛型组件、具名插槽、实例 expose 在调用端继续可用。
 */
export function createYdComponent<T extends object>(base: T, name: string, styleName: string): T {
  const wrapper = defineComponent({
    name,
    inheritAttrs: false,
    setup(_, { attrs, slots }) {
      const { forwardRef } = useForwardExpose()
      return () => {
        // 无原生根节点的浮层提供者不接收额外 attrs，避免影响触发器透传。
        if (['tooltip', 'dropdown', 'context-menu', 'image-preview'].includes(styleName)) {
          return h('span', { class: `yd-base yd-${styleName}`, style: { display: 'contents' } }, [
            h(base as Component, { ...attrs, ref: forwardRef }, slots),
          ])
        }
        return h(base as Component, {
          ...attrs,
          ref: forwardRef,
          class: [`yd-base yd-${styleName}`, attrs.class],
          ...(styleName === 'button' ? { 'data-yd-auto-size': attrs.size === undefined ? '' : undefined } : {}),
          ...(styleName === 'card'
            ? {
                // 模板惯用 kebab 写法（content-class），attrs 里也是 kebab key；
                // 只读驼峰会静默丢掉调用方类名，导致自定义样式永远不生效
                headerClass: ['yd-card-header', attrs.headerClass ?? attrs['header-class']],
                contentClass: ['yd-card-content', attrs.contentClass ?? attrs['content-class']],
                footerClass: ['yd-card-footer', attrs.footerClass ?? attrs['footer-class']],
              }
            : {}),
        }, slots)
      }
    },
  })
  return wrapper as unknown as T
}
