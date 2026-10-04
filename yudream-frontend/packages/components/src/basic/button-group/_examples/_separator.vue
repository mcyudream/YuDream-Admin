<script setup lang="ts">
// 组件实际使用时无需手动导入，框架会自动导入
import FaButton from '../../button/index.vue'
import FaIcon from '../../icon/index.vue'
import FaButtonGroup from '../index.vue'
</script>

<template>
  <div class="flex flex-col gap-4 items-start">
    <FaButtonGroup separator>
      <FaButton>
        <FaIcon name="morph:eye" class="size-4" />
        查看详情
      </FaButton>
      <FaButton>
        <FaIcon name="morph:pencil" class="size-4" />
        编辑
      </FaButton>
      <FaButton>
        <FaIcon name="morph:trash-2" class="size-4" />
        删除
      </FaButton>
    </FaButtonGroup>
    <FaButtonGroup orientation="vertical" separator>
      <FaButton>
        <FaIcon name="morph:eye" class="size-4" />
        查看详情
      </FaButton>
      <FaButton>
        <FaIcon name="morph:pencil" class="size-4" />
        编辑
      </FaButton>
      <FaButton>
        <FaIcon name="morph:trash-2" class="size-4" />
        删除
      </FaButton>
    </FaButtonGroup>
    <p>按钮如果为 outline 则无需设置分割线</p>
    <FaButtonGroup>
      <FaButton variant="outline">
        <FaIcon name="morph:eye" class="size-4" />
        查看详情
      </FaButton>
      <FaButton variant="outline">
        <FaIcon name="morph:pencil" class="size-4" />
        编辑
      </FaButton>
      <FaButton variant="outline">
        <FaIcon name="morph:trash-2" class="size-4" />
        删除
      </FaButton>
    </FaButtonGroup>
  </div>
</template>
