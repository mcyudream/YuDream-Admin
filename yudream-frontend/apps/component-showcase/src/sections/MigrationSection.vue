<script setup lang="ts">
import type { YdFormInstance, YdFormSubmitData } from '@yudream/components'
import { YdAction, YdForm, YdFormItem, YdInputTag, YdRate, YdUiProvider } from '@yudream/components'
import { reactive, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const form = reactive({ name: '', tags: ['YD'], rating: 2.5 })
const formRef = ref<YdFormInstance>()
const disabled = ref(false)
const readonly = ref(false)
const result = ref('提交空名称可以查看校验，重置恢复初始值。')
const selectedCard = ref('overview')
function submit(data: YdFormSubmitData) {
  result.value = data.errors ? `校验失败：${Object.values(data.errors).map(error => error.message).join('；')}` : `本地校验通过：${JSON.stringify(data.values)}`
}
</script>

<template>
  <YdUiProvider>
    <DemoCard title="统一表单与字段" description="YdForm / YdFormItem：标签、必填、校验、实例重置和禁用继承。此处为本地示例，不提交 API。">
      <div class="mb-4 flex gap-4">
        <YdCheckbox v-model="disabled">
          禁用表单
        </YdCheckbox>
        <YdCheckbox v-model="readonly">
          标签与评分只读
        </YdCheckbox>
      </div>
      <YdForm ref="formRef" :model="form" :disabled="disabled" class="max-w-xl" @submit="submit">
        <YdFormItem field="name" label="显示名称" :rules="[{ required: true, message: '请输入显示名称' }]" extra="标签点击可定位输入框，修改后自动校验。">
          <YdInput v-model="form.name" placeholder="请输入显示名称" />
        </YdFormItem>
        <YdFormItem field="tags" label="标签" :rules="[{ type: 'array', minLength: 1, message: '至少保留一个标签' }]" extra="Enter 创建标签、删除、去重；模型始终为数组。">
          <YdInputTag v-model="form.tags" :readonly="readonly" placeholder="输入后按 Enter" />
        </YdFormItem>
        <YdFormItem field="rating" label="评分" extra="支持半星；Tab 后使用方向键、Home / End；Delete 清空。">
          <YdRate v-model="form.rating" allow-half allow-clear :readonly="readonly" aria-label="组件体验评分" />
        </YdFormItem>
        <div class="flex gap-2">
          <YdButton type="submit" :disabled="disabled">
            校验并提交
          </YdButton>
          <YdButton variant="outline" type="button" :disabled="disabled" @click="formRef?.resetFields()">
            重置字段
          </YdButton>
        </div>
      </YdForm>
      <p class="text-sm mt-4" role="status">
        {{ result }}
      </p>
    </DemoCard>
    <DemoCard title="自定布局动作" description="YdAction 保留原生按钮语义、属性、事件和焦点；用于导航与自定卡片，不替代普通 CRUD 的 YdButton。">
      <div class="gap-3 grid grid-cols-2">
        <YdAction v-for="item in [{ id: 'overview', label: '概览' }, { id: 'details', label: '详情' }]" :key="item.id" class="p-4 border rounded-lg" :aria-pressed="selectedCard === item.id" @click="selectedCard = item.id">
          <strong>{{ item.label }}</strong><span class="text-sm mt-1 block">{{ selectedCard === item.id ? '当前区域' : '点击切换区域' }}</span>
        </YdAction>
      </div>
    </DemoCard>
  </YdUiProvider>
</template>
