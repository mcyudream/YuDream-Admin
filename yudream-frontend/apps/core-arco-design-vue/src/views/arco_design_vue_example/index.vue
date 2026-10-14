<script setup lang="ts">
const inputValue = ref<string>('')
const inputValue1 = ref<string>('')

const radioValue = ref<number>(1)
const radioOptions = [
  { label: 'A', value: 1 },
  { label: 'B', value: 2 },
  { label: 'C', value: 3 },
  { label: 'D', value: 4 },
]

const datePickerValue1 = ref<string | number | undefined>()
const datePickerValue2 = ref<string | number | undefined>()
const datePickerValue3 = ref<string | number | undefined>()
const datePickerValue4 = ref<string | number | undefined>()
const datePickerValue5 = ref<string | number | undefined>()

const rateValue = ref<number>(2)

const selectValue1 = ref('lucy')
const selectValue2 = ref('lucy')
const selectOptions1 = [
  { value: 'jack', label: 'Jack' },
  { value: 'lucy', label: 'Lucy' },
  { value: 'disabled', label: 'Disabled', disabled: true },
  { value: 'yiminghe', label: 'Yiminghe' },
]
const selectOptions2 = [{ value: 'lucy', label: 'Lucy' }]

const sliderValue1 = ref<number[]>([0])
const sliderValue2 = ref<number[]>([20, 50])
const switchChecked = ref<boolean>(false)

const columns = [
  { accessorKey: 'name', header: 'Name' },
  { accessorKey: 'salary', header: 'Salary' },
  { accessorKey: 'address', header: 'Address' },
  { accessorKey: 'email', header: 'Email' },
]
const data = [
  { name: 'Jane Doe', salary: 23000, address: '32 Park Road, London', email: 'jane.doe@example.com' },
  { name: 'Alisa Ross', salary: 25000, address: '35 Park Road, London', email: 'alisa.ross@example.com' },
  { name: 'Kevin Sandra', salary: 22000, address: '31 Park Road, London', email: 'kevin.sandra@example.com' },
  { name: 'Ed Hellen', salary: 17000, address: '42 Park Road, London', email: 'ed.hellen@example.com' },
  { name: 'William Smith', salary: 27000, address: '62 Park Road, London', email: 'william.smith@example.com' },
]
</script>

<template>
  <div>
    <YdPageHeader title="YD 组件示例" description="宿主业务界面使用 YuDream Design 公开组件，控件行为遵循各 YD 组件契约。" />
    <YdPageMain>
      <h2>按钮</h2>
      <div class="flex flex-wrap items-center gap-3">
        <YdButton>Default</YdButton>
        <YdButton variant="secondary">Secondary</YdButton>
        <YdButton variant="outline">Outline</YdButton>
        <YdButton variant="ghost">Ghost</YdButton>
        <YdButton variant="link">Link</YdButton>
      </div>
    </YdPageMain>
    <YdPageMain>
      <h2>输入</h2>
      <div class="flex flex-wrap items-center gap-3">
        <YdInput v-model="inputValue" class="w-80" placeholder="请输入内容" clearable />
        <YdInput v-model="inputValue1" class="w-80" placeholder="只读内容" readonly />
      </div>
    </YdPageMain>
    <YdPageMain>
      <h2>单选</h2>
      <YdRadioGroup v-model="radioValue" :options="radioOptions" />
    </YdPageMain>
    <YdPageMain>
      <h2>日期粒度</h2>
      <div class="flex flex-col gap-3">
        <YdDatePicker v-model="datePickerValue1" mode="date" />
        <YdDatePicker v-model="datePickerValue2" mode="week" />
        <YdDatePicker v-model="datePickerValue3" mode="month" />
        <YdDatePicker v-model="datePickerValue4" mode="quarter" />
        <YdDatePicker v-model="datePickerValue5" mode="year" />
      </div>
    </YdPageMain>
    <YdPageMain>
      <h2>评分</h2>
      <YdRate v-model="rateValue" />
    </YdPageMain>
    <YdPageMain>
      <h2>选择</h2>
      <div class="flex flex-wrap items-center gap-3">
        <YdSelect v-model="selectValue1" :options="selectOptions1" />
        <YdSelect v-model="selectValue2" :options="selectOptions2" disabled />
      </div>
    </YdPageMain>
    <YdPageMain>
      <h2>滑块</h2>
      <div class="flex flex-col gap-6">
        <YdSlider v-model="sliderValue1" />
        <YdSlider v-model="sliderValue2" :min="0" :max="100" />
      </div>
    </YdPageMain>
    <YdPageMain>
      <h2>开关</h2>
      <YdSwitch v-model="switchChecked" />
    </YdPageMain>
    <YdPageMain>
      <h2>响应式表格</h2>
      <YdResponsiveTable :columns="columns" :data="data" />
    </YdPageMain>
  </div>
</template>
