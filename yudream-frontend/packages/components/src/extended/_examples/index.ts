import Fab from './fab.vue'
import FabRaw from './fab.vue?raw'
import List from './list.vue'
import ListRaw from './list.vue?raw'
import Mockups from './mockups.vue'
import MockupsRaw from './mockups.vue?raw'
import Stats from './stats.vue'
import StatsRaw from './stats.vue?raw'

export default [
  { title: '图标浮动操作', component: Fab, componentRaw: FabRaw },
  { title: '基础列表', component: List, componentRaw: ListRaw },
  { title: '浏览器与窗口外框', component: Mockups, componentRaw: MockupsRaw },
  { title: '统计指标', component: Stats, componentRaw: StatsRaw },
]
