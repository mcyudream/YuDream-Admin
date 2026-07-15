import type { CmsBlock } from '@/api/modules/platform-cms'

export type CmsBlockKind = 'atomic' | 'preset'

export interface CmsBlockDefinition {
  id: string
  label: string
  category: string
  kind: CmsBlockKind
  media: string
  content: string
  css?: string
  description?: string
  icon?: string
}

export function toBlockDefinition(block: CmsBlock): CmsBlockDefinition {
  const kind: CmsBlockKind = block.kind === 'PRESET' ? 'preset' : 'atomic'
  return {
    id: block.code,
    label: block.name,
    category: block.category || (block.kind === 'PRESET' ? '预制' : '自定义'),
    kind,
    media: block.previewImageUrl
      ? `<img src="${block.previewImageUrl}" style="width:100%;height:auto;object-fit:cover;border-radius:6px;" />`
      : genericBlockPreview(),
    content: block.htmlContent || '',
    css: block.cssContent,
    description: block.description,
    icon: block.icon,
  }
}

export function genericBlockPreview(): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="42" viewBox="0 0 64 42" fill="none">
    <rect width="64" height="42" rx="6" fill="#f1f5f9"/>
    <rect x="8" y="10" width="48" height="6" rx="2" fill="#cbd5e1"/>
    <rect x="8" y="22" width="36" height="4" rx="2" fill="#94a3b8"/>
  </svg>`
}

export function cmsBlocks(): CmsBlockDefinition[] {
  return [
    // 布局 - atomic structural atoms
    {
      id: 'yb-section',
      label: '区段',
      category: '布局',
      kind: 'atomic',
      media: blockPreview('section'),
      content: `<section style="padding:56px 48px; background:#ffffff;">
  <div style="max-width:1120px; margin:0 auto;"></div>
</section>`,
    },
    {
      id: 'yb-container',
      label: '内容容器',
      category: '布局',
      kind: 'atomic',
      media: blockPreview('container'),
      content: `<div style="max-width:1120px; margin:0 auto; padding:24px;"></div>`,
    },
    {
      id: 'yb-grid-2',
      label: '双列布局',
      category: '布局',
      kind: 'atomic',
      media: blockPreview('grid2'),
      content: `<div style="display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px;">
  <div style="min-height:120px; padding:20px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:20px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
</div>`,
    },
    {
      id: 'yb-grid-3',
      label: '三列布局',
      category: '布局',
      kind: 'atomic',
      media: blockPreview('grid3'),
      content: `<div style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px;">
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
</div>`,
    },

    // 文字 - atomic
    {
      id: 'yb-heading',
      label: '标题',
      category: '文字',
      kind: 'atomic',
      media: blockPreview('heading'),
      content: `<h2 style="margin:0 0 14px; color:#0f172a; font-size:40px; line-height:1.1; font-weight:900;">页面标题</h2>`,
    },
    {
      id: 'yb-paragraph',
      label: '段落',
      category: '文字',
      kind: 'atomic',
      media: blockPreview('paragraph'),
      content: `<p style="margin:0 0 16px; color:#475569; font-size:16px; line-height:1.8;">这里填写正文内容，可以在右侧面板调整字体、颜色、行高和间距。</p>`,
    },
    {
      id: 'yb-list-item',
      label: '列表项',
      category: '文字',
      kind: 'atomic',
      media: blockPreview('listItem'),
      content: `<li style="margin-bottom:8px; color:#475569; line-height:1.7;">列表项</li>`,
    },
    {
      id: 'yb-quote',
      label: '引用',
      category: '文字',
      kind: 'atomic',
      media: blockPreview('quote'),
      content: `<blockquote style="margin:0 0 16px; padding:12px 16px; border-left:4px solid #0f766e; color:#334155; background:#f0fdfa;">引用文本</blockquote>`,
    },
    {
      id: 'yb-link',
      label: '链接',
      category: '文字',
      kind: 'atomic',
      media: blockPreview('link'),
      content: `<a href="/site" style="color:#0f766e; font-weight:700; text-decoration:underline;">链接文本</a>`,
    },

    // 基础组件 - atomic
    {
      id: 'yb-button',
      label: '按钮',
      category: '基础组件',
      kind: 'atomic',
      media: blockPreview('button'),
      content: `<a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:42px; padding:0 18px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">立即查看</a>`,
    },
    {
      id: 'yb-card',
      label: '卡片',
      category: '基础组件',
      kind: 'atomic',
      media: blockPreview('card'),
      content: `<article style="display:grid; width:min(100%, 380px); gap:12px; padding:22px; border:1px solid #e5e7eb; border-radius:12px; background:#ffffff; box-shadow:0 10px 28px rgba(15,23,42,.06);">
  <h3 style="margin:0; color:#0f172a; font-size:22px;">卡片标题</h3>
  <p style="margin:0; color:#64748b; line-height:1.7;">卡片内容描述。</p>
</article>`,
    },
    {
      id: 'yb-divider',
      label: '分割线',
      category: '基础组件',
      kind: 'atomic',
      media: blockPreview('divider'),
      content: `<hr style="width:100%; margin:28px 0; border:0; border-top:1px solid #e5e7eb;">`,
    },
    {
      id: 'yb-spacer',
      label: '间距',
      category: '基础组件',
      kind: 'atomic',
      media: blockPreview('spacer'),
      content: `<div style="height:24px;"></div>`,
    },
    {
      id: 'yb-badge',
      label: '徽章',
      category: '基础组件',
      kind: 'atomic',
      media: blockPreview('badge'),
      content: `<span style="display:inline-flex; align-items:center; min-height:26px; padding:0 10px; border-radius:999px; background:#ecfdf5; color:#047857; font-size:12px; font-weight:700;">标签</span>`,
    },

    // 媒体 - atomic
    {
      id: 'yb-image',
      label: '图片',
      category: '媒体',
      kind: 'atomic',
      media: blockPreview('image'),
      content: `<img src="{{site.logo}}" alt="图片" style="display:block; width:100%; max-width:720px; aspect-ratio:16/9; object-fit:cover; border-radius:12px; background:#e2e8f0;">`,
    },

    // 动态数据 - atomic
    {
      id: 'yb-repeat-wrapper',
      label: '动态循环容器',
      category: '动态数据',
      kind: 'atomic',
      media: blockPreview('repeat'),
      content: `<div data-yb-repeat="pages" style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px;"></div>`,
    },
    {
      id: 'yb-page-card',
      label: '页面数据卡片',
      category: '动态数据',
      kind: 'atomic',
      media: blockPreview('pageCard'),
      content: `<article style="display:grid; gap:12px; padding:18px; border:1px solid #e5e7eb; border-radius:12px; background:#ffffff;">
  <img src="{{item.coverImageUrl}}" alt="{{item.title}}" style="width:100%; aspect-ratio:16/10; object-fit:cover; border-radius:10px; background:#e2e8f0;">
  <small style="color:#0f766e; font-weight:800;">{{item.category}} · {{item.publishedAt}}</small>
  <h3 style="margin:0; color:#0f172a; font-size:22px;">{{item.title}}</h3>
  <p style="margin:0; color:#64748b;">{{item.excerpt}}</p>
  <a href="{{item.url}}" style="color:#0f766e; font-weight:800;">阅读全文</a>
</article>`,
    },
    {
      id: 'yb-tag-link',
      label: '分类/标签链接',
      category: '动态数据',
      kind: 'atomic',
      media: blockPreview('tag'),
      content: `<a href="{{item.url}}" style="display:inline-flex; align-items:center; min-height:34px; padding:0 12px; border-radius:999px; background:#ecfdf5; color:#047857; font-weight:700; text-decoration:none;">{{item.label}} · {{item.count}}</a>`,
    },
    {
      id: 'yb-guest-box',
      label: '游客可见容器',
      category: '动态数据',
      kind: 'atomic',
      media: blockPreview('visible'),
      content: `<div data-visible-when="guest" style="padding:20px; border:1px dashed #cbd5e1; border-radius:12px;">游客可见内容</div>`,
    },
    {
      id: 'yb-user-box',
      label: '登录可见容器',
      category: '动态数据',
      kind: 'atomic',
      media: blockPreview('visible'),
      content: `<div data-visible-when="logged-in" style="padding:20px; border:1px dashed #cbd5e1; border-radius:12px;">{{auth.welcome}}</div>`,
    },

    // 预制 - presets
    {
      id: 'yb-hero-center',
      label: '居中首屏',
      category: '预制',
      kind: 'preset',
      media: blockPreview('heroCenter'),
      content: `<section style="padding:80px 24px; text-align:center; background:#ffffff;">
  <div style="max-width:800px; margin:0 auto;">
    <h1 style="margin:0 0 16px; font-size:48px; line-height:1.1; font-weight:900; color:#0f172a;">主标题</h1>
    <p style="margin:0 0 28px; font-size:18px; color:#64748b; line-height:1.7;">副标题描述文案，可以在这里介绍核心价值。</p>
    <div style="display:flex; gap:12px; justify-content:center;">
      <a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:44px; padding:0 20px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">立即开始</a>
      <a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:44px; padding:0 20px; border-radius:8px; border:1px solid #e5e7eb; color:#334155; font-weight:700; text-decoration:none;">了解更多</a>
    </div>
  </div>
</section>`,
    },
    {
      id: 'yb-hero-split',
      label: '双栏首屏',
      category: '预制',
      kind: 'preset',
      media: blockPreview('heroSplit'),
      content: `<section style="padding:64px 24px; background:#ffffff;">
  <div style="display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:40px; max-width:1120px; margin:0 auto; align-items:center;">
    <div>
      <h1 style="margin:0 0 16px; font-size:44px; line-height:1.1; font-weight:900; color:#0f172a;">主标题</h1>
      <p style="margin:0 0 24px; font-size:16px; color:#64748b; line-height:1.8;">左侧文案区域，用来展示品牌价值和关键信息。</p>
      <a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:42px; padding:0 18px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">立即查看</a>
    </div>
    <div style="min-height:260px; border-radius:16px; background:#e2e8f0;"></div>
  </div>
</section>`,
    },
    {
      id: 'yb-features-3',
      label: '三列特性',
      category: '预制',
      kind: 'preset',
      media: blockPreview('features3'),
      content: `<section style="padding:64px 24px; background:#f8fafc;">
  <div style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:24px; max-width:1120px; margin:0 auto;">
    <div style="padding:24px; border-radius:16px; background:#ffffff; border:1px solid #e5e7eb; text-align:center;">
      <div style="width:48px; height:48px; margin:0 auto 16px; display:flex; align-items:center; justify-content:center; border-radius:50%; background:#f0fdfa; font-size:24px;">✦</div>
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">特性一</h3>
      <p style="margin:0; color:#64748b; line-height:1.7;">特性描述文案。</p>
    </div>
    <div style="padding:24px; border-radius:16px; background:#ffffff; border:1px solid #e5e7eb; text-align:center;">
      <div style="width:48px; height:48px; margin:0 auto 16px; display:flex; align-items:center; justify-content:center; border-radius:50%; background:#f0fdfa; font-size:24px;">★</div>
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">特性二</h3>
      <p style="margin:0; color:#64748b; line-height:1.7;">特性描述文案。</p>
    </div>
    <div style="padding:24px; border-radius:16px; background:#ffffff; border:1px solid #e5e7eb; text-align:center;">
      <div style="width:48px; height:48px; margin:0 auto 16px; display:flex; align-items:center; justify-content:center; border-radius:50%; background:#f0fdfa; font-size:24px;">◆</div>
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">特性三</h3>
      <p style="margin:0; color:#64748b; line-height:1.7;">特性描述文案。</p>
    </div>
  </div>
</section>`,
    },
    {
      id: 'yb-cta-box',
      label: '行动召唤',
      category: '预制',
      kind: 'preset',
      media: blockPreview('ctaBox'),
      content: `<section style="padding:48px 24px; background:#ffffff;">
  <div style="max-width:720px; margin:0 auto; padding:48px; border-radius:20px; text-align:center; background:#0f766e; color:#ffffff;">
    <h2 style="margin:0 0 12px; font-size:32px; font-weight:900;">准备好开始了吗？</h2>
    <p style="margin:0 0 24px; line-height:1.7; opacity:.9;">描述文案，激励用户采取下一步行动。</p>
    <a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:44px; padding:0 24px; border-radius:8px; background:#ffffff; color:#0f766e; font-weight:800; text-decoration:none;">立即行动</a>
  </div>
</section>`,
    },
    {
      id: 'yb-testimonial',
      label: '客户评价',
      category: '预制',
      kind: 'preset',
      media: blockPreview('testimonial'),
      content: `<section style="padding:64px 24px; background:#ffffff;">
  <div style="max-width:720px; margin:0 auto; padding:32px; border-radius:16px; border:1px solid #e5e7eb; background:#ffffff;">
    <p style="margin:0 0 20px; font-size:18px; color:#334155; line-height:1.8;">"这里是客户评价内容，展示真实用户的声音和反馈。"</p>
    <div style="display:flex; align-items:center; gap:14px;">
      <div style="width:44px; height:44px; border-radius:50%; background:#e2e8f0;"></div>
      <div>
        <div style="font-weight:800; color:#0f172a;">用户名</div>
        <div style="font-size:14px; color:#64748b;">职位 / 公司</div>
      </div>
    </div>
  </div>
</section>`,
    },
    {
      id: 'yb-pricing-3',
      label: '三列定价',
      category: '预制',
      kind: 'preset',
      media: blockPreview('pricing3'),
      content: `<section style="padding:64px 24px; background:#f8fafc;">
  <div style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:24px; max-width:1040px; margin:0 auto;">
    <div style="padding:28px; border-radius:16px; background:#ffffff; border:1px solid #e5e7eb;">
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">基础版</h3>
      <div style="font-size:32px; font-weight:900; color:#0f172a; margin-bottom:16px;">¥0</div>
      <ul style="margin:0 0 20px; padding:0; list-style:none; color:#64748b; line-height:1.8;">
        <li>功能一</li>
        <li>功能二</li>
      </ul>
      <a href="/site" style="display:block; text-align:center; padding:10px; border-radius:8px; background:#f1f5f9; color:#334155; font-weight:800; text-decoration:none;">选择方案</a>
    </div>
    <div style="padding:28px; border-radius:16px; background:#ffffff; border:2px solid #0f766e;">
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">专业版</h3>
      <div style="font-size:32px; font-weight:900; color:#0f172a; margin-bottom:16px;">¥99</div>
      <ul style="margin:0 0 20px; padding:0; list-style:none; color:#64748b; line-height:1.8;">
        <li>全部基础功能</li>
        <li>高级功能</li>
      </ul>
      <a href="/site" style="display:block; text-align:center; padding:10px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">选择方案</a>
    </div>
    <div style="padding:28px; border-radius:16px; background:#ffffff; border:1px solid #e5e7eb;">
      <h3 style="margin:0 0 8px; color:#0f172a; font-size:18px;">企业版</h3>
      <div style="font-size:32px; font-weight:900; color:#0f172a; margin-bottom:16px;">¥299</div>
      <ul style="margin:0 0 20px; padding:0; list-style:none; color:#64748b; line-height:1.8;">
        <li>全部专业功能</li>
        <li>专属支持</li>
      </ul>
      <a href="/site" style="display:block; text-align:center; padding:10px; border-radius:8px; background:#f1f5f9; color:#334155; font-weight:800; text-decoration:none;">联系销售</a>
    </div>
  </div>
</section>`,
    },
    {
      id: 'yb-footer-simple',
      label: '简单页脚',
      category: '预制',
      kind: 'preset',
      media: blockPreview('footerSimple'),
      content: `<footer style="padding:48px 24px; background:#0f172a; color:#94a3b8;">
  <div style="max-width:1120px; margin:0 auto; display:grid; grid-template-columns:2fr 1fr 1fr 1fr; gap:32px;">
    <div>
      <strong style="color:#ffffff; font-size:18px;">品牌名</strong>
      <p style="margin:12px 0 0; line-height:1.7;">简短的品牌介绍或版权信息。</p>
    </div>
    <div>
      <strong style="color:#ffffff;">产品</strong>
      <ul style="margin:12px 0 0; padding:0; list-style:none; line-height:1.9;">
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">功能</a></li>
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">定价</a></li>
      </ul>
    </div>
    <div>
      <strong style="color:#ffffff;">资源</strong>
      <ul style="margin:12px 0 0; padding:0; list-style:none; line-height:1.9;">
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">文档</a></li>
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">博客</a></li>
      </ul>
    </div>
    <div>
      <strong style="color:#ffffff;">关于</strong>
      <ul style="margin:12px 0 0; padding:0; list-style:none; line-height:1.9;">
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">团队</a></li>
        <li><a href="/site" style="color:#94a3b8; text-decoration:none;">联系</a></li>
      </ul>
    </div>
  </div>
  <div style="max-width:1120px; margin:32px auto 0; padding-top:24px; border-top:1px solid #1e293b; font-size:14px; text-align:center;">© 2026 品牌名. 保留所有权利。</div>
</footer>`,
    },
  ]
}

export function blockPreview(type: string): string {
  const previewMap: Record<string, string> = {
    section: '<div class="cms-block-preview section"><span></span><strong></strong></div>',
    container: '<div class="cms-block-preview container"><strong></strong><span></span></div>',
    grid2: '<div class="cms-block-preview grid two"><span></span><span></span></div>',
    grid3: '<div class="cms-block-preview grid three"><span></span><span></span><span></span></div>',
    heading: '<div class="cms-block-preview text heading"><strong></strong><span></span></div>',
    paragraph: '<div class="cms-block-preview text paragraph"><span></span><span></span><span></span></div>',
    button: '<div class="cms-block-preview button"><span></span></div>',
    image: '<div class="cms-block-preview image"><span></span></div>',
    card: '<div class="cms-block-preview card"><strong></strong><span></span><span></span></div>',
    divider: '<div class="cms-block-preview divider"><span></span></div>',
    repeat: '<div class="cms-block-preview repeat"><span></span><span></span><span></span></div>',
    pageCard: '<div class="cms-block-preview page-card"><i></i><strong></strong><span></span></div>',
    tag: '<div class="cms-block-preview tag"><span></span><span></span><span></span></div>',
    visible: '<div class="cms-block-preview visible"><strong></strong><span></span></div>',
    spacer: '<div class="cms-block-preview spacer"><span></span></div>',
    badge: '<div class="cms-block-preview badge"><span></span><span></span></div>',
    listItem: '<div class="cms-block-preview list-item"><span></span></div>',
    quote: '<div class="cms-block-preview quote"><span></span></div>',
    link: '<div class="cms-block-preview link"><span></span></div>',
    heroCenter: '<div class="cms-block-preview hero-center"><strong></strong><span></span><em></em></div>',
    heroSplit: '<div class="cms-block-preview hero-split"><div class="hero-split-text"><strong></strong><span></span><em></em></div><div class="hero-split-media"></div></div>',
    features3: '<div class="cms-block-preview features-3"><span></span><span></span><span></span></div>',
    ctaBox: '<div class="cms-block-preview cta-box"><strong></strong><span></span><em></em></div>',
    testimonial: '<div class="cms-block-preview testimonial"><span></span><div class="testimonial-avatar"><i></i><div><strong></strong><em></em></div></div></div>',
    pricing3: '<div class="cms-block-preview pricing-3"><span></span><span></span><span></span></div>',
    footerSimple: '<div class="cms-block-preview footer-simple"><div class="footer-col-wide"><strong></strong><span></span></div><div class="footer-col"><strong></strong><span></span><span></span></div><div class="footer-col"><strong></strong><span></span><span></span></div><div class="footer-col"><strong></strong><span></span><span></span></div></div>',
  }
  return previewMap[type] || '<div class="cms-block-preview"></div>'
}
