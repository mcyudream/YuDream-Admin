import path from 'node:path'
import { fileURLToPath } from 'node:url'
import UnoCSS from 'unocss/vite'
import { defineConfig } from 'vitepress'
import { withMermaid } from 'vitepress-plugin-mermaid'

const projectRoot = path.dirname(fileURLToPath(import.meta.url))
const componentsRoot = path.resolve(projectRoot, '../../../yudream-frontend/packages/components')

const guideSidebar = [
  {
    text: '开始使用',
    items: [
      { text: '框架概览', link: '/guide/introduction' },
      { text: '两种使用方式', link: '/guide/usage-modes' },
      { text: '快速启动', link: '/guide/getting-started' }
    ]
  },
  {
    text: '核心概念',
    items: [
      { text: '系统架构', link: '/guide/architecture' },
      { text: '平台能力', link: '/guide/platform-capabilities' },
      { text: '独有工具与注解', link: '/guide/tools-and-annotations' }
    ]
  },
  {
    text: '二次开发',
    items: [
      { text: '开发环境与工程规范', link: '/guide/development' },
      { text: '部署与发布', link: '/guide/deployment' }
    ]
  }
]

const featureSidebar = [
  {
    text: '平台能力',
    items: [
      { text: '能力总览', link: '/features/' },
      { text: '能力框架与双闸门', link: '/features/capability-framework' }
    ]
  },
  {
    text: 'AI 与知识',
    items: [
      { text: 'AI 模型管理', link: '/features/ai' },
      { text: 'Agent 智能体', link: '/features/agent' },
      { text: 'AI 对话', link: '/features/chat' },
      { text: 'Wiki 知识库', link: '/features/wiki' }
    ]
  },
  {
    text: '内容与业务',
    items: [
      { text: 'CMS 内容管理', link: '/features/cms' },
      { text: '动态表单', link: '/features/dynamic-form' },
      { text: '数据可视化', link: '/features/dataviz' },
      { text: 'QQ 消息平台（Milky / 官方）', link: '/features/milky' }
    ]
  },
  {
    text: '文档与集成',
    items: [
      { text: '消息渲染', link: '/features/render' },
      { text: 'Word 文档模板', link: '/features/word-document' },
      { text: '接口文档', link: '/features/api-doc' },
      { text: '外部系统集成', link: '/features/integration' }
    ]
  }
]

const securitySidebar = [
  {
    text: '安全体系',
    items: [
      { text: '总览', link: '/security/' },
      { text: '双 Token 认证', link: '/security/dual-token' },
      { text: '接口加密', link: '/security/encryption' },
      { text: 'API Key', link: '/security/api-key' },
      { text: 'Passkey 免密登录', link: '/security/passkey' },
      { text: 'OAuth', link: '/security/oauth' }
    ]
  }
]

const spiV1Items = [
  { text: '生命周期与 PluginContext', link: '/plugin/spi/v1/core' },
  { text: '注解声明', link: '/plugin/spi/v1/annotations' },
  { text: 'HTTP 端点', link: '/plugin/spi/v1/http' },
  { text: '框架能力端口 FrameworkServices', link: '/plugin/spi/v1/framework-services' },
  { text: '用户与认证 UserSpi', link: '/plugin/spi/v1/user' },
  { text: '安全 SecuritySpi', link: '/plugin/spi/v1/security' },
  { text: '对象存储 StorageSpi', link: '/plugin/spi/v1/storage' },
  { text: '邮件 MailSpi', link: '/plugin/spi/v1/mail' },
  { text: '文档渲染 DocumentRender', link: '/plugin/spi/v1/document-render' },
  { text: '动态表单 FormSpi', link: '/plugin/spi/v1/form' },
  { text: 'AI 能力 AiSpi', link: '/plugin/spi/v1/ai' },
  { text: '图数据库 GraphSpi', link: '/plugin/spi/v1/graph' },
  { text: '消息 MessagingSpi（Milky / 官方）', link: '/plugin/spi/v1/messaging' },
  { text: '命令 CommandSpi', link: '/plugin/spi/v1/command' },
  { text: '记忆 MemorySpi', link: '/plugin/spi/v1/memory' },
  { text: '注册项 RegistryItems', link: '/plugin/spi/v1/registry-items' }
]

const pluginSidebar = [
  {
    text: '插件开发',
    items: [
      { text: '插件系统总览', link: '/plugin/overview' },
      { text: '创建你的第一个插件', link: '/plugin/getting-started' },
      { text: '插件规范与检查清单', link: '/plugin/specification' },
      { text: '插件仓库与多模块工程', link: '/plugin/repository' },
      { text: '开发工具链与本地调试', link: '/plugin/dev-tools' }
    ]
  },
  {
    text: '后端 SPI 参考',
    items: [
      { text: '版本说明与升级指引', link: '/plugin/spi/' },
      { text: 'SPI v1', collapsed: true, items: spiV1Items }
    ]
  },
  {
    text: '前端 SDK 与组件',
    items: [
      { text: '@yudream/plugin-sdk', link: '/plugin/sdk/' },
      { text: '插件前端工程化（remoteEntry）', link: '/plugin/frontend-remote' }
    ]
  },
  {
    text: '发布与生态',
    items: [
      { text: '插件市场与上架流程', link: '/plugin/marketplace' }
    ]
  }
]

const referenceSidebar = [
  {
    text: '参考',
    items: [
      { text: '索引', link: '/reference/' },
      { text: '工具集 Toolkit', link: '/reference/toolkit' },
      { text: '系统注解详解', link: '/reference/annotations' }
    ]
  }
]

const protocolSidebar = [
  {
    text: '协议',
    items: [
      { text: '通信与渲染协议总览', link: '/protocol/' },
      { text: 'QQ 协议详解（Milky / 官方 OpenAPI）', link: '/protocol/milky' }
    ]
  }
]

const componentSidebar = [
  {
    text: '组件库',
    items: [{ text: '使用指南', link: '/components/' }]
  },
  {
    text: '基础控件',
    items: [
      { text: 'FaButton 按钮', link: '/components/fa-button' },
      { text: 'FaInput 输入框', link: '/components/fa-input' },
      { text: 'FaTextarea 文本域', link: '/components/fa-textarea' },
      { text: 'FaInputOTP 验证码输入', link: '/components/input-otp' },
      { text: 'FaSelect 选择器', link: '/components/fa-select' },
      { text: 'FaCheckbox 复选框', link: '/components/fa-checkbox' },
      { text: 'FaRadioGroup 单选组', link: '/components/radio-group' },
      { text: 'FaNumberField 数字输入框', link: '/components/number-field' },
      { text: 'FaSwitch 开关', link: '/components/fa-switch' },
      { text: 'FaSlider 滑块', link: '/components/slider' },
      { text: 'FaLabel 标签', link: '/components/label' },
      { text: 'FaPasswordStrength 密码强度', link: '/components/password-strength' },
      { text: 'FaKbd 键盘按键', link: '/components/kbd' },
      { text: 'FaKbdGroup 按键组', link: '/components/kbd-group' }
    ]
  },
  {
    text: '数据展示',
    items: [
      { text: 'FaTag 标签', link: '/components/tag' },
      { text: 'FaBadge 徽标', link: '/components/badge' },
      { text: 'FaAvatar 头像', link: '/components/avatar' },
      { text: 'FaAlert 警告提示', link: '/components/alert' },
      { text: 'FaTrend 趋势', link: '/components/trend' },
      { text: 'FaDescriptions 描述列表', link: '/components/descriptions' },
      { text: 'FaDivider 分割线', link: '/components/divider' },
      { text: 'FaTable 表格', link: '/components/fa-table' },
      { text: 'FaTabs 标签页', link: '/components/fa-tabs' },
      { text: 'FaProgress 进度条', link: '/components/fa-progress' },
      { text: 'FaPagination 分页', link: '/components/pagination' },
      { text: 'FaResponsiveTable 响应式表格', link: '/components/responsive-table' },
      { text: 'FaSearchBar 搜索栏', link: '/components/search-bar' }
    ]
  },
  {
    text: '浮层与反馈',
    items: [
      { text: 'FaModal 对话框', link: '/components/fa-modal' },
      { text: 'FaDrawer 抽屉', link: '/components/fa-drawer' },
      { text: 'FaToast 轻提示', link: '/components/toast' },
      { text: 'FaTooltip 工具提示', link: '/components/tooltip' },
      { text: 'FaPopover 弹出面板', link: '/components/popover' },
      { text: 'FaHoverCard 悬停卡片', link: '/components/hover-card' },
      { text: 'FaDropdown 下拉菜单', link: '/components/dropdown' },
      { text: 'FaContextMenu 右键菜单', link: '/components/context-menu' },
      { text: 'FaCollapsible 折叠面板', link: '/components/collapsible' }
    ]
  },
  {
    text: '布局与其他',
    items: [
      { text: 'FaPageHeader 页头', link: '/components/page-header' },
      { text: 'FaPageMain 页面主体', link: '/components/page-main' },
      { text: 'FaFixedBar 固定栏', link: '/components/fixed-bar' },
      { text: 'FaScrollArea 滚动区域', link: '/components/scroll-area' },
      { text: 'FaButtonGroup 按钮组', link: '/components/button-group' },
      { text: 'FaIcon 图标', link: '/components/fa-icon' },
      { text: 'FaImagePreview 图片预览', link: '/components/fa-image-preview' },
      { text: 'FaFileUpload 文件上传', link: '/components/fa-file-upload' },
      { text: 'FaImageUpload 图片上传', link: '/components/fa-image-upload' }
    ]
  },
  {
    text: '功能函数',
    items: [
      { text: 'useFaModal', link: '/components/use-fa-modal' },
      { text: 'useFaDrawer', link: '/components/use-fa-drawer' }
    ]
  },
  {
    text: 'Yd* YuDream 原创组件',
    items: [
      { text: 'YdBubble 气泡', link: '/components/yd-bubble' },
      { text: 'YdChatSender 输入发送器', link: '/components/yd-chat-sender' },
      { text: 'YdChatMessageList 消息列表', link: '/components/yd-chat-message-list' },
      { text: 'YdChatWindow 对话窗口', link: '/components/yd-chat-window' },
      { text: 'YdChatSessionList 会话列表', link: '/components/yd-chat-session-list' },
      { text: 'YdChatProcess 执行过程', link: '/components/yd-chat-process' },
      { text: 'YdChatGraph 关系图谱', link: '/components/yd-chat-graph' },
      { text: 'YdChatActions 操作栏', link: '/components/yd-chat-actions' },
      { text: 'YdChatReasoning 思维链', link: '/components/yd-chat-reasoning' },
      { text: 'YdChatLoading 加载态', link: '/components/yd-chat-loading' },
      { text: 'YdThoughtChain 步骤链', link: '/components/yd-thought-chain' },
      { text: 'YdSuggestion 建议', link: '/components/yd-suggestion' },
      { text: 'YdPrompts 提示词', link: '/components/yd-prompts' },
      { text: 'YdCitationList 引用列表', link: '/components/yd-citation-list' },
      { text: 'YdAttachmentList 附件列表', link: '/components/yd-attachment-list' },
      { text: 'YdWelcome 欢迎', link: '/components/yd-welcome' },
      { text: 'YdGraphCanvas 关系图谱画布', link: '/components/yd-graph-canvas' },
      { text: 'YdTablePicker 弹出式选择输入框', link: '/components/yd-table-picker' },
      { text: 'YdDatePicker 日期选择器', link: '/components/yd-date-picker' },
      { text: 'YdRangePicker 日期范围选择器', link: '/components/yd-range-picker' },
      { text: 'YdTimePicker 时间选择器', link: '/components/yd-time-picker' }
    ]
  }
]

export default withMermaid(
  defineConfig({
    lang: 'zh-CN',
    title: 'YuDream Admin',
    description: 'YuDream Admin 框架与插件开发文档',
    head: [['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }]],
    themeConfig: {
      siteTitle: 'YuDream Admin 文档',
      nav: [
        { text: '指南', link: '/guide/introduction', activeMatch: '/guide/' },
        { text: '平台能力', link: '/features/', activeMatch: '/features/' },
        { text: '安全', link: '/security/', activeMatch: '/security/' },
        { text: '插件开发', link: '/plugin/overview', activeMatch: '/plugin/' },
        { text: '协议', link: '/protocol/', activeMatch: '/protocol/' },
        { text: '参考', link: '/reference/', activeMatch: '/reference/' },
        { text: '组件库', link: '/components/', activeMatch: '/components/' }
      ],
      sidebar: {
        '/guide/': guideSidebar,
        '/features/': featureSidebar,
        '/security/': securitySidebar,
        '/plugin/': pluginSidebar,
        '/reference/': referenceSidebar,
        '/protocol/': protocolSidebar,
        '/components/': componentSidebar
      },
      socialLinks: [],
      outline: { level: [2, 3], label: '本页目录' },
      docFooter: { prev: '上一页', next: '下一页' },
      search: { provider: 'local', options: { translations: { button: { buttonText: '搜索文档' } } } },
      returnToTopLabel: '回到顶部',
      sidebarMenuLabel: '菜单'
    },
    mermaid: {},
    markdown: {
      config(md) {
        // 允许在代码块中渲染 Mermaid 图
      }
    },
    vite: {
      plugins: [UnoCSS()],
      resolve: {
        dedupe: ['vue']
      },
      server: {
        port: 5174,
        fs: { allow: [componentsRoot] }
      },
      ssr: {
        noExternal: ['@yudream/components']
      },
      optimizeDeps: {
        // 直接预打包 mermaid 整个依赖图：其传递依赖中的 CJS 包（dayjs、
        // fastdom、@braintree/sanitize-url 等）由 esbuild 统一做 CJS→ESM 转换。
        // 若不这样做，pnpm 严格布局下这些包会被当作裸文件加载，浏览器报
        // "doesn't provide an export named 'default'"。
        include: ['mermaid'],
        exclude: ['@yudream/components']
      }
    }
  })
)
