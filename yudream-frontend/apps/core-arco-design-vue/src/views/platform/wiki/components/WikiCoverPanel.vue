<script setup lang="ts">
import type { WikiSpaceCoverFields, WikiSpaceCoverType } from '../wiki-admin-api'
import { computed, inject, ref, watch } from 'vue'
import apiFiles from '@/api/modules/files'
import {
  clearWikiSpaceCover,
  randomWikiSpaceCover,
  updateWikiSpaceCover,
} from '../wiki-admin-api'
import { wikiCoverOf, wikiCoverStyle } from '../wiki-cover'
import { wikiWorkbenchKey } from '../wiki-utils'

/**
 * 空间封面管理：随机 seed 封面 / 上传图片封面 / 替代文本 / 移除封面。
 *
 * 后端契约（WikiSpaceCoverAppService）：
 * - 随机封面 seed 由服务端生成，前端无法指定展示内容；
 * - 上传封面必须是「当前用户上传、未删除、图片 MIME、≤5MB、公开可读」的文件对象，
 *   因此这里用系统文件接口按公开读上传（module=wiki-cover），再由封面接口引用文件 ID；
 * - 移除封面只清空引用，不删除文件对象（文件可能被其他内容共享）。
 */
const props = defineProps<{
  /** 紧凑模式：嵌在设置分区里，减少外层留白 */
  embedded?: boolean
}>()

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const { auth } = useAppAuth()

const MAX_COVER_SIZE = 5 * 1024 * 1024

const saving = ref(false)
const uploading = ref(false)
const clearing = ref(false)
const altInput = ref('')

const cover = computed(() => wikiCoverOf(store.space.value))
const coverStyle = computed(() => wikiCoverStyle(store.space.value))
const canEdit = computed(() => auth('platform:wiki:cover:edit'))
const canDelete = computed(() => auth('platform:wiki:cover:delete'))
const busy = computed(() => saving.value || uploading.value || clearing.value)
const typeLabel = computed(() => {
  if (cover.value.type === 'UPLOAD') {
    return '上传封面'
  }
  return cover.value.type === 'SEEDED' ? '随机封面' : '未设置封面'
})

watch(cover, (value) => {
  altInput.value = value.alt
}, { immediate: true })

/** 应用封面接口返回的最新空间字段（原地更新当前空间，避免整列表刷新后治理面板重建） */
function applyCoverResult(result: WikiSpaceCoverFields | undefined) {
  const space = store.space.value
  if (!space || !result) {
    return
  }
  const record = space as unknown as Record<string, unknown>
  record.coverType = result.coverType
  record.coverSeed = result.coverSeed
  record.coverFileObjectId = result.coverFileObjectId
  record.coverAlt = result.coverAlt
}

async function applyCover(payload: { coverType: WikiSpaceCoverType, fileObjectId?: string, seed?: string, alt?: string }) {
  const spaceId = store.spaceId.value
  if (!spaceId) {
    return
  }
  saving.value = true
  try {
    const response = await updateWikiSpaceCover(spaceId, payload)
    applyCoverResult(response.data)
    toast.success('封面已更新')
  }
  finally {
    saving.value = false
  }
}

async function randomCover() {
  const spaceId = store.spaceId.value
  if (!spaceId || busy.value) {
    return
  }
  saving.value = true
  try {
    const response = await randomWikiSpaceCover(spaceId, altInput.value.trim() || undefined)
    applyCoverResult(response.data)
    toast.success('已生成新的随机封面（同一 seed 呈现稳定）')
  }
  finally {
    saving.value = false
  }
}

/** 上传：按公开读上传到系统文件接口，再引用文件对象；后端会再次校验归属/类型/大小/公开读 */
async function onPickFile(files: File[]) {
  const file = files[0]
  const spaceId = store.spaceId.value
  if (!file || !spaceId) {
    return
  }
  if (!file.type.startsWith('image/')) {
    toast.warning('封面只支持图片文件')
    return
  }
  if (file.size > MAX_COVER_SIZE) {
    toast.warning('封面图片不能超过 5MB')
    return
  }
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    form.append('module', 'wiki-cover')
    // 封面必须公开可读：后端 UPLOAD 封面校验 publicAccess=true，这里不能绕过校验另存私有文件
    form.append('publicAccess', 'true')
    const uploaded = await apiFiles.upload(form)
    const fileObjectId = uploaded.data?.id
    if (!fileObjectId) {
      throw new Error('图片上传后未返回文件对象 ID')
    }
    const response = await updateWikiSpaceCover(spaceId, {
      coverType: 'UPLOAD',
      fileObjectId: String(fileObjectId),
      alt: altInput.value.trim() || undefined,
    })
    applyCoverResult(response.data)
    toast.success('封面已更新')
  }
  catch {
    // 上传或校验失败的具体原因由接口层提示（toast），这里保持封面不变
  }
  finally {
    uploading.value = false
  }
}

function saveAlt() {
  if (cover.value.type === 'NONE') {
    return
  }
  void applyCover({
    coverType: cover.value.type,
    seed: cover.value.type === 'SEEDED' ? cover.value.seed : undefined,
    fileObjectId: cover.value.type === 'UPLOAD' ? cover.value.fileObjectId : undefined,
    alt: altInput.value.trim() || undefined,
  })
}

function removeCover() {
  const spaceId = store.spaceId.value
  if (!spaceId || busy.value) {
    return
  }
  modal.confirm({
    title: '移除封面',
    content: '确定移除当前封面吗？只会清空知识库的封面引用，不会删除可能被其他内容共享的图片文件。',
    onConfirm: async () => {
      clearing.value = true
      try {
        const response = await clearWikiSpaceCover(spaceId)
        applyCoverResult(response.data)
        toast.success('封面已移除')
      }
      finally {
        clearing.value = false
      }
    },
  })
}
</script>

<template>
  <div class="cover-panel" :class="{ 'cover-panel--embedded': props.embedded }">
    <div class="cover-panel__preview">
      <!-- 16:9 预览：UPLOAD 呈现真实图片，SEEDED 按 seed 确定性渐变，NONE 明确标注未设置 -->
      <div class="cover-preview" :style="coverStyle">
        <img
          v-if="cover.hasImage"
          :src="cover.url"
          :alt="cover.alt || `${store.space.value?.name || '知识库'} 封面`"
          class="cover-preview__img"
        >
        <template v-else>
          <YdIcon name="morph:image" class="cover-preview__icon" />
          <span class="cover-preview__initial">{{ (store.space.value?.name || '?').trim().slice(0, 1).toUpperCase() }}</span>
        </template>
        <span class="cover-preview__type">{{ typeLabel }}</span>
      </div>
      <p class="cover-panel__note">
        <template v-if="cover.type === 'UPLOAD'">
          封面来自公开可读的图片文件（文件对象 #{{ cover.fileObjectId }}）；移除封面不会删除该文件。
        </template>
        <template v-else-if="cover.type === 'SEEDED'">
          seed 由服务端生成并持久化：`{{ cover.seed }}`，同一 seed 每次渲染结果一致，不会刷新跳变。
        </template>
        <template v-else>
          尚未设置封面：公开端与入口页按名称哈希显示占位图案，不代表已有封面。
        </template>
      </p>
    </div>

    <div class="cover-panel__form">
      <label class="cover-field">
        <span>替代文本（alt）</span>
        <YdInput
          v-model="altInput"
          :disabled="!canEdit || cover.type === 'NONE'"
          maxlength="200"
          placeholder="描述封面内容，供无障碍与图片加载失败时展示"
          aria-label="封面替代文本"
        />
      </label>

      <div class="cover-actions">
        <YdButton size="sm" :disabled="!canEdit || busy || cover.type === 'NONE'" :loading="saving" @click="saveAlt">
          <YdIcon name="morph:save" /> 保存替代文本
        </YdButton>
        <YdButton size="sm" variant="outline" :disabled="!canEdit || busy" :loading="saving" @click="randomCover">
          <YdIcon name="morph:dices" /> 随机封面
        </YdButton>
        <YdFilePicker accept="image/*" label="选择封面图片" :disabled="!canEdit || busy" @select="onPickFile">
          <template #default="{ open, disabled, label, triggerAttrs }">
            <YdButton
              v-bind="triggerAttrs"
              size="sm"
              variant="outline"
              :disabled="disabled"
              :loading="uploading"
              @click="open"
            >
              <YdIcon name="morph:upload" /> {{ uploading ? '上传中' : label }}
            </YdButton>
          </template>
        </YdFilePicker>
        <YdButton
          size="sm"
          variant="ghost"
          class="cover-danger"
          :disabled="!canDelete || busy || cover.type === 'NONE'"
          :loading="clearing"
          @click="removeCover"
        >
          <YdIcon name="morph:trash-2" /> 移除封面
        </YdButton>
      </div>

      <p class="cover-panel__hint">
        <YdIcon name="morph:shield-alert" />
        上传的封面会按「公开可读」图片保存，公开站可直接访问；后端会校验文件归属、图片类型、大小（≤5MB）与公开读标记，
        校验不通过会拒绝引用。随机封面与上传封面都只影响展示，不改动页面内容与索引。
      </p>
    </div>
  </div>
</template>

<style scoped>
.cover-panel {
  display: grid;
  grid-template-columns: minmax(0, 320px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.cover-panel--embedded {
  grid-template-columns: minmax(0, 280px) minmax(0, 1fr);
}

.cover-panel__preview {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.cover-preview {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background-color: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.cover-preview__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-preview__icon {
  position: absolute;
  font-size: 22px;
  color: rgb(255 255 255 / 70%);
}

.cover-preview__initial {
  font-size: 40px;
  font-weight: 700;
  color: rgb(255 255 255 / 88%);
}

.cover-preview__type {
  position: absolute;
  bottom: 8px;
  left: 8px;
  padding: 1px 8px;
  font-size: 11px;
  color: rgb(255 255 255 / 92%);
  background: rgb(0 0 0 / 32%);
  border-radius: 999px;
}

.cover-panel__note {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.cover-panel__form {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.cover-field {
  display: grid;
  gap: 6px;
}

.cover-field > span {
  font-size: 13px;
  color: var(--color-text-3);
}

.cover-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.cover-danger {
  color: rgb(var(--danger-6));
}

.cover-panel__hint {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.cover-panel__hint :deep(svg) {
  flex-shrink: 0;
  margin-top: 3px;
}

@media (width <= 900px) {
  .cover-panel,
  .cover-panel--embedded {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
