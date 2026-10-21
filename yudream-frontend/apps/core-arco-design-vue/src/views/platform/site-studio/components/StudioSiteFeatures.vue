<script setup lang="ts">
// 站点功能：平台在公开站实现的受控效果（粒子、背景音乐与音效、页面进入提示），文档不含脚本。
// 是否使用、取什么值由主题在「站点内容模型」中声明 effect 字段决定，运营在「站点内容」页修改；
// 这里只说明机制、列出已声明的效果，并把旧版写在框架字段里的设置一键迁移进内容模型。
import { computed } from 'vue'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { declaredStudioEffects, hasLegacyStudioEffects, migrateStudioEffects, STUDIO_EFFECT_LABELS } from '@/utils/site-studio-effects'

defineOptions({ name: 'StudioSiteFeatures' })

const store = useSiteStudioStore()
const toast = useYdToast()
const router = useRouter()

const declared = computed(() => store.doc ? declaredStudioEffects(store.doc) : [])
const legacy = computed(() => Boolean(store.doc && hasLegacyStudioEffects(store.doc)))

function migrate() {
  if (!store.doc) {
    return
  }
  const { commands, skipped } = migrateStudioEffects(store.doc)
  try {
    if (commands.length) {
      store.execute(commands)
      toast.success(skipped.length ? '已迁移可转换的设置，其余保留旧配置' : '已迁移到站点内容模型，可在「站点内容」页修改')
    }
    else if (!skipped.length) {
      toast.info('没有可迁移的设置')
    }
    skipped.forEach(message => toast.warning(message))
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '迁移失败')
  }
}
</script>

<template>
  <section class="flex flex-col gap-3" aria-label="站点功能">
    <h2 class="text-sm font-semibold m-0">
      站点功能
    </h2>
    <p class="text-xs text-muted-foreground m-0">
      由平台在公开站实现的受控效果，不需要也不允许写脚本。音乐开关：给按钮加属性 <code class="font-mono">data-yd-action="toggle-music"</code>，
      音效开关为 <code class="font-mono">toggle-sound</code>；开启状态写入 <code class="font-mono">aria-pressed</code>，可用样式类的 pressed 状态做高亮。
    </p>
    <p class="text-xs text-muted-foreground m-0">
      是否使用这些效果、取什么值由主题决定：在「站点内容模型」中给字段声明 <code class="font-mono">effect</code>
      （可在「站点内容模型」中直接添加），运营在「站点内容」页修改。
      没有声明的效果不会出现。
    </p>
    <div v-if="declared.length" class="flex flex-wrap gap-1">
      <YdTag v-for="item in declared" :key="item.field.key" variant="secondary">
        {{ STUDIO_EFFECT_LABELS[item.field.effect!] }} · {{ item.section }} / {{ item.field.label }}
      </YdTag>
    </div>
    <p v-else class="text-xs m-0">
      当前主题未声明站点功能。
    </p>
    <YdAlert v-if="legacy" title="还有旧版站点功能设置">
      <template #description>
        <div class="flex flex-col gap-2">
          <span>旧版设置可以迁入内容模型；无法转换的内嵌资源会保留，上传公开文件后再处理。迁移仅修改草稿，可撤销，不会自动发布。</span>
          <div>
            <YdButton v-auth="'platform:cms:edit'" size="sm" @click="migrate">
              迁移到站点内容模型
            </YdButton>
          </div>
        </div>
      </template>
    </YdAlert>
    <div>
      <YdButton variant="outline" size="sm" :disabled="!declared.length" @click="router.push('/platform/site-content')">
        <YdIcon name="morph:file-pen" /> 去站点内容修改
      </YdButton>
    </div>
  </section>
</template>
