import { createApp } from 'vue'
import App from './App.vue'

// UnoCSS
import 'virtual:uno.css'
// Arco 主题变量（--color-bg-1、--color-text-1、--primary-6 等）
import '@arco-design/web-vue/dist/arco.css'
// 全局样式
import '@/styles/globals.css'

const app = createApp(App)
app.mount('#app')
