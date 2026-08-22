import DefaultTheme from 'vitepress/theme'
import '@arco-design/web-vue/dist/arco.css'
import 'virtual:uno.css'
import './custom.css'
import Mermaid from 'vitepress-plugin-mermaid/Mermaid.vue'
import Demo from './components/Demo.vue'
import ApiTable from './components/ApiTable.vue'
import Layout from './Layout.vue'

export default {
  extends: DefaultTheme,
  Layout,
  enhanceApp({ app }) {
    app.component('Mermaid', Mermaid)
    app.component('Demo', Demo)
    app.component('ApiTable', ApiTable)
  }
}
