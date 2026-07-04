<script setup lang="ts">
import { getPageBuilder, usePageBuilderModal } from '@myissue/vue-website-page-builder'

interface CmsBlock {
  title: string
  group: string
  description: string
  html: string
}

const toast = useFaToast()
const { closeAddComponentModal } = usePageBuilderModal()
const keyword = ref('')
const activeGroup = ref('全部')
const inserting = ref(false)

const blocks: CmsBlock[] = [
  {
    title: '沉浸式首页首屏',
    group: '首页',
    description: '品牌标题、动态用户欢迎语、双按钮行动入口。',
    html: `<section data-component-title="沉浸式首页首屏" class="yb-hero yb-hero--immersive" style="padding: 88px 48px; border-radius: 24px; background: linear-gradient(135deg, #0f172a, #14532d); color: #fff;">
  <p style="margin: 0 0 18px; color: rgba(255,255,255,.72); font-weight: 700;">{{site.name}} · {{auth.welcome}}</p>
  <h1 style="max-width: 820px; margin: 0; font-size: 64px; line-height: .98;">把内容、数据和业务能力组合成真正可发布的网站</h1>
  <p style="max-width: 680px; margin: 22px 0 0; color: rgba(255,255,255,.82); font-size: 18px;">使用可视化区块、媒体库和动态变量搭建页面。当前用户：{{user.nickname}}</p>
  <div style="display:flex; gap:12px; flex-wrap:wrap; margin-top: 32px;">
    <a href="/site" style="padding: 12px 18px; border-radius: 10px; background: #fff; color: #0f172a; font-weight: 800; text-decoration:none;">查看站点</a>
    <a href="/login" data-visible-when="guest" style="padding: 12px 18px; border-radius: 10px; border: 1px solid rgba(255,255,255,.35); color:#fff; font-weight: 800; text-decoration:none;">登录体验</a>
  </div>
</section>`,
  },
  {
    title: 'WordPress式文章流',
    group: '内容',
    description: '三列文章卡片，可替换为真实内容列表。',
    html: `<section data-component-title="WordPress式文章流" class="yb-post-grid" style="padding: 56px 0;">
  <div style="display:grid; gap: 10px; margin-bottom: 24px;">
    <span style="color:#0f766e; font-weight:800;">Latest Posts</span>
    <h2 style="margin:0; font-size: 40px;">精选内容</h2>
    <p style="margin:0; color:#64748b;">用页面、分类和标签组织内容，像 WordPress 一样搭建栏目。</p>
  </div>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px;">
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>公告</small><h3>CMS 发布流程</h3><p>保存草稿、发布上线、公开渲染。</p></article>
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>教程</small><h3>媒体库和区块</h3><p>上传素材后直接插入页面。</p></article>
    <article style="padding:22px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><small>动态</small><h3>变量渲染</h3><p>支持用户、登录态、站点变量。</p></article>
  </div>
</section>`,
  },
  {
    title: '功能矩阵',
    group: '营销',
    description: '适合产品介绍和平台能力展示。',
    html: `<section data-component-title="功能矩阵" class="yb-feature-matrix" style="padding: 56px 0;">
  <h2 style="font-size: 38px; margin: 0 0 18px;">平台能力</h2>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px;">
    <div style="padding:20px; border-radius:14px; background:#ecfeff;"><strong>可视化构建</strong><p>低代码组合页面与首页。</p></div>
    <div style="padding:20px; border-radius:14px; background:#f0fdf4;"><strong>动态变量</strong><p>按登录态和用户信息渲染。</p></div>
    <div style="padding:20px; border-radius:14px; background:#fff7ed;"><strong>S3 媒体库</strong><p>使用 RustFS 管理素材。</p></div>
  </div>
</section>`,
  },
  {
    title: '登录态行动区',
    group: '动态',
    description: '同时包含游客和已登录用户可见内容。',
    html: `<section data-component-title="登录态行动区" class="yb-auth-cta" style="display:grid; gap:16px; padding: 38px; border-radius:18px; background:#111827; color:#fff;">
  <div data-visible-when="guest"><h2>加入 YuDream</h2><p>登录后可看到你的头像、昵称和个人入口。</p><a href="/login" style="color:#fff; font-weight:800;">立即登录</a></div>
  <div data-visible-when="logged-in"><h2>欢迎回来，{{user.nickname}}</h2><p>你的账号 {{user.username}} 已登录，可以继续管理内容。</p><img src="{{user.avatar}}" alt="{{user.nickname}}" style="width:56px; height:56px; border-radius:50%; object-fit:cover;"></div>
</section>`,
  },
  {
    title: '导航头像列表',
    group: '动态',
    description: '用于展示导航栏用户、协作者或推荐成员。',
    html: `<section data-component-title="导航头像列表" class="yb-avatar-strip" style="padding: 28px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
  <h2 style="margin-top:0;">在线协作入口</h2>
  <div data-yb-repeat="navUsers" style="display:flex; gap:10px; flex-wrap:wrap;">
    <a href="{{item.url}}" style="display:flex; align-items:center; gap:8px; padding:8px 12px; border-radius:999px; background:#f8fafc; color:#0f172a; text-decoration:none;">
      <img src="{{item.avatar}}" alt="{{item.name}}" style="width:32px; height:32px; border-radius:50%; object-fit:cover;">
      <span>{{item.name}}</span>
    </a>
  </div>
</section>`,
  },
  {
    title: '价格表',
    group: '营销',
    description: '三档套餐或服务报价。',
    html: `<section data-component-title="价格表" class="yb-pricing" style="padding:56px 0;">
  <h2 style="font-size:38px; margin:0 0 20px;">选择适合的方案</h2>
  <div style="display:grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap:16px;">
    <article style="padding:24px; border:1px solid #e5e7eb; border-radius:18px;"><h3>Starter</h3><strong style="font-size:34px;">免费</strong><p>适合内容试运行。</p></article>
    <article style="padding:24px; border:2px solid #0f766e; border-radius:18px;"><h3>Pro</h3><strong style="font-size:34px;">¥99</strong><p>适合业务站点。</p></article>
    <article style="padding:24px; border:1px solid #e5e7eb; border-radius:18px;"><h3>Team</h3><strong style="font-size:34px;">定制</strong><p>适合团队协作。</p></article>
  </div>
</section>`,
  },
  {
    title: 'FAQ 折叠区',
    group: '内容',
    description: '常见问题与答案。',
    html: `<section data-component-title="FAQ 折叠区" class="yb-faq" style="padding: 48px 0;">
  <h2 style="font-size:36px;">常见问题</h2>
  <details open style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>CMS 能发布首页吗？</summary><p>可以，首页使用 homeHtml 保存构建器完整 HTML。</p></details>
  <details style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>能使用 HTML 吗？</summary><p>可以，HTML 模式支持动态变量。</p></details>
  <details style="padding:16px; border-bottom:1px solid #e5e7eb;"><summary>媒体库在哪里？</summary><p>在构建器选择图片时直接打开 CMS 媒体库。</p></details>
</section>`,
  },
  {
    title: '变量速查卡',
    group: '动态',
    description: '展示页面可用变量，方便编辑者复制。',
    html: `<section data-component-title="变量速查卡" class="yb-variable-card" style="padding:24px; border-radius:16px; background:#f8fafc; border:1px solid #e5e7eb;">
  <h2>动态变量</h2>
  <p>站点：{{site.name}}，当前路径：{{route.path}}</p>
  <p>登录状态：{{auth.isLoggedIn}}，欢迎语：{{auth.welcome}}</p>
  <p>用户：{{user.username}} / {{user.nickname}}</p>
</section>`,
  },
  {
    title: '杂志式首页模板',
    group: '首页',
    description: '首屏、主推内容、侧栏导航组合，适合门户首页。',
    html: `<section data-component-title="杂志式首页模板" class="yb-magazine-home" style="display:grid; grid-template-columns: minmax(0, 1.4fr) minmax(280px, .6fr); gap:24px; padding:56px 0;">
  <article style="display:grid; align-content:end; min-height:520px; padding:40px; border-radius:22px; background:linear-gradient(135deg,#052e2b,#0f766e); color:#fff;">
    <span style="font-weight:800; color:rgba(255,255,255,.72);">Featured · {{site.name}}</span>
    <h1 style="max-width:760px; margin:14px 0 0; font-size:58px; line-height:1;">用低代码构建站点首页、专题页和内容栏目</h1>
    <p style="max-width:620px; margin:18px 0 0; color:rgba(255,255,255,.82); font-size:18px;">当前访问：{{route.path}}，导航项 {{navigation.count}} 个。</p>
  </article>
  <aside style="display:grid; gap:14px;">
    <div data-yb-repeat="navigation" style="display:grid; gap:10px;">
      <a href="{{item.url}}" style="display:flex; justify-content:space-between; gap:12px; padding:16px; border:1px solid #e5e7eb; border-radius:14px; background:#fff; color:#0f172a; text-decoration:none;"><strong>{{item.label}}</strong><span>#{{index}}</span></a>
    </div>
  </aside>
</section>`,
  },
  {
    title: '图文首屏',
    group: '首页',
    description: '左文案右媒体，适合产品、品牌或专题入口。',
    html: `<section data-component-title="图文首屏" class="yb-media-hero" style="display:grid; grid-template-columns: minmax(0, .92fr) minmax(360px, 1.08fr); gap:32px; align-items:center; padding:72px 0;">
  <div>
    <h1 style="margin:0; font-size:56px; line-height:1.02;">{{page.title}}</h1>
    <p style="margin:20px 0 0; color:#475569; font-size:18px; line-height:1.7;">{{page.summary}}</p>
    <div style="display:flex; gap:12px; flex-wrap:wrap; margin-top:28px;">
      <a href="/site" style="padding:12px 18px; border-radius:10px; background:#0f766e; color:#fff; font-weight:800; text-decoration:none;">访问首页</a>
      <a href="/login" data-visible-when="guest" style="padding:12px 18px; border:1px solid #cbd5e1; border-radius:10px; color:#0f172a; font-weight:800; text-decoration:none;">登录</a>
    </div>
  </div>
  <figure style="margin:0; overflow:hidden; min-height:420px; border-radius:24px; background:#e2e8f0;">
    <img src="{{site.logo}}" alt="{{site.name}}" style="width:100%; height:100%; min-height:420px; object-fit:cover;">
  </figure>
</section>`,
  },
  {
    title: '导航菜单循环',
    group: '动态',
    description: '读取 CMS 菜单配置并循环渲染。',
    html: `<nav data-component-title="导航菜单循环" class="yb-navigation-repeat" style="display:flex; align-items:center; justify-content:space-between; gap:18px; padding:16px 22px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
  <a href="/site" style="color:#0f172a; font-size:20px; font-weight:900; text-decoration:none;">{{site.name}}</a>
  <div data-yb-repeat="navigation" style="display:flex; gap:8px; flex-wrap:wrap;">
    <a href="{{item.url}}" style="padding:9px 12px; border-radius:999px; color:#475569; text-decoration:none;">{{item.label}}</a>
  </div>
</nav>`,
  },
  {
    title: '页面元信息横幅',
    group: '内容',
    description: '页面标题、摘要、分类、标签和路径变量。',
    html: `<section data-component-title="页面元信息横幅" class="yb-page-meta" style="padding:46px; border-radius:22px; background:#f8fafc; border:1px solid #e5e7eb;">
  <span style="color:#0f766e; font-weight:800;">{{page.slug}}</span>
  <h1 style="margin:10px 0 12px; font-size:48px; line-height:1.08;">{{page.title}}</h1>
  <p style="max-width:760px; margin:0; color:#475569; font-size:18px; line-height:1.7;">{{page.excerpt}}</p>
  <div style="display:flex; gap:8px; flex-wrap:wrap; margin-top:22px;">
    <span style="padding:7px 10px; border-radius:999px; background:#dcfce7; color:#166534;">{{page.categories}}</span>
    <span style="padding:7px 10px; border-radius:999px; background:#e0f2fe; color:#075985;">{{page.tags}}</span>
  </div>
</section>`,
  },
  {
    title: '团队头像网格',
    group: '动态',
    description: '循环渲染登录用户或导航头像列表。',
    html: `<section data-component-title="团队头像网格" class="yb-user-grid" style="padding:48px 0;">
  <h2 style="margin:0 0 18px; font-size:36px;">成员入口</h2>
  <div data-yb-repeat="navUsers" style="display:grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap:14px;">
    <a href="{{item.url}}" style="display:grid; gap:10px; padding:18px; border:1px solid #e5e7eb; border-radius:16px; background:#fff; color:#0f172a; text-decoration:none;">
      <img src="{{item.avatar}}" alt="{{item.name}}" style="width:54px; height:54px; border-radius:50%; object-fit:cover;">
      <strong>{{item.name}}</strong>
      <small style="color:#64748b;">成员 #{{index}}</small>
    </a>
  </div>
</section>`,
  },
  {
    title: '媒体画廊',
    group: '媒体',
    description: '适合展示产品截图、案例图片或媒体素材。',
    html: `<section data-component-title="媒体画廊" class="yb-gallery" style="padding:54px 0;">
  <div style="display:flex; justify-content:space-between; gap:18px; align-items:end; margin-bottom:20px;">
    <div><h2 style="margin:0; font-size:38px;">媒体画廊</h2><p style="margin:8px 0 0; color:#64748b;">从 CMS 媒体库选择图片后替换占位图。</p></div>
    <span style="color:#0f766e; font-weight:800;">{{site.name}}</span>
  </div>
  <div style="display:grid; grid-template-columns: 1.25fr .75fr; gap:14px;">
    <img src="{{site.logo}}" alt="主图" style="width:100%; height:420px; object-fit:cover; border-radius:18px; background:#e2e8f0;">
    <div style="display:grid; gap:14px;">
      <img src="{{user.avatar}}" alt="图片" style="width:100%; height:203px; object-fit:cover; border-radius:18px; background:#e2e8f0;">
      <div style="display:grid; place-items:center; height:203px; border:1px dashed #cbd5e1; border-radius:18px; color:#64748b;">替换为媒体库图片</div>
    </div>
  </div>
</section>`,
  },
  {
    title: '联系表单静态页',
    group: '表单',
    description: '提供可二次接线的联系表单布局。',
    html: `<section data-component-title="联系表单静态页" class="yb-contact" style="display:grid; grid-template-columns: .8fr 1.2fr; gap:28px; padding:56px 0;">
  <div><h2 style="margin:0; font-size:40px;">联系 {{site.name}}</h2><p style="color:#64748b; line-height:1.7;">表单可作为低代码静态外观，后续可接入平台表单能力。</p></div>
  <form style="display:grid; gap:12px; padding:22px; border:1px solid #e5e7eb; border-radius:18px; background:#fff;">
    <input placeholder="你的姓名" style="height:44px; padding:0 12px; border:1px solid #cbd5e1; border-radius:10px;">
    <input placeholder="邮箱" style="height:44px; padding:0 12px; border:1px solid #cbd5e1; border-radius:10px;">
    <textarea placeholder="留言内容" rows="5" style="padding:12px; border:1px solid #cbd5e1; border-radius:10px;"></textarea>
    <button type="button" style="height:44px; border:0; border-radius:10px; background:#0f766e; color:#fff; font-weight:800;">提交</button>
  </form>
</section>`,
  },
  {
    title: '文章目录侧栏',
    group: '内容',
    description: '长文阅读布局，含正文、目录和作者卡。',
    html: `<section data-component-title="文章目录侧栏" class="yb-article-layout" style="display:grid; grid-template-columns: minmax(0, 1fr) 280px; gap:30px; padding:48px 0;">
  <article style="display:grid; gap:18px;">
    <h2 style="margin:0; font-size:42px;">{{page.title}}</h2>
    <p style="color:#475569; font-size:18px; line-height:1.8;">{{page.summary}}</p>
    <h3>第一部分</h3><p style="line-height:1.8;">在这里编辑文章正文，可以保留变量，也可以使用普通 HTML。</p>
    <h3>第二部分</h3><p style="line-height:1.8;">分类：{{page.categories}}，标签：{{page.tags}}。</p>
  </article>
  <aside style="position:sticky; top:86px; align-self:start; display:grid; gap:14px;">
    <div style="padding:16px; border:1px solid #e5e7eb; border-radius:16px; background:#fff;"><strong>目录</strong><a href="#" style="display:block; margin-top:10px; color:#475569;">第一部分</a><a href="#" style="display:block; margin-top:8px; color:#475569;">第二部分</a></div>
    <div data-visible-when="logged-in" style="padding:16px; border-radius:16px; background:#f0fdf4;"><img src="{{user.avatar}}" alt="{{user.nickname}}" style="width:42px; height:42px; border-radius:50%; object-fit:cover;"><p>{{auth.welcome}}</p></div>
  </aside>
</section>`,
  },
  {
    title: '时间线路线图',
    group: '营销',
    description: '展示版本路线、服务流程或发展历程。',
    html: `<section data-component-title="时间线路线图" class="yb-timeline" style="padding:56px 0;">
  <h2 style="margin:0 0 24px; font-size:38px;">路线图</h2>
  <div style="display:grid; gap:14px;">
    <article style="display:grid; grid-template-columns:120px 1fr; gap:18px; padding:18px; border-left:4px solid #0f766e; background:#fff;"><strong>阶段一</strong><p style="margin:0; color:#475569;">完成 CMS 页面、首页、导航和媒体库。</p></article>
    <article style="display:grid; grid-template-columns:120px 1fr; gap:18px; padding:18px; border-left:4px solid #2563eb; background:#fff;"><strong>阶段二</strong><p style="margin:0; color:#475569;">扩展低代码动态组件和变量渲染。</p></article>
    <article style="display:grid; grid-template-columns:120px 1fr; gap:18px; padding:18px; border-left:4px solid #f97316; background:#fff;"><strong>阶段三</strong><p style="margin:0; color:#475569;">接入表单、会员、内容列表和自动化发布。</p></article>
  </div>
</section>`,
  },
  {
    title: '功能对比表',
    group: '营销',
    description: '用表格方式比较套餐、版本或能力差异。',
    html: `<section data-component-title="功能对比表" class="yb-compare" style="padding:54px 0;">
  <h2 style="margin:0 0 18px; font-size:38px;">能力对比</h2>
  <div style="overflow:auto; border:1px solid #e5e7eb; border-radius:16px;">
    <table style="width:100%; min-width:720px; border-collapse:collapse; background:#fff;">
      <thead><tr style="background:#f8fafc;"><th style="padding:14px; text-align:left;">能力</th><th style="padding:14px;">基础</th><th style="padding:14px;">专业</th><th style="padding:14px;">团队</th></tr></thead>
      <tbody>
        <tr><td style="padding:14px; border-top:1px solid #e5e7eb;">CMS 构建器</td><td style="padding:14px; text-align:center;">✓</td><td style="padding:14px; text-align:center;">✓</td><td style="padding:14px; text-align:center;">✓</td></tr>
        <tr><td style="padding:14px; border-top:1px solid #e5e7eb;">动态变量</td><td style="padding:14px; text-align:center;">-</td><td style="padding:14px; text-align:center;">✓</td><td style="padding:14px; text-align:center;">✓</td></tr>
        <tr><td style="padding:14px; border-top:1px solid #e5e7eb;">媒体库</td><td style="padding:14px; text-align:center;">✓</td><td style="padding:14px; text-align:center;">✓</td><td style="padding:14px; text-align:center;">✓</td></tr>
      </tbody>
    </table>
  </div>
</section>`,
  },
  {
    title: '动态页脚',
    group: '动态',
    description: '站点信息、导航循环和登录状态组合。',
    html: `<footer data-component-title="动态页脚" class="yb-footer" style="display:grid; grid-template-columns: 1fr auto; gap:28px; padding:36px 0; border-top:1px solid #e5e7eb;">
  <div><strong style="font-size:22px;">{{site.name}}</strong><p style="margin:8px 0 0; color:#64748b;">{{site.description}}</p><small style="color:#94a3b8;">{{auth.welcome}}</small></div>
  <nav data-yb-repeat="navigation" style="display:flex; gap:10px; flex-wrap:wrap; justify-content:flex-end;">
    <a href="{{item.url}}" style="color:#475569; text-decoration:none;">{{item.label}}</a>
  </nav>
</footer>`,
  },
  {
    title: '登录态仪表入口',
    group: '动态',
    description: '登录用户看到控制台入口，游客看到登录入口。',
    html: `<section data-component-title="登录态仪表入口" class="yb-auth-dashboard" style="padding:34px; border-radius:20px; background:#0f172a; color:#fff;">
  <div data-visible-when="guest" style="display:flex; align-items:center; justify-content:space-between; gap:18px; flex-wrap:wrap;"><div><h2 style="margin:0;">访问更多内容</h2><p style="color:rgba(255,255,255,.74);">登录后可进入个人中心并查看专属导航。</p></div><a href="/login" style="padding:12px 18px; border-radius:10px; background:#fff; color:#0f172a; font-weight:800; text-decoration:none;">登录</a></div>
  <div data-visible-when="logged-in" style="display:flex; align-items:center; justify-content:space-between; gap:18px; flex-wrap:wrap;"><div><h2 style="margin:0;">{{auth.welcome}}</h2><p style="color:rgba(255,255,255,.74);">账号：{{user.username}}，头像用户 {{navUsers.count}} 位。</p></div><a href="/" style="padding:12px 18px; border-radius:10px; background:#34d399; color:#052e2b; font-weight:800; text-decoration:none;">进入控制台</a></div>
</section>`,
  },
]

const groups = computed(() => ['全部', ...Array.from(new Set(blocks.map(item => item.group)))])
const filteredBlocks = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  return blocks.filter((item) => {
    const matchGroup = activeGroup.value === '全部' || item.group === activeGroup.value
    const matchKeyword = !key || `${item.title} ${item.group} ${item.description}`.toLowerCase().includes(key)
    return matchGroup && matchKeyword
  })
})

async function insertBlock(block: CmsBlock) {
  inserting.value = true
  try {
    await getPageBuilder().addComponent({
      id: null,
      title: block.title,
      html_code: block.html,
    })
    toast.success(`已插入「${block.title}」`)
    closeAddComponentModal()
  }
  finally {
    inserting.value = false
  }
}
</script>

<template>
  <section class="cms-builder-components">
    <div class="components-toolbar">
      <FaInput v-model="keyword" clearable placeholder="搜索区块、模板、变量" />
      <div class="group-tabs">
        <button v-for="group in groups" :key="group" type="button" :class="{ active: activeGroup === group }" @click="activeGroup = group">
          {{ group }}
        </button>
      </div>
    </div>

    <div class="block-grid">
      <button v-for="block in filteredBlocks" :key="block.title" type="button" class="block-card" :disabled="inserting" @click="insertBlock(block)">
        <span>{{ block.group }}</span>
        <strong>{{ block.title }}</strong>
        <small>{{ block.description }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.cms-builder-components {
  display: grid;
  gap: 14px;
}

.components-toolbar {
  display: grid;
  gap: 10px;
}

.group-tabs {
  display: flex;
  gap: 8px;
  overflow: auto;
  padding-bottom: 4px;
}

.group-tabs button {
  flex: 0 0 auto;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.group-tabs button.active {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.block-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  max-height: 640px;
  overflow: auto;
}

.block-card {
  display: grid;
  gap: 8px;
  min-height: 146px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  text-align: left;
}

.block-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.block-card span {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 800;
}

.block-card strong {
  font-size: 17px;
}

.block-card small {
  color: var(--color-text-3);
  line-height: 1.5;
}

@media (max-width: 920px) {
  .block-grid {
    grid-template-columns: 1fr;
  }
}
</style>
