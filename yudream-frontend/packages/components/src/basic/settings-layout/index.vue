<script setup lang="ts">
import type { TabsContentProps, TabsRootProps } from 'reka-ui'
import type { HTMLAttributes } from 'vue'
import { useTextDirection } from '@vueuse/core'
import { computed, ref, useSlots, watch } from 'vue'
import { cn } from '#utils'
import { YdSelect } from '../../adapters/index'
import Icon from '../icon/index.vue'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../tabs/tabs'

defineOptions({
  name: 'BuiltInSettingsLayout',
})

const props = withDefaults(defineProps<{
  modelValue?: TabsRootProps['modelValue']
  list: {
    icon?: string
    label: string
    value: TabsContentProps['value']
    class?: HTMLAttributes['class']
  }[]
  /** 页内标题，不传且无 header/title 插槽时不渲染头部 */
  title?: string
  /** 页内说明，随标题一起渲染 */
  description?: string
  /** 内容对齐方式：center 居中限宽（默认，参考后台设置页版式），start 左对齐不限宽 */
  align?: 'center' | 'start'
  /** 居中模式下的最大宽度类，可被页面 class 覆盖 */
  widthClass?: string
  /** 页面级铺满：铺白色底并至少占满可视高度，用于替换卡片式的 YdPageMain 容器 */
  surface?: boolean
  /** 页面内滚动：内容区独立滚动，页头与分区导航固定；需配合固定高度的页面外壳（.settings-page-shell） */
  scroll?: boolean
  class?: HTMLAttributes['class']
  headerClass?: HTMLAttributes['class']
  navClass?: HTMLAttributes['class']
  listClass?: HTMLAttributes['class']
  contentClass?: HTMLAttributes['class']
}>(), {
  align: 'center',
  widthClass: 'max-w-4xl',
  surface: false,
  scroll: false,
})

const emits = defineEmits<{
  'update:modelValue': [string | number]
}>()

const slots = useSlots()

const dir = useTextDirection({
  observe: true,
})

const activeTab = ref(props.modelValue)

watch(() => props.modelValue, (newValue) => {
  activeTab.value = newValue
})

function handleChange(value: string | number) {
  activeTab.value = value
  emits('update:modelValue', value)
}

/** 窄屏分区选择器：不做横向标签，直接按 label 切换分区 */
const mobileOptions = computed(() => props.list.map(item => ({ label: item.label, value: String(item.value) })))

function handleMobileChange(value: string | number | undefined) {
  if (value == null) {
    return
  }
  const matched = props.list.find(item => String(item.value) === String(value))
  handleChange(matched ? matched.value : value)
}
</script>

<template>
  <div
    :class="cn(
      'flex w-full flex-col',
      // surface 模式整面白底，页面自身不再有外层容器补内边距：
      // 水平方向由本组件负责，窄屏内容才不会贴到可视区边缘
      props.surface && ['bg-card', 'px-5 lg:px-6'],
      !props.scroll && props.surface && 'min-h-full py-5 lg:py-6',
      props.scroll && 'min-h-0 flex-1 overflow-hidden',
      props.class,
    )"
  >
    <header
      v-if="title || description || slots.header || slots.title || slots.description"
      :class="cn('flex flex-col gap-1.5 border-b', props.scroll ? 'pb-5 pt-5 lg:pt-6' : 'mb-6 pb-5', props.headerClass)"
    >
      <slot name="header">
        <h2 class="text-2xl leading-8 tracking-tight font-semibold">
          <slot name="title">
            {{ title }}
          </slot>
        </h2>
        <p v-if="description || slots.description" class="text-sm text-muted-foreground">
          <slot name="description">
            {{ description }}
          </slot>
        </p>
      </slot>
    </header>

    <div :class="cn('flex w-full flex-col', props.align === 'center' && ['mx-auto', props.widthClass], props.scroll && 'min-h-0 flex-1')">
      <Tabs
        v-model="activeTab"
        :dir="dir === 'ltr' ? 'ltr' : 'rtl'"
        :class="cn('flex flex-col gap-6', props.scroll && 'min-h-0 flex-1')"
        @update:model-value="handleChange"
      >
        <!-- 窄屏：分区选择器（不做横向标签切换） -->
        <div :class="cn('lg:hidden', props.scroll && 'pt-5 lg:pt-6')">
          <YdSelect
            :model-value="activeTab == null ? '' : String(activeTab)"
            :options="mobileOptions"
            class="w-full"
            aria-label="设置分区"
            @update:model-value="handleMobileChange"
          />
        </div>

        <div
          :class="cn(
            'flex flex-col gap-6 lg:gap-8 xl:gap-12 lg:grid lg:grid-cols-[minmax(0,200px)_minmax(0,1fr)]',
            props.scroll ? 'min-h-0 flex-1 lg:items-stretch' : 'lg:items-start',
          )"
        >
          <!-- 宽屏：竖向吸顶导航 -->
          <TabsList
            :class="cn('hidden h-auto w-full flex-col items-stretch gap-1 self-start rounded-none bg-transparent p-0 lg:flex',
                       props.scroll ? 'lg:py-6' : 'lg:sticky lg:top-6', props.navClass)"
          >
            <TabsTrigger
              v-for="item in list"
              :key="item.value"
              :value="item.value"
              :class="cn('h-auto min-h-9 flex-none justify-start gap-2 border-transparent bg-transparent px-3 text-sm font-normal text-muted-foreground transition-colors hover:bg-muted/60 hover:text-foreground data-[state=active]:bg-muted data-[state=active]:font-medium data-[state=active]:text-foreground data-[state=active]:shadow-none', item.class)"
            >
              <Icon v-if="item.icon" :name="item.icon" class="flex-shrink-0 size-4" />
              {{ item.label }}
            </TabsTrigger>
          </TabsList>

          <div :class="cn('min-w-0', props.scroll && 'min-h-0 flex-1 overflow-y-auto py-5 lg:py-6', props.contentClass)">
            <TabsContent v-for="item in list" :key="item.value" :value="item.value">
              <slot :name="item.value" />
            </TabsContent>
          </div>
        </div>
      </Tabs>
    </div>
  </div>
</template>
