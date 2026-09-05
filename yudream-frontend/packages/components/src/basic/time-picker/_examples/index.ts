import Basic from './_basic.vue'
import BasicRaw from './_basic.vue?raw'
import Range from './_range.vue'
import RangeRaw from './_range.vue?raw'
import Step from './_step.vue'
import StepRaw from './_step.vue?raw'

export default [
  {
    title: '基础',
    component: Basic,
    componentRaw: BasicRaw,
  },
  {
    title: '时间范围',
    component: Range,
    componentRaw: RangeRaw,
  },
  {
    title: '步长与格式',
    component: Step,
    componentRaw: StepRaw,
  },
]
