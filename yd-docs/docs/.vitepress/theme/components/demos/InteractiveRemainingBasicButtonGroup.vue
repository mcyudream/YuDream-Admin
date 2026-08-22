<script setup lang="ts">
import { ref } from 'vue'
import { FaButton, FaButtonGroup } from '@yudream/components'

const feedback = ref('请选择一个操作')
</script>

<template>
  <div class="space-y-4">
    <div class="demo-row">
      <FaButtonGroup>
        <FaButton @click="feedback = '已查看详情'">查看详情</FaButton>
        <FaButton @click="feedback = '已进入编辑'">编辑</FaButton>
        <FaButton @click="feedback = '已标记删除'">删除</FaButton>
      </FaButtonGroup>
      <span class="text-sm text-muted-foreground">{{ feedback }}</span>
    </div>
    <div class="demo-row">
      <FaButtonGroup separator>
        <FaButton variant="outline" @click="feedback = '已复制'">复制</FaButton>
        <FaButton variant="outline" @click="feedback = '已剪切'">剪切</FaButton>
        <FaButton variant="outline" @click="feedback = '已粘贴'">粘贴</FaButton>
      </FaButtonGroup>
    </div>
    <div class="demo-row">
      <FaButtonGroup orientation="vertical" separator>
        <FaButton size="sm" variant="ghost" @click="feedback = '已切换粗体'">粗体</FaButton>
        <FaButton size="sm" variant="ghost" @click="feedback = '已切换斜体'">斜体</FaButton>
        <FaButton size="sm" variant="ghost" @click="feedback = '已切换下划线'">下划线</FaButton>
      </FaButtonGroup>
    </div>
  </div>
</template>
