<script setup lang="ts">
// 插件数据适配器：把插件的匿名公开接口声明为站点数据源 adapter:{编码}，页面用数据绑定取用（列表或详情）。
// 只允许 /api/plugins/{插件}/public/... 的 GET；{参数} 取页面地址查询参数（详情页 ?id=）。修改经 set-features 命令。
import type { StudioDataSource } from '@/types/site-studio'
import { computed, ref } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { toBackendAssetUrl } from '@/utils/backend-url'

defineOptions({ name: 'StudioDataSources' })

const store = useSiteStudioStore()
const toast = useYdToast()

const sources = computed<StudioDataSource[]>(() => store.doc?.features?.dataSources ?? [])
const testParams = ref<Record<string, string>>({})
const testResult = ref<Record<string, string>>({})

function apply(next: StudioDataSource[]) {
  const features = { ...store.doc?.features }
  if (next.length) {
    features.dataSources = next
  }
  else {
    delete features.dataSources
  }
  try {
    store.execute({ type: 'set-features', features })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '适配器保存失败')
  }
}

function update(index: number, patch: Partial<StudioDataSource>) {
  const next = sources.value.map((item, position) => position === index ? { ...item, ...patch } : item)
  const item = next[index]!
  if (!item.recordsPath) {
    delete item.recordsPath
  }
  if (!item.single) {
    delete item.single
  }
  if (item.fields && !Object.keys(item.fields).length) {
    delete item.fields
  }
  apply(next)
}

function add() {
  const index = sources.value.length + 1
  apply([...sources.value, { id: `source-${index}`, label: `数据源 ${index}`, endpoint: '/api/plugins/plugin-code/public/items' }])
}

function remove(index: number) {
  apply(sources.value.filter((_, position) => position !== index))
}

function value(event: Event) {
  return ((event.target as HTMLInputElement | HTMLTextAreaElement | null)?.value ?? '').trim()
}

/** 字段映射文本：每行「模板字段=源字段路径」 */
function fieldsText(item: StudioDataSource) {
  return Object.entries(item.fields ?? {}).map(([target, source]) => `${target}=${source}`).join('\n')
}
function parseFields(text: string) {
  return Object.fromEntries(text.split('\n').map(line => line.split('=').map(part => part.trim())).filter(parts => parts.length === 2 && parts[0] && parts[1]))
}

function paramNames(endpoint: string) {
  return [...endpoint.matchAll(/\{(\w+)\}/g)].map(match => match[1]!)
}

/** 试取接口：展示首条记录的字段，便于设置绑定与映射（同样只读取公开接口、不带凭据） */
async function test(item: StudioDataSource) {
  let missing = ''
  const path = item.endpoint.replace(/\{(\w+)\}/g, (_whole, name: string) => {
    const param = testParams.value[`${item.id}:${name}`]
    if (!param) {
      missing = name
    }
    return encodeURIComponent(param ?? '')
  })
  if (missing) {
    toast.error(`先填写测试参数 ${missing}`)
    return
  }
  try {
    const response = await fetch(toBackendAssetUrl(path), { credentials: 'omit', headers: { Accept: 'application/json' } })
    let body: any = await response.json()
    if (body && typeof body === 'object' && 'code' in body) {
      body = Number(body.code) === 200 ? body.data : { 错误: body.message }
    }
    const list = item.single ? [body] : (item.recordsPath ? item.recordsPath.split('.').reduce((current: any, key) => current?.[key], body) : body)
    const first = Array.isArray(list) ? list[0] : (Array.isArray(list?.records) ? list.records[0] : Array.isArray(list?.items) ? list.items[0] : list)
    testResult.value = { ...testResult.value, [item.id]: JSON.stringify(first ?? null, null, 2).slice(0, 1200) }
  }
  catch (error) {
    testResult.value = { ...testResult.value, [item.id]: error instanceof Error ? error.message : '请求失败' }
  }
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="插件数据适配器">
    <h2 class="text-sm font-semibold m-0">
      插件数据适配器
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      把插件的公开接口接入站点：页面数据绑定选择 <code class="font-mono">adapter:编码</code> 即可取用。接口须为
      <code class="font-mono">/api/plugins/插件/public/...</code>（匿名、只读）；路径里的 <code class="font-mono">{id}</code> 等参数取自页面地址查询参数，
      适合做详情页（列表条目用链接模板 <code class="font-mono">/site/详情页?id={id}</code> 跳转）。插件主题块（服务器列表、活动广场、大事记）无需在此配置。
    </p>
    <div v-for="(item, index) in sources" :key="item.id" class="p-2 border rounded flex flex-col gap-2">
      <div class="flex flex-wrap gap-2 items-center">
        <YdInput :model-value="item.id" class="w-32" aria-label="适配器编码" @change="(event: Event) => value(event) && update(index, { id: value(event) })" />
        <YdInput :model-value="item.label" class="w-40" aria-label="适配器名称" @change="(event: Event) => value(event) && update(index, { label: value(event) })" />
        <code class="text-xs font-mono">adapter:{{ item.id }}</code>
        <span class="flex-1" />
        <YdButton v-auth="'platform:cms:edit'" variant="ghost" size="sm" aria-label="删除适配器" @click="remove(index)">
          <YdIcon name="morph:trash-2" />
        </YdButton>
      </div>
      <YdInput :model-value="item.endpoint" aria-label="接口路径" placeholder="/api/plugins/timeline/public/events/{id}" @change="(event: Event) => value(event) && update(index, { endpoint: value(event) })" />
      <div class="flex flex-wrap gap-2 items-center">
        <label class="text-xs flex gap-2 items-center">
          <YdSwitch :model-value="Boolean(item.single)" aria-label="单条记录" @update:model-value="(next: unknown) => update(index, { single: Boolean(next) })" />
          <span>结果是单条记录（详情）</span>
        </label>
        <YdInput v-if="!item.single" :model-value="item.recordsPath ?? ''" class="w-48" placeholder="列表路径，如 records" aria-label="列表路径" @change="(event: Event) => update(index, { recordsPath: value(event) })" />
      </div>
      <YdTextarea :model-value="fieldsText(item)" :rows="3" placeholder="字段映射（可选），每行 模板字段=源字段，如 time=dateLabel" aria-label="字段映射" @change="(event: Event) => update(index, { fields: parseFields(value(event)) })" />
      <div class="flex flex-wrap gap-2 items-center">
        <YdInput
          v-for="name in paramNames(item.endpoint)"
          :key="name"
          v-model="testParams[`${item.id}:${name}`]"
          class="w-56"
          :placeholder="`测试参数 ${name}`"
          :aria-label="`测试参数 ${name}`"
        />
        <YdButton variant="outline" size="sm" @click="test(item)">
          <YdIcon name="morph:flask-conical" /> 测试
        </YdButton>
      </div>
      <pre v-if="testResult[item.id]" class="text-xs m-0 p-2 bg-muted/40 max-h-48 overflow-auto">{{ testResult[item.id] }}</pre>
    </div>
    <div>
      <YdButton v-auth="'platform:cms:edit'" variant="outline" size="sm" :disabled="sources.length >= 20 || !store.doc" @click="add">
        <YdIcon name="morph:plus" /> 添加适配器
      </YdButton>
    </div>
  </section>
</template>
