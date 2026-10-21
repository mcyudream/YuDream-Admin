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
  { label: '今天日期', value: 'date' },
  { label: '当前页面标题（承载宿主页时为其标题）', value: 'pageTitle' },
  { label: '当前时间', value: 'time' },
]
const accountOptions = [
  { label: '不绑定', value: '' },
  { label: '账号名', value: 'name' },
  { label: '头像（图片元素）', value: 'avatar' },
  { label: '账号首字（无头像时显示）', value: 'initial' },
]
const authOptions = [
  { label: '始终显示', value: '' },
  { label: '仅未登录时', value: 'guest' },
  { label: '仅登录后', value: 'user' },
]
const menuOptions = [
  { label: '无', value: '' },
  { label: '菜单容器（含触发项与子菜单）', value: 'data-yd-menu' },
  { label: '菜单触发项（链接或按钮）', value: 'data-yd-menu-trigger' },
  { label: '子菜单（非数据列表时）', value: 'data-yd-submenu' },
]
const menuRole = computed(() => menuOptions.find(option => option.value && option.value in attrs.value)?.value ?? '')
function setMenuRole(value: unknown) {
  setAttributes({ 'data-yd-menu': null, 'data-yd-menu-trigger': null, 'data-yd-submenu': null, ...(value ? { [String(value)]: 'true' } : {}) })
}
const sourceOptions = computed(() => [
  { label: '不绑定', value: '' },
  ...STUDIO_DATA_SOURCES.map(item => ({ label: item.label, value: item.value as string })),
  ...(store.doc?.features?.dataSources ?? []).map(item => ({ label: `插件适配器：${item.label}`, value: `adapter:${item.id}` })),
])

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
  return [{ label: '不绑定', value: '' }, ...(source?.fields ?? []).map(field => ({ label: field, value: field as string }))]
})
/** 插件适配器等未知字段的数据源：字段以文本输入（可用适配器「测试」查看字段） */
const freeFields = computed(() => fieldOptions.value.length <= 1)
const customSource = computed(() => {
  const value = attrs.value['data-yd-source'] || ''
  return value.startsWith('block:') && !STUDIO_DATA_SOURCES.some(item => item.value === value) ? value : ''
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

    <label class="text-xs flex flex-col gap-1">
      <span>显示数据源条数（如首页统计数字）</span>
      <YdSelect v-auth="'platform:cms:edit'" :model-value="attrs['data-yd-count'] || ''" :options="sourceOptions" aria-label="条数数据源" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-count': String(value || '') || null })" />
    </label>
    <div v-if="ancestorSource" class="gap-2 grid grid-cols-2">
      <label class="text-xs flex flex-col gap-1 min-w-0">
        <span>字段为真时显示</span>
        <YdInput :model-value="attrs['data-yd-show'] || ''" placeholder="如 online" aria-label="字段为真时显示" @change="(event: Event) => setAttributes({ 'data-yd-show': ((event.target as HTMLInputElement | null)?.value || '').trim() || null })" />
      </label>
      <label class="text-xs flex flex-col gap-1 min-w-0">
        <span>字段为真时隐藏</span>
        <YdInput :model-value="attrs['data-yd-hide'] || ''" placeholder="如 online" aria-label="字段为真时隐藏" @change="(event: Event) => setAttributes({ 'data-yd-hide': ((event.target as HTMLInputElement | null)?.value || '').trim() || null })" />
      </label>
    </div>
    <label v-if="ancestorSource" class="text-xs flex flex-col gap-1">
      <span>链接模板（由字段拼出地址，如 /site/activity?id={id}）</span>
      <YdInput :model-value="attrs['data-yd-link'] || ''" placeholder="/site/详情页?id={id}" aria-label="链接模板" @change="(event: Event) => setAttributes({ 'data-yd-link': ((event.target as HTMLInputElement | null)?.value || '').trim() || null })" />
    </label>

    <h3 class="text-xs text-muted-foreground font-medium mt-1">
      账号与菜单
    </h3>
    <div class="gap-2 grid grid-cols-2">
      <label class="text-xs flex flex-col gap-1 min-w-0">
        <span>显示条件</span>
        <YdSelect v-auth="'platform:cms:edit'" class="min-w-0 w-full" :model-value="attrs['data-yd-auth'] || ''" :options="authOptions" aria-label="登录状态显示条件" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-auth': String(value || '') || null })" />
      </label>
      <label class="text-xs flex flex-col gap-1 min-w-0">
        <span>账号信息</span>
        <YdSelect v-auth="'platform:cms:edit'" class="min-w-0 w-full" :model-value="attrs['data-yd-account'] || ''" :options="accountOptions" aria-label="账号信息绑定" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-account': String(value || '') || null })" />
      </label>
      <label class="text-xs flex flex-col gap-1 col-span-2 min-w-0">
        <span>菜单角色（子菜单配合样式类 parent-hover / parent-expanded 状态展开）</span>
        <YdSelect v-auth="'platform:cms:edit'" class="min-w-0 w-full" :model-value="menuRole" :options="menuOptions" aria-label="菜单角色" @update:model-value="setMenuRole" />
      </label>
    </div>

    <h3 class="text-xs text-muted-foreground font-medium mt-1">
      宿主页面承载
    </h3>
    <label class="text-xs flex gap-2 items-center">
      <YdSwitch :model-value="'data-yd-outlet' in attrs" aria-label="宿主页面插槽" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-outlet': value ? 'true' : null, 'data-yd-host-hide': null })" />
      <span>宿主页面插槽（承载 /wiki 等页面时内容显示在这里；普通访问时隐藏）</span>
    </label>
    <label class="text-xs flex gap-2 items-center">
      <YdSwitch :model-value="'data-yd-host-hide' in attrs" aria-label="承载时隐藏" @update:model-value="(value: unknown) => setAttributes({ 'data-yd-host-hide': value ? 'true' : null, 'data-yd-outlet': null })" />
      <span>承载宿主页面时隐藏本元素</span>
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
      <label class="text-xs flex flex-col gap-1">
        <span>嵌套列表字段（如二级菜单 children；容器内再放条目模板）</span>
        <YdInput :model-value="attrs['data-yd-each'] || ''" placeholder="children" aria-label="嵌套列表字段" @change="(event: Event) => setAttributes({ 'data-yd-each': ((event.target as HTMLInputElement | null)?.value || '').trim() || null })" />
      </label>
      <div class="gap-2 grid grid-cols-2">
        <label v-for="[key, label] in ([['data-yd-text', '文本'], ['data-yd-href', '链接'], ['data-yd-src', '图片'], ['data-yd-title', '提示/复制']] as const)" :key="key" class="text-xs flex flex-col gap-1 min-w-0">
          <span>{{ label }}字段</span>
          <YdInput
            v-if="freeFields"
            :model-value="attrs[key] || ''"
            placeholder="字段名"
            :aria-label="`${label}绑定字段`"
            @change="(event: Event) => setAttributes({ [key]: ((event.target as HTMLInputElement | null)?.value || '').trim() || null })"
          />
          <YdSelect
            v-else
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
