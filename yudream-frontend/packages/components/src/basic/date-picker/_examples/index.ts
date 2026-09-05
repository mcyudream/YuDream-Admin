import Basic from './_basic.vue'
import BasicRaw from './_basic.vue?raw'
import Disabled from './_disabled.vue'
import DisabledRaw from './_disabled.vue?raw'
import Mode from './_mode.vue'
import ModeRaw from './_mode.vue?raw'
import Shortcuts from './_shortcuts.vue'
import ShortcutsRaw from './_shortcuts.vue?raw'
import ShowTime from './_show-time.vue'
import ShowTimeRaw from './_show-time.vue?raw'

export default [
  {
    title: '基础',
    component: Basic,
    componentRaw: BasicRaw,
  },
  {
    title: '周/月/季度/年',
    component: Mode,
    componentRaw: ModeRaw,
  },
  {
    title: '日期时间',
    component: ShowTime,
    componentRaw: ShowTimeRaw,
  },
  {
    title: '快捷选项',
    component: Shortcuts,
    componentRaw: ShortcutsRaw,
  },
  {
    title: '禁用',
    component: Disabled,
    componentRaw: DisabledRaw,
  },
]
