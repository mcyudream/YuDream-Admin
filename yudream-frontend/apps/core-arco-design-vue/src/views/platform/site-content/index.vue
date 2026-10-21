<script setup lang="ts">
// 站点内容：按当前公开站主题声明的内容模型（网站工作台「主题 → 站点内容模型」）可视化填写首页横幅、卡片列表等，
// 外加站点导航与公开页外观；全部写入同一站点草稿（set-features 命令），保存草稿 / 发布与网站工作台一致。
import type { ThemeConfigField as ThemeField } from '@/api/modules/platform-theme'
import type { StudioContentField } from '@/types/site-studio'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import apiTheme from '@/api/modules/platform-theme'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { STUDIO_PARTICLE_PRESETS } from '@/types/site-studio'
import { STUDIO_PARTICLE_LABELS } from '@/utils/site-studio-effects'
import { openStudioPreviewChannel, studioPagePathToSlug } from '@/utils/site-studio-preview'
import StudioNavigationEditor from '@/views/platform/site-studio/components/StudioNavigationEditor.vue'
import StudioPublicSkin from '@/views/platform/site-studio/components/StudioPublicSkin.vue'
import ThemeConfigField from '@/views/platform/theme-center/components/ThemeConfigField.vue'

defineOptions({ name: 'SiteContent' })

const store = useSiteStudioStore()
const router = useRouter()
const toast = useYdToast()
const modal = useYdModal()

const content = computed(() => store.doc?.features?.content)
const sections = computed(() => content.value?.sections ?? [])
const active = ref('')
const tabs = computed(() => [
  ...sections.value.map(section => ({ label: section.title, value: section.code, icon: 'morph:file-pen' })),
  { label: '站点导航', value: 'navigation', icon: 'morph:list-tree' },
  { label: '公开页外观', value: 'appearance', icon: 'morph:paintbrush' },
])
// 文档加载后默认打开第一个内容分区；分区被移除时回落到第一个可用分区
watch(tabs, (list, previous) => {
  const loadedNow = !previous?.length || previous.length === 2
  if (!list.some(tab => tab.value === active.value) || (loadedNow && sections.value.length && !sections.value.some(section => section.code === active.value))) {
    active.value = list[0]!.value
  }
}, { immediate: true })

/** 内容字段 → 主题配置字段渲染器（link 用文本输入；列表条目字段同样映射） */
function toThemeField(field: StudioContentField): ThemeField {
  return {
    key: field.key,
    label: field.label,
    type: field.type === 'link' ? 'text' : field.type === 'particles' ? 'select' : field.type,
    description: field.description,
    placeholder: field.placeholder ?? (field.type === 'link' ? '站点页 /site/...、宿主页 /wiki 或 https://…' : undefined),
    defaultValue: field.defaultValue,
    // 粒子字段：平台预设（列表条目里留空表示沿用站点默认）
    options: field.type === 'particles' ? STUDIO_PARTICLE_PRESETS.map(value => ({ label: STUDIO_PARTICLE_LABELS[value], value })) : field.options,
    itemFields: field.itemFields?.map(toThemeField),
  }
}

// 输入先进本地值，停顿后再写入草稿：一次停顿为一步撤销，校验失败（如链接不合法）就地提示且不落草稿
const pending = ref<Record<string, unknown>>({})
const errors = ref<Record<string, string>>({})
let timer: ReturnType<typeof setTimeout> | undefined
function valueOf(field: StudioContentField) {
  return field.key in pending.value ? pending.value[field.key] : content.value?.values[field.key] ?? field.defaultValue
}
function setValue(key: string, value: unknown) {
  pending.value = { ...pending.value, [key]: value }
  clearTimeout(timer)
  timer = setTimeout(flush, 400)
}
function flush(): boolean {
  clearTimeout(timer)
  const changes = pending.value
  if (!content.value || !Object.keys(changes).length) {
    return true
  }
  pending.value = {}
  const values = { ...content.value.values }
  for (const [key, value] of Object.entries(changes)) {
    if (value === undefined) {
      delete values[key]
    }
    else {
      values[key] = value
    }
  }
  try {
    store.execute({ type: 'set-features', features: { ...store.doc?.features, content: { ...content.value, values } } })
    errors.value = Object.fromEntries(Object.entries(errors.value).filter(([key]) => !(key in changes)))
    return true
  }
  catch (error) {
    const message = error instanceof Error ? error.message : '内容保存失败'
    errors.value = { ...errors.value, ...Object.fromEntries(Object.keys(changes).map(key => [key, message])) }
    pending.value = { ...changes, ...pending.value }
    return false
  }
}

// ===== 加载当前公开站主题的站点文档 =====
const activeTheme = ref('')
async function load() {
  try {
    const res = await apiTheme.overview()
    activeTheme.value = res.data.themes?.find(theme => theme.active)?.pluginCode || 'default'
  }
  catch {
    activeTheme.value = 'default'
  }
  if (!store.doc || store.themeCode !== activeTheme.value) {
    await store.load(activeTheme.value)
  }
}
void load()

async function saveDraft() {
  if (!flush()) {
    toast.error('请先修正站点内容中的输入错误')
    return
  }
  try {
    await store.saveDraft()
    toast.success('草稿已保存')
  }
  catch {
    toast.error(store.saveError || '保存草稿失败')
  }
}

function publish() {
  if (!flush()) {
    toast.error('请先修正站点内容中的输入错误')
    return
  }
  modal.confirm({
    title: '发布站点内容',
    content: '确认把当前草稿（含站点内容、导航与外观）发布到公开站吗？发布后可在网站工作台的发布版本页恢复。',
    confirmButtonText: '发布',
    onConfirm: async () => {
      try {
        await store.publish()
        toast.success('已发布到公开站')
      }
      catch {
        toast.error(store.saveError || '发布失败')
      }
    },
  })
}

// ===== 草稿预览：与网站工作台同一预览通道，预览页实时显示未保存的修改 =====
let previewChannel: BroadcastChannel | null = null
let previewTimer: ReturnType<typeof setTimeout> | undefined
function broadcast() {
  if (previewChannel && store.doc) {
    previewChannel.postMessage({ type: 'document', themeCode: store.themeCode, documentJson: JSON.stringify(store.doc), dirty: store.dirty, revision: store.state?.revision })
  }
}
watch(() => store.themeCode, (code) => {
  previewChannel?.close()
  previewChannel = openStudioPreviewChannel(code)
  if (previewChannel) {
    previewChannel.onmessage = (event: MessageEvent<{ type?: string, themeCode?: string }>) => {
      if (event.data?.type === 'request' && event.data.themeCode === store.themeCode) {
        broadcast()
      }
    }
  }
}, { immediate: true })
watch(() => [store.doc, store.dirty] as const, () => {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(broadcast, 250)
})
onBeforeUnmount(() => {
  clearTimeout(previewTimer)
  clearTimeout(timer)
  previewChannel?.close()
})
function openPreview() {
  if (!flush()) {
    toast.error('请先修正站点内容中的输入错误')
    return
  }
  const home = store.doc?.pages.find(page => page.path === '/') ?? store.doc?.pages[0]
  window.open(router.resolve({ name: 'siteStudioPreview', params: { themeCode: store.themeCode, slug: studioPagePathToSlug(home?.path ?? '/') } }).href, '_blank')
}

onBeforeRouteLeave(() => {
  flush()
  if (!store.dirty && !Object.keys(pending.value).length) {
    return true
  }
  return new Promise<boolean>((resolve) => {
    modal.confirm({
      title: '离开站点内容？',
      content: Object.keys(pending.value).length ? '有未通过校验的输入，离开后会丢失。请先修正，或确认放弃。' : '有未保存的修改，离开后会丢失。可先保存草稿再离开。',
      confirmButtonText: '不保存并离开',
      cancelButtonText: '留在本页',
      onConfirm: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
})
</script>

<template>
  <div class="settings-page-shell">
    <YdPageHeader title="站点内容" :description="`修改公开站的横幅、首页内容、导航与外观（主题：${store.themeCode}）；保存为草稿，发布后对访客生效。`" class="mb-0">
      <YdButton variant="outline" :disabled="!store.doc" @click="openPreview">
        <YdIcon name="morph:eye" />
        预览
      </YdButton>
      <YdButton v-auth="'platform:cms:edit'" variant="outline" :disabled="!store.dirty || store.busy" :loading="store.saving" @click="saveDraft">
        <YdIcon name="morph:save" />
        保存草稿
      </YdButton>
      <YdButton v-auth="'platform:cms:publish'" :disabled="!store.doc || store.busy" :loading="store.publishing" @click="publish">
        <YdIcon name="morph:send" />
        发布
      </YdButton>
    </YdPageHeader>

    <YdLoadingOverlay :loading="store.loading">
      <YdResult v-if="store.loadError" status="error" title="站点内容加载失败" :description="store.loadError">
        <template #actions>
          <YdButton variant="outline" @click="load">
            重试
          </YdButton>
        </template>
      </YdResult>
      <YdSettingsLayout v-else-if="store.doc" v-model="active" :list="tabs" surface scroll width-class="max-w-[960px]">
        <template v-for="section in sections" :key="section.code" #[section.code]>
          <YdSettingsSection :title="section.title" :description="section.description">
            <div class="flex flex-col gap-5">
              <div v-for="field in section.fields" :key="field.key" class="flex flex-col gap-1">
                <ThemeConfigField :field="toThemeField(field)" :model-value="valueOf(field)" :plugin-code="store.themeCode" :empty-means-default="false" @update:model-value="setValue(field.key, $event)" />
                <YdButton v-if="content?.values[field.key] !== undefined" variant="ghost" size="sm" class="self-start" @click="setValue(field.key, undefined)">
                  恢复主题默认
                </YdButton>
                <p v-if="errors[field.key]" class="text-xs text-destructive m-0" role="alert">
                  {{ errors[field.key] }}
                </p>
              </div>
            </div>
          </YdSettingsSection>
        </template>
        <template #navigation>
          <StudioNavigationEditor />
        </template>
        <template #appearance>
          <StudioPublicSkin />
        </template>
      </YdSettingsLayout>
      <YdEmpty v-if="store.doc && !sections.length" description="当前主题还没有声明站点内容模型">
        <YdButton variant="outline" @click="router.push('/platform/site-studio')">
          去网站工作台声明（主题 → 站点内容模型）
        </YdButton>
      </YdEmpty>
    </YdLoadingOverlay>
  </div>
</template>
