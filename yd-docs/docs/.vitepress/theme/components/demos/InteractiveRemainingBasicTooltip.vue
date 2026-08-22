<script setup lang="ts">
import { FaButton, FaTooltip } from '@yudream/components'
</script>

<template>
  <div class="demo-row">
    <FaTooltip text="注意噢！">
      <FaButton variant="outline">悬停或聚焦查看提示</FaButton>
    </FaTooltip>
    <FaTooltip text="上方提示" side="top">
      <FaButton variant="outline">Top</FaButton>
    </FaTooltip>
    <FaTooltip text="下方提示" side="bottom">
      <FaButton variant="outline">Bottom</FaButton>
    </FaTooltip>
    <FaTooltip>
      <FaButton variant="outline">自定义内容</FaButton>
      <template #content><p>支持 <strong>富文本</strong> 的自定义内容</p></template>
    </FaTooltip>
  </div>
</template>
