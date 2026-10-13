import { createApp } from 'vue'
import App from './App.vue'

// UnoCSS
import 'virtual:uno.css'
// 内部基础控件的布局与中性主题变量，由 YD 组件封装视觉。
import '@arco-design/web-vue/dist/arco.css'
import '@yudream/components/appearance.css'
// 全局样式
import '@/styles/globals.css'

const app = createApp(App)
app.mount('#app')
