<script setup lang="ts">
import { ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const password = ref('')
const otp = ref('')
const numberValue = ref(5)
const sliderSingle = ref([40])
const sliderRange = ref([20, 80])
const radioValue = ref('standard')
const checkboxValues = ref(['backup'])
const radioCustom = ref('pro')
</script>

<template>
  <DemoCard title="YdPasswordStrength" description="密码强度指示：password 属性传入密码值，实时显示规则命中与色阶">
    <div class="gap-3 grid max-w-md">
      <YdInput v-model="password" type="password" placeholder="请输入密码试一试" />
      <YdPasswordStrength :password="password" />
    </div>
  </DemoCard>

  <DemoCard title="YdInputOTP" description="一次性验证码输入：自动跳格、支持粘贴，length 控制位数，separator 分组">
    <div class="gap-3 grid max-w-md">
      <YdInputOTP v-model="otp" :length="6" :separator="[3]" pattern="only-digits" />
      <output class="text-xs text-muted-foreground" aria-live="polite">
        当前值：{{ otp || '(未输入)' }}
      </output>
    </div>
  </DemoCard>

  <DemoCard title="YdNumberField" description="数字输入：min / max / step 范围控制，内置增减按钮">
    <div class="flex flex-wrap gap-6 items-center">
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">数量（0-10，步长 1）</span>
        <YdNumberField v-model="numberValue" :min="0" :max="10" :step="1" class="w-32" />
      </div>
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">禁用状态</span>
        <YdNumberField :model-value="3" :min="0" :max="10" disabled class="w-32" />
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdSlider" description="滑块：modelValue 为 number[]，单值取单元素数组，双元素即范围">
    <div class="gap-5 grid max-w-md">
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">单值：{{ sliderSingle[0] }}</span>
        <YdSlider v-model="sliderSingle" :min="0" :max="100" :step="1" />
      </div>
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">范围：{{ sliderRange[0] }} - {{ sliderRange[1] }}</span>
        <YdSlider v-model="sliderRange" :min="0" :max="100" :step="5" />
      </div>
      <div class="gap-2 grid">
        <span class="text-xs text-muted-foreground">禁用</span>
        <YdSlider :model-value="[60]" :min="0" :max="100" disabled />
      </div>
    </div>
  </DemoCard>

  <DemoCard title="YdRadioGroup" description="单选组：options 数据驱动，支持描述文案与禁用项">
    <div class="gap-5 grid max-w-md">
      <YdRadioGroup
        v-model="radioValue"
        :options="[
          { label: '标准版', value: 'standard', description: '适合个人与小型团队' },
          { label: '专业版', value: 'pro', description: '包含全部平台能力' },
          { label: '旗舰版（暂不可选）', value: 'flagship', disabled: true },
        ]"
      />
      <output class="text-xs text-muted-foreground" aria-live="polite">
        当前选择：{{ radioValue }}
      </output>
    </div>
  </DemoCard>

  <DemoCard title="YdRadioGroup 自定义选项" description="option 插槽接管完整视觉，适合卡片式选择">
    <YdRadioGroup
      v-model="radioCustom" class="gap-3 grid sm:grid-cols-2" :options="[
        { label: '按年付费', value: 'yearly' },
        { label: '按月付费', value: 'monthly' },
      ]"
    >
      <template #option="{ option, checked }">
        <div
          class="p-3 border rounded-lg flex gap-3 cursor-pointer transition-colors items-start"
          :class="checked ? 'border-[oklch(var(--primary))] bg-[oklch(var(--primary)/0.06)]' : 'hover:bg-[var(--color-fill-2)]'"
        >
          <YdIcon :name="checked ? 'morph:circle-check' : 'morph:circle-dot'" class="mt-0.5 shrink-0" />
          <span>
            <span class="text-sm font-medium block">{{ option.label }}</span>
            <span class="text-xs text-muted-foreground block">{{ option.value === 'yearly' ? '立省两个月费用' : '灵活随时取消' }}</span>
          </span>
        </div>
      </template>
    </YdRadioGroup>
  </DemoCard>

  <DemoCard title="YdCheckboxGroup" description="复选框组：options 数据驱动，min / max 控制选中数量边界">
    <div class="gap-4 grid max-w-md">
      <YdCheckboxGroup
        v-model="checkboxValues"
        :options="[
          { label: '自动备份', value: 'backup', description: '每日凌晨执行' },
          { label: '异地容灾', value: 'remote' },
          { label: '审计日志', value: 'audit' },
          { label: '灰度发布（暂不可选）', value: 'gray', disabled: true },
        ]"
      />
      <output class="text-xs text-muted-foreground break-words" aria-live="polite">
        当前选择：{{ JSON.stringify(checkboxValues) }}
      </output>
    </div>
  </DemoCard>
</template>
