<script setup lang="ts">
import type { FileItem, FileUploadRequestOptions } from '@yudream/components'
import { useYdToast } from '@yudream/components'
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const toast = useYdToast()

const fileList = ref<FileItem[]>([
  { name: '季度报告.pdf', size: 1_284_311, status: 'success' },
  { name: '素材归档.zip', size: 24_576_000, status: 'error', file: new File(['demo'], '素材归档.zip') },
])

const imageList = ref<string[]>([])

/** 演示用自定义上传：读取本地文件生成 blob 链接，模拟 800ms 上传进度 */
function mockHttpRequest(options: FileUploadRequestOptions): Promise<string> {
  return new Promise((resolve) => {
    let percent = 0
    const timer = setInterval(() => {
      percent = Math.min(100, percent + 12 + Math.round(Math.random() * 10))
      options.onProgress(percent)
      if (percent >= 100) {
        clearInterval(timer)
        resolve(URL.createObjectURL(options.file))
      }
    }, 80)
  })
}

function extractUrl(url: string) {
  return url
}
</script>

<template>
  <DemoCard
    title="YdFileUpload"
    description="文件上传：拖拽 / 点击 / 粘贴，v-model 绑定 FileItem[]；httpRequest 自定义上传请求，max 控制数量，directory 支持文件夹。列表为满宽行：类型图标 + 名称 + 大小/进度，失败行可重试。"
  >
    <div class="gap-6 grid max-w-xl">
      <YdFileUpload
        v-model="fileList"
        :http-request="mockHttpRequest"
        :after-upload="extractUrl"
        multiple
        description="拖放或点击上传，演示使用本地 mock 上传"
      />
      <div class="flex gap-2">
        <YdButton size="sm" variant="outline" @click="toast(`当前 ${fileList.length} 个文件`)">
          查看文件列表
        </YdButton>
        <YdButton size="sm" variant="ghost" @click="fileList = []">
          清空
        </YdButton>
      </div>
    </div>
  </DemoCard>

  <DemoCard
    title="YdImageUpload"
    description="图片上传：v-model 绑定 URL 数组，支持粘贴上传（Ctrl+V）与排序；width / height 控制预览尺寸"
  >
    <div class="flex flex-wrap gap-6 items-start">
      <div>
        <p class="text-xs text-muted-foreground mb-2">
          单图（默认 max=1）
        </p>
        <YdImageUpload
          v-model="imageList"
          :http-request="mockHttpRequest"
          :after-upload="extractUrl"
          :width="120"
          :height="120"
        />
      </div>
      <div>
        <p class="text-xs text-muted-foreground mb-2">
          多图（max=3）
        </p>
        <YdImageUpload
          :model-value="[]"
          :http-request="mockHttpRequest"
          :after-upload="extractUrl"
          multiple
          :max="3"
          :width="80"
          :height="80"
        />
      </div>
    </div>
  </DemoCard>
</template>
