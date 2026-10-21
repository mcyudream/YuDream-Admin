<script setup lang="ts">
// 检查器「样式类与数据」：节点引用主题样式类；数据绑定把插件/框架数据填进作者设计好的条目模板。
// 全部落为普通属性（class、data-yd-*），由公开站运行时解释；无数据时页面原样显示作者写的示例条目。
import type { StudioNode } from '@/types/site-studio'
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { STUDIO_DATA_SOURCES } from '@/utils/site-studio-runtime'

defineOptions({ name: 'StudioNodeBindings' })

const props = defineProps<{ node: StudioNode }>()

const store = useSiteStudioStore()
const toast = useYdToast()

const attrs = computed(() => props.node.attributes)
const classNames = computed(() => (attrs.value.class || '').split(/\s+/).filter(Boolean))
const themeClasses = computed(() => (store.doc?.theme.classes ?? []).map(item => item.name))
const siteOptions = [
  { label: '不绑定', value: '' },
  { label: '站点名称', value: 'name' },
  { label: '站点简介', value: 'description' },
  { label: '站点 Logo（图片元素）', value: 'logo' },
  { label: '版权信息', value: 'copyright' },
  { label: '当前年份', value: 'year' },
]
const sourceOptions = [{ label: '不绑定', value: '' }, ...STUDIO_DATA_SOURCES.map(item => ({ label: item.label, value: item.value }))]

/** 当前节点所在（或自身）的数据源容器，决定字段提示 */
const ancestorSource = computed(() => {
  const doc = store.doc
  if (!doc) {
    return ''
  }
  const roots = [...doc.theme.header, ...doc.theme.footer, ...doc.pages.flatMap(page => page.root)]
  const walk = (nodes: StudioNode[], source: string): string | null => {
    for (const item of nodes) {
      const next = item.attributes['data-yd-source'] || source
      if (item.id === props.node.id) {
        return next
      }
      const found = walk(item.children, next)
      if (found !== null) {
        return found
      }
    }
    return null
  }
  return walk(roots, '') ?? ''
})
const fieldOptions = computed(() => {
  const source = STUDIO_DATA_SOURCES.find(item => item.value === ancestorSource.value)
  return [{ label: '不绑定', value: '' }, ...(source?.fields ?? []).map(field => ({ label: field, value: field }))]
})
const customSource = computed(() => {
  const value = attrs.value['data-yd-source'] || ''
  return value && !STUDIO_DATA_SOURCES.some(item => item.value === value) ? value : ''
})

function setAttributes(patch: Record<string, string | null>) {
  const next = { ...attrs.value }
  for (const [key, value] of Object.entries(patch)) {
    if (value === null || value === '') {
      delete next[key]
    }
    else {
      next[key] = value
    }
  }
  try {
    store.execute({ type: 'update-node', nodeId: props.node.id, patch: { attributes: next } })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '属性保存失败')
  }
}

function toggleClass(name: string) {
  const list = classNames.value.includes(name) ? classNames.value.filter(item => item !== name) : [...classNames.value, name]
  setAttributes({ class: list.join(' ') || null })
}

function onBlockCode(event: Event) {
  const code = (event.target as HTMLInputElement | null)?.value.trim() || ''
  if (code && !/^[\w-]+$/.test(code)) {
    toast.error('块编码只能包含字母、数字、下划线与连字符')
    return
  }
  setAttributes({ 'data-yd-source': code ? `block:${code}` : null })
}

function onLimit(event: Event) {
  const value = Number.parseInt((event.target as HTMLInputElement | null)?.value || '', 10)
  setAttributes({ 'data-yd-limit': Number.isFinite(value) && value > 0 ? String(Math.min(value, 50)) : null })
}
</script>

<template>
  <section class="flex flex-col gap-2" aria-label="样式类与数据">
    <h3 class="text-xs text-muted-foreground font-medium">
      样式类
    </h3>
    <div v-if="themeClasses.length" class="flex flex-wrap gap-1">
      <YdButton
        v-for="name in themeClasses"
        :key="name"
        v-auth="'platform:cms:edit'"
        size="sm"
        :variant="classNames.includes(name) ? 'default' : 'outline'"
        :aria-pressed="classNames.includes(name)"
        @click="toggleClass(name)"
      >
        {{ name }}
      </YdButton>
    </div>
    <p v-else class="text-xs text-muted-foreground m-0">
      主题里还没有样式类；在「主题 → 主题样式表」中定义后可在此一键套用。
    </p>

    <h3 class="text-xs text-muted-foreground font-medium mt-1">
      站点信息
    </h3>
    <label class="text-xs flex flex-col gap-1">
      <span>显示「系统设置」中的站点信息（随设置更新，未设置时保留当前内容）</span>
      <YdSelect
        v-auth="'platform:cms:edit'"
        :model-value="attrs['data-yd-site'] || ''"
        :options="siteOptions"
        aria-label="站点信息绑定"
        @update:model-value="(value: unknown) => setAttributes({ 'data-yd-site': String(value || '') || null })"
      />
    </label>

    <h3 class="text-xs text-muted-foreground font-medium mt-1">
      数据绑定
    </h3>
    <label class="text-xs flex flex-col gap-1">
      <span>数据源（容器）</span>
      <YdSelect
        v-auth="'platform:cms:edit'"
        :model-value="customSource ? '' : attrs['data-yd-source'] || ''"
        :options="sourceOptions"
        aria-label="数据源"
        @update:model-value="(value: unknown) => setAttributes({ 'data-yd-source': String(value || '') || null })"
      />
    </label>
    <label class="text-xs flex flex-col gap-1">
      <span>或其他插件提供的主题块编码</span>
      <YdInput :model-value="customSource.replace(/^block:/, '')" placeholder="如 my-plugin-block" aria-label="自定义主题块编码" @change="onBlockCode" />
    </label>
    <label v-if="attrs['data-yd-source']" class="text-xs flex flex-col gap-1">
      <span>最多条数</span>
      <YdInput :model-value="attrs['data-yd-limit'] || '12'" aria-label="最多条数" @change="onLimit" />
    </label>
    <label class="text-xs flex gap-2 items-center">
      <YdSwitch :model-value="'data-yd-sample' in attrs" aria-label="仅示例时显示" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-sample': value ? 'true' : null, 'data-yd-live': null })" />
      <span>仅显示示例时可见（如「示例数据」标记）</span>
    </label>
    <label class="text-xs flex gap-2 items-center">
      <YdSwitch :model-value="'data-yd-live' in attrs" aria-label="仅真实数据时显示" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-live': value ? 'true' : null, 'data-yd-sample': null })" />
      <span>仅有真实数据时可见</span>
    </label>
    <label v-if="!ancestorSource && ('data-yd-sample' in attrs || 'data-yd-live' in attrs)" class="text-xs flex flex-col gap-1">
      <span>关联数据源（标记不在数据容器内时）</span>
      <YdSelect
        v-auth="'platform:cms:edit'"
        :model-value="attrs['data-yd-for'] || ''"
        :options="sourceOptions"
        aria-label="关联数据源"
        @update:model-value="(value: unknown) => setAttributes({ 'data-yd-for': String(value || '') || null })"
      />
    </label>
    <div v-if="ancestorSource" class="flex flex-col gap-2">
      <label class="text-xs flex gap-2 items-center">
        <YdSwitch :model-value="'data-yd-item' in attrs" aria-label="作为条目模板" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-item': value ? 'true' : null })" />
        <span>作为条目模板（第一项为模板，其余同标记项是示例，有数据时一并替换）</span>
      </label>
      <div class="gap-2 grid grid-cols-2">
        <label v-for="[key, label] in ([['data-yd-text', '文本'], ['data-yd-href', '链接'], ['data-yd-src', '图片'], ['data-yd-title', '提示/复制']] as const)" :key="key" class="text-xs flex flex-col gap-1 min-w-0">
          <span>{{ label }}字段</span>
          <YdSelect
            v-auth="'platform:cms:edit'"
            class="min-w-0 w-full"
            :model-value="attrs[key] || ''"
            :options="fieldOptions"
            :aria-label="`${label}绑定字段`"
            @update:model-value="(value: unknown) => setAttributes({ [key]: String(value || '') || null })"
          />
        </label>
      </div>
    </div>
  </section>
</template>
