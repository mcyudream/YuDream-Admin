export type CmsChromeZone = 'header' | 'footer'
export type CmsSiteLayoutMode = 'HEADER_FOOTER' | 'HEADER_COPYRIGHT' | 'ADMIN'

const chromeCssKeys: Record<CmsChromeZone, string> = {
  header: 'chromeHeaderCss',
  footer: 'chromeFooterCss',
}

const chromeStyleActions = new Set(['set-css', 'append-css', 'set-styles'])

export function chromeCssKey(zone: CmsChromeZone) {
  return chromeCssKeys[zone]
}

export function readChromeCss(settings: Record<string, string> | undefined, zone: CmsChromeZone) {
  return settings?.[chromeCssKey(zone)] || ''
}

export function writeChromeCss(settings: Record<string, string>, zone: CmsChromeZone, css: string) {
  settings[chromeCssKey(zone)] = css
  return settings
}

export function isChromeStyleAction(action?: string) {
  return Boolean(action && chromeStyleActions.has(action))
}

export function chromeTemplate(zone: CmsChromeZone) {
  if (zone === 'header') {
    return `<header data-yb-chrome="header" class="site-layout-header">
  <div class="site-layout-header__bar">
    <a data-yb-chrome-slot="logo" class="site-layout-header__brand" href="/site">
      <img src="{{site.logo}}" alt="{{site.name}}">
      <span>{{site.name}}</span>
    </a>
    <nav data-yb-chrome-slot="navigation" class="site-layout-header__nav" data-yb-chrome-navigation>
      <div class="site-nav-item">
        <a href="{{item.url}}">{{item.label}}</a>
      </div>
    </nav>
    <div data-yb-chrome-slot="auth" class="site-layout-header__auth">
      <div data-visible-when="guest">
        <a class="ghost" href="/login">登录</a>
        <a class="primary" href="/register">注册</a>
      </div>
      <details data-visible-when="logged-in" class="site-layout-header__account">
        <summary>
          <img src="{{user.avatar}}" alt="{{user.nickname}}">
          <span>{{user.nickname}}</span>
          <i>⌄</i>
        </summary>
        <div>
          <a href="/profile">个人中心</a>
          <a class="danger" href="/logout">退出登录</a>
        </div>
      </details>
    </div>
  </div>
</header>`
  }
  return `<footer data-yb-chrome="footer" class="site-layout-footer">
  <div class="site-shell">
    <div data-yb-chrome-slot="footer-brand">
      <strong>{{site.name}}</strong>
      <p>{{site.description}}</p>
      <small>{{system.copyright.dates}}</small>
    </div>
    <nav data-yb-chrome-slot="footer-navigation" data-yb-repeat="navigation">
      <a href="{{item.url}}">{{item.label}}</a>
    </nav>
  </div>
  </footer>`
}

export function chromeFrameTemplate(content = '', layoutMode: CmsSiteLayoutMode = 'HEADER_FOOTER') {
  const mode = layoutMode || 'HEADER_FOOTER'
  const adminSidebar = mode === 'ADMIN'
    ? `<aside data-yb-chrome="admin-sidebar" data-yb-layout-slot="admin-sidebar" class="site-admin-sidebar">
  <strong>{{site.name}}</strong>
  <a href="/site">首页</a>
  <a href="{{item.url}}">{{item.label}}</a>
</aside>`
    : ''
  const footer = mode === 'HEADER_FOOTER'
    ? chromeTemplate('footer')
    : `<footer data-yb-chrome="footer" class="site-layout-copyright">{{system.copyright.dates}}</footer>`
  return `${chromeTemplate('header')}
<div data-yb-layout="${mode}" class="site-layout-frame layout-${mode.toLowerCase().replace('_', '-')}">
${adminSidebar}
<div class="site-layout-content">
<main data-yb-home-content class="site-builder-home">
${content || '<section class="yb-empty"><h1>开始设计首页内容</h1></section>'}
</main>
</div>
</div>
${footer}`
}

export function extractHomeContent(html: string) {
  if (typeof DOMParser === 'undefined') {
    return html
  }
  const doc = new DOMParser().parseFromString(`<div>${html}</div>`, 'text/html')
  const content = doc.querySelector('[data-yb-home-content]')
  return content?.innerHTML?.trim() || ''
}

/** CSS used only by the GrapesJS iframe so the locked chrome is visible while editing. */
export function chromeCanvasPreviewCss(layoutMode: CmsSiteLayoutMode = 'HEADER_FOOTER') {
  const mode = layoutMode || 'HEADER_FOOTER'
  const baseCss = `
:where(html, body) {
  min-height: 100%;
  margin: 0;
  --yb-site-bg: #f8fafc;
  --yb-site-text: #111827;
  --yb-site-heading: #0f172a;
  --yb-site-muted: #64748b;
  --yb-site-caption: #94a3b8;
  --yb-site-nav-text: #475569;
  --yb-site-text-2: #334155;
  --yb-site-border: #e5e7eb;
  --yb-site-border-2: #e2e8f0;
  --yb-site-header-bg: #ffffff;
  --yb-site-surface: #ffffff;
  --yb-site-hover: #f1f5f9;
  --yb-site-primary: #0f766e;
  --yb-site-primary-text: #ffffff;
  --yb-site-primary-btn-bg: #111827;
  --yb-site-primary-btn-text: #ffffff;
  --yb-site-hero-bg: linear-gradient(135deg, #0f766e, #1f2937);
  --yb-site-hero-text: #ffffff;
  --yb-site-danger: #b91c1c;
}
:where(html.dark, body.dark) {
  --yb-site-bg: #0f172a;
  --yb-site-text: #e2e8f0;
  --yb-site-heading: #f8fafc;
  --yb-site-muted: #94a3b8;
  --yb-site-caption: #64748b;
  --yb-site-nav-text: #cbd5e1;
  --yb-site-text-2: #e2e8f0;
  --yb-site-border: #1e293b;
  --yb-site-border-2: #334155;
  --yb-site-header-bg: #1e293b;
  --yb-site-surface: #1e293b;
  --yb-site-hover: #334155;
  --yb-site-primary: #2dd4bf;
  --yb-site-primary-text: #0f172a;
  --yb-site-primary-btn-bg: #2dd4bf;
  --yb-site-primary-btn-text: #0f172a;
  --yb-site-hero-bg: linear-gradient(135deg, #115e59, #111827);
  --yb-site-hero-text: #f8fafc;
}
:where(.site-layout-header, .site-layout-footer) {
  display: block;
  position: relative;
  box-sizing: border-box;
  width: 100%;
  border-bottom: 1px solid var(--yb-site-border);
  background: var(--yb-site-header-bg);
  color: var(--yb-site-heading);
}
:where(.site-layout-header)::before,
:where(.site-layout-footer)::before {
  position: absolute;
  z-index: 2;
  top: 8px;
  right: 10px;
  padding: 2px 6px;
  border: 1px solid var(--yb-site-border-2);
  border-radius: 4px;
  background: var(--yb-site-bg);
  color: var(--yb-site-muted);
  content: attr(data-yb-chrome);
  font: 600 10px/1.4 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  pointer-events: none;
  text-transform: uppercase;
}
:where(.site-layout-header__bar, .site-layout-footer .site-shell) {
  display: flex;
  box-sizing: border-box;
  width: min(1240px, calc(100% - 40px));
  min-height: 62px;
  margin: 0 auto;
  align-items: center;
  gap: 22px;
}
:where(.site-layout-header__brand, .site-layout-header__nav, .site-layout-header__auth, .site-layout-header__account summary, .site-layout-footer nav) {
  display: flex;
  align-items: center;
}
:where(.site-layout-header__brand, .site-layout-header__nav a, .site-layout-header__auth a, .site-layout-footer a) {
  color: inherit;
  text-decoration: none;
}
:where(.site-layout-header__brand) {
  min-width: 0;
  gap: 10px;
  font-size: 18px;
  font-weight: 900;
}
:where(.site-layout-header__brand img) {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
}
:where(.site-layout-header__nav) {
  flex: 1 1 auto;
  justify-content: flex-start;
  gap: 4px;
  min-width: 0;
}
:where(.site-layout-header__nav a) {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 9px;
  border-radius: 7px;
  color: var(--yb-site-nav-text);
  font-size: 14px;
  font-weight: 650;
}
:where(.site-nav-item) {
  position: relative;
}
:where(.site-layout-header__nav a:hover, .site-nav-item:hover > a) {
  background: var(--yb-site-hover);
  color: var(--yb-site-heading);
}
:where(.site-nav-dropdown) {
  position: absolute;
  top: 100%;
  left: -8px;
  z-index: 20;
  display: none;
  min-width: 168px;
  padding: 14px 8px 8px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
}
:where(.site-nav-item:hover .site-nav-dropdown, .site-nav-item:focus-within .site-nav-dropdown) {
  display: grid;
  gap: 2px;
}
:where(.site-layout-header__auth) {
  flex: 0 0 auto;
  gap: 8px;
}
:where(.site-layout-header__auth > div[data-visible-when="guest"]) {
  display: flex;
  gap: 8px;
}
:where(.site-layout-header__auth a, .site-layout-header__account summary) {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 14px;
  font-weight: 750;
  line-height: 1;
}
:where(.site-layout-header__auth a) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
:where(.site-layout-header__auth .ghost, .site-layout-header__account summary) {
  background: var(--yb-site-surface);
  color: var(--yb-site-text-2);
}
:where(.site-layout-header__auth .ghost) {
  border: 1px solid var(--yb-site-border-2);
}
:where(.site-layout-header__auth .primary) {
  background: var(--yb-site-primary-btn-bg);
  color: var(--yb-site-primary-btn-text);
}
:where(.site-layout-header__account) {
  position: relative;
}
:where(.site-layout-header__account summary) {
  gap: 7px;
  border: 0;
  list-style: none;
  cursor: pointer;
}
:where(.site-layout-header__account summary::-webkit-details-marker) {
  display: none;
}
:where(.site-layout-header__account img) {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}
:where(.site-layout-header__account i) {
  color: var(--yb-site-caption);
  font-size: 12px;
  font-style: normal;
}
:where(.site-layout-header__account > div) {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  display: grid;
  min-width: 142px;
  padding: 7px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}
:where(.site-layout-footer) {
  z-index: 1;
  min-height: 0;
  margin-top: 0;
  padding: 36px 0;
  border-top: 1px solid var(--yb-site-border);
  border-bottom: 0;
  background: var(--yb-site-surface);
}
:where(.site-layout-footer .site-shell) {
  min-height: 0;
  align-items: flex-start;
  gap: 18px;
  justify-content: space-between;
}
:where(.site-layout-footer strong) {
  font-size: 20px;
}
:where(.site-layout-footer p) {
  margin: 8px 0 0;
  color: var(--yb-site-muted);
}
:where(.site-layout-footer small) {
  display: block;
  margin-top: 12px;
  color: var(--yb-site-caption);
}
:where(.site-layout-footer nav) {
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
@media (max-width: 680px) {
  :where(.site-layout-header__bar, .site-layout-footer .site-shell) {
    width: calc(100% - 24px);
    min-height: 62px;
    gap: 12px;
  }
  :where(.site-layout-header__nav) {
    justify-content: flex-start;
    overflow: hidden;
  }
  :where(.site-layout-header__auth) {
    display: none;
  }
}
`
  const layoutCss = mode === 'ADMIN'
    ? `
:where(.site-layout-frame) {
  display: flex;
  min-height: calc(100vh - 63px);
}
:where(.site-layout-content) {
  flex: 1 1 auto;
  min-width: 0;
}
:where(.site-admin-sidebar) {
  display: grid;
  width: 240px;
  min-height: calc(100vh - 63px);
  align-content: start;
  gap: 6px;
  padding: 18px 14px;
  border-right: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
}
:where(.site-admin-sidebar strong) {
  padding: 10px 12px 14px;
  color: var(--yb-site-heading);
}
:where(.site-admin-sidebar a) {
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--yb-site-nav-text);
  text-decoration: none;
}`
    : `
:where(.site-layout-frame) {
  display: block;
  min-height: 0;
}
:where(.site-layout-content) {
  min-width: 0;
}`
  return `${baseCss}
${layoutCss}
:where(.site-layout-copyright) {
  padding: 18px;
  border-top: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  color: var(--yb-site-muted);
  text-align: center;
}
@media (max-width: 680px) {
  :where(.site-admin-sidebar) {
    width: auto;
    min-height: 0;
    border-right: 0;
    border-bottom: 1px solid var(--yb-site-border);
  }
  :where(.site-layout-frame) {
    display: block;
  }
}`.trim()
}
