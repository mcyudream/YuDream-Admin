<script setup lang="ts">
// 站点导航编辑：两级导航（名称、地址、图标），随站点草稿保存与发布。页面里绑定 site:navigation 的列表
// （如底部物品栏）据此渲染，二级条目用条目内 data-yd-each="children" 展开。所有修改经 set-features 命令。
import type { StudioNavigation, StudioNavItem } from '@/types/site-studio'
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'

defineOptions({ name: 'StudioNavigationEditor' })

const store = useSiteStudioStore()
const toast = useYdToast()

const navigation = computed<StudioNavigation | undefined>(() => store.doc?.features?.navigation)
const items = computed(() => navigation.value?.items ?? [])
const iconOptions = computed(() => [{ label: '无（用页面图标或默认图标）', value: '' }, ...(store.doc?.assets ?? []).filter(asset => asset.kind === 'image').map(asset => ({ label: asset.name, value: asset.id }))])
const pageOptions = computed(() => (store.doc?.pages ?? []).map(page => ({ label: `站点页面：${page.name}`, value: page.path === '/' ? '/site' : `/site${page.path}` })))

function apply(next: StudioNavigation | undefined) {
  const features = { ...store.doc?.features }
  if (next) {
    features.navigation = next
  }
  else {
    delete features.navigation
  }
  try {
    store.execute({ type: 'set-features', features })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '导航保存失败')
  }
}

const newItem = (label: string, url: string): StudioNavItem => ({ id: `nav-${crypto.randomUUID().slice(0, 8)}`, label, url })

/** 由站点页面生成初始导航，便于在此基础上调整 */
function initialize() {
  apply({ appendAuto: true, items: pageOptions.value.map(option => newItem(option.label.replace('站点页面：', ''), option.value)) })
}

function update(path: number[], patch: Partial<StudioNavItem> | null) {
  const next = structuredClone(items.value) as StudioNavItem[]
  const [top, child] = path
  const list = child === undefined ? next : (next[top!]!.children ??= [])
  const index = child === undefined ? top! : child
  if (patch === null) {
    list.splice(index, 1)
  }
  else {
    const merged = { ...list[index]!, ...patch }
    if (!merged.iconAssetId) {
      delete merged.iconAssetId
    }
    list[index] = merged
  }
  if (child !== undefined && !next[top!]!.children!.length) {
    delete next[top!]!.children
  }
  apply({ appendAuto: navigation.value?.appendAuto ?? true, items: next })
}

function move(path: number[], offset: number) {
  const next = structuredClone(items.value) as StudioNavItem[]
  const [top, child] = path
  const list = child === undefined ? next : next[top!]!.children!
  const index = child === undefined ? top! : child
  const target = index + offset
  if (target < 0 || target >= list.length) {
    return
  }
  [list[index], list[target]] = [list[target]!, list[index]!]
  apply({ appendAuto: navigation.value?.appendAuto ?? true, items: next })
}

function addTop() {
  apply({ appendAuto: navigation.value?.appendAuto ?? true, items: [...items.value, newItem('新导航', '/site')] })
}

function addChild(top: number) {
  const next = structuredClone(items.value) as StudioNavItem[]
  ;(next[top]!.children ??= []).push(newItem('子菜单', next[top]!.url))
  apply({ appendAuto: navigation.value?.appendAuto ?? true, items: next })
}

function text(event: Event) {
  return ((event.target as HTMLInputElement | null)?.value ?? '').trim()
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="站点导航">
    <h2 class="text-sm font-semibold m-0">
      站点导航
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      页面中绑定「站点导航」数据源的列表据此显示（如底部物品栏），二级条目以子菜单展开。地址可填站点页面（/site/...）、宿主页面（/wiki、/market、插件页）或 http(s) 外链。
      未设置时使用「主题中心 → 导航」与站点页面自动生成。
    </p>
    <div v-if="!navigation" class="flex flex-wrap gap-2">
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.doc" @click="initialize">
        <YdIcon name="morph:list-tree" /> 从站点页面生成导航
      </YdButton>
    </div>
    <template v-else>
      <label class="text-xs flex gap-2 items-center">
        <YdSwitch :model-value="navigation.appendAuto" aria-label="自动追加插件页面" @update:model-value="(value: unknown) => apply({ ...navigation!, appendAuto: Boolean(value) })" />
        <span>在后面自动追加插件页面、插件市场与知识库入口（按地址去重）</span>
      </label>
      <div v-for="(item, top) in items" :key="item.id" class="p-2 border rounded flex flex-col gap-2">
        <div class="flex flex-wrap gap-2 items-center">
          <YdInput :model-value="item.label" class="w-32" aria-label="导航名称" @change="(event: Event) => text(event) && update([top], { label: text(event) })" />
          <YdInput :model-value="item.url" class="flex-1 min-w-40" aria-label="导航地址" @change="(event: Event) => text(event) && update([top], { url: text(event) })" />
          <YdSelect class="w-44" :model-value="item.iconAssetId ?? ''" :options="iconOptions" aria-label="导航图标" @update:model-value="(value: unknown) => update([top], { iconAssetId: String(value || '') })" />
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="上移" :disabled="top === 0" @click="move([top], -1)">
            <YdIcon name="morph:arrow-up" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="下移" :disabled="top === items.length - 1" @click="move([top], 1)">
            <YdIcon name="morph:arrow-down" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="添加子菜单" @click="addChild(top)">
            <YdIcon name="morph:list-plus" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除导航" @click="update([top], null)">
            <YdIcon name="morph:trash-2" />
          </YdButton>
        </div>
        <div v-for="(child, index) in item.children ?? []" :key="child.id" class="pl-6 flex flex-wrap gap-2 items-center">
          <YdInput :model-value="child.label" class="w-32" aria-label="子菜单名称" @change="(event: Event) => text(event) && update([top, index], { label: text(event) })" />
          <YdInput :model-value="child.url" class="flex-1 min-w-40" aria-label="子菜单地址" @change="(event: Event) => text(event) && update([top, index], { url: text(event) })" />
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="上移子菜单" :disabled="index === 0" @click="move([top, index], -1)">
            <YdIcon name="morph:arrow-up" />
          </YdButton>
          <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除子菜单" @click="update([top, index], null)">
            <YdIcon name="morph:trash-2" />
          </YdButton>
        </div>
      </div>
      <div class="flex flex-wrap gap-2">
        <YdButton v-auth="'platform:cms:edit'" variant="outline" size="sm" :disabled="items.length >= 30" @click="addTop">
          <YdIcon name="morph:plus" /> 添加导航
        </YdButton>
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" @click="apply(undefined)">
          改回自动导航
        </YdButton>
      </div>
      <p class="text-xs text-muted-foreground m-0">
        可用的站点页面地址：<code v-for="option in pageOptions" :key="option.value" class="font-mono mr-2">{{ option.value }}</code>
      </p>
    </template>
  </section>
</template>
