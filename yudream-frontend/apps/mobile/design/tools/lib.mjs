/**
 * Pixso 移动端 UI 构建公共库：
 * - FaTheme0 token（shadcn 中性单色系，宿主 packages/themes/index.ts 实测换算）
 * - 注入到每个 eval_script 的 helper 集（自动布局盒模型/文本/图标）
 * - 运行器：拼接脚本 → eval_script → 返回节点 id → 截图落盘
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(ROOT, '.pixso-out');

/** FaTheme0 亮色（OKLCH 已换算 sRGB） */
export const LIGHT = {
  mode: 'light',
  bg: '#f2f2f2', surface: '#ffffff', elevated: '#ffffff',
  fg: '#0a0a0a', fg2: '#737373', fg3: '#a3a3a3',
  primary: '#171717', primaryFg: '#fafafa',
  muted: '#f5f5f5', mutedFg: '#737373',
  secondary: '#f5f5f5', secondaryFg: '#171717',
  border: '#e5e5e5', input: '#e5e5e5', ring: '#a3a3a3',
  destructive: '#dc2626', success: '#16a34a', scrim: '#000000',
};

/** FaTheme0 暗色 */
export const DARK = {
  mode: 'dark',
  bg: '#0a0a0a', surface: '#171717', elevated: '#262626',
  fg: '#fafafa', fg2: '#a3a3a3', fg3: '#737373',
  primary: '#e5e5e5', primaryFg: '#171717',
  muted: '#262626', mutedFg: '#a3a3a3',
  secondary: '#262626', secondaryFg: '#fafafa',
  border: '#262626', input: '#262626', ring: '#525252',
  destructive: '#ef4444', success: '#22c55e', scrim: '#000000',
};

/** lucide 图标（24×24，stroke 2，shadcn 同源） */
export const ICONS = {
  home: '<path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>',
  search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/>',
  user: '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  'chevron-left': '<path d="m15 18-6-6 6-6"/>',
  'chevron-right': '<path d="m9 18 6-6-6-6"/>',
  'chevron-down': '<path d="m6 9 6 6 6-6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  settings: '<path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/>',
  globe: '<circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2"/><path d="M12 20v2"/><path d="m4.93 4.93 1.41 1.41"/><path d="m17.66 17.66 1.41 1.41"/><path d="M2 12h2"/><path d="M20 12h2"/><path d="m6.34 17.66-1.41 1.41"/><path d="m19.07 4.93-1.41 1.41"/>',
  moon: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
  monitor: '<rect width="20" height="14" x="2" y="3" rx="2"/><line x1="8" x2="16" y1="21" y2="21"/><line x1="12" x2="12" y1="17" y2="21"/>',
  'trash-2': '<path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>',
  'log-out': '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" x2="9" y1="12" y2="12"/>',
  'message-circle': '<path d="M7.9 20A9 9 0 1 0 4 16.1L2 22Z"/>',
  'thumbs-up': '<path d="M7 10v12"/><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"/>',
  eye: '<path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"/><circle cx="12" cy="12" r="3"/>',
  share: '<path d="M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8"/><polyline points="16 6 12 2 8 6"/><line x1="12" x2="12" y1="2" y2="15"/>',
  ellipsis: '<circle cx="12" cy="12" r="1.4" fill="FILL" stroke="none"/><circle cx="19" cy="12" r="1.4" fill="FILL" stroke="none"/><circle cx="5" cy="12" r="1.4" fill="FILL" stroke="none"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  send: '<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  'layout-grid': '<rect width="7" height="7" x="3" y="3" rx="1"/><rect width="7" height="7" x="14" y="3" rx="1"/><rect width="7" height="7" x="14" y="14" rx="1"/><rect width="7" height="7" x="3" y="14" rx="1"/>',
  server: '<rect width="20" height="8" x="2" y="2" rx="2"/><rect width="20" height="8" x="2" y="14" rx="2"/><line x1="6" x2="6.01" y1="6" y2="6"/><line x1="6" x2="6.01" y1="18" y2="18"/>',
  'book-open': '<path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
  'refresh-cw': '<path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/><path d="M21 3v5h-5"/><path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/><path d="M8 16H3v5"/>',
  wifi: '<path d="M5 13a10 10 0 0 1 14 0"/><path d="M8.5 16.5a5 5 0 0 1 7 0"/><path d="M2 8.82a15 15 0 0 1 20 0"/><line x1="12" x2="12.01" y1="20" y2="20"/>',
  'shield-check': '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>',
  clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
  image: '<rect width="18" height="18" x="3" y="3" rx="2"/><circle cx="9" cy="9" r="2"/><path d="m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"/>',
  'circle-plus': '<circle cx="12" cy="12" r="10"/><path d="M8 12h8"/><path d="M12 8v8"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/><rect width="10" height="4" x="5" y="10" rx="0.5" fill="FILL" stroke="none"/>',
  'signal-bars': '<line x1="4" x2="4" y1="18" y2="14" stroke-width="2.6"/><line x1="9" x2="9" y1="18" y2="11" stroke-width="2.6"/><line x1="14" x2="14" y1="18" y2="8" stroke-width="2.6"/><line x1="19" x2="19" y1="18" y2="5" stroke-width="2.6" opacity="0.35"/>',
  skin: '<path d="M20 6h-4V4a2 2 0 0 0-4 0v2H4a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h0a2 2 0 0 1 2 2v5a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2v-5a2 2 0 0 1 2-2h0a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2z"/>',
};

/**
 * 注入到 eval_script 的 helper 库。T 由调用方在脚本头注入。
 * 自动布局盒模型约定：o.w/o.h = number | 'fill' | 'hug'；
 * 纵向父容器里 'fill' 宽 → layoutAlign STRETCH，'fill' 高 → layoutGrow 1。
 */
export const LIB = `
function hx(h){h=h.replace('#','');if(h.length===3){h=h.split('').map(function(c){return c+c;}).join('');}var n=parseInt(h,16);return {r:((n>>16)&255)/255,g:((n>>8)&255)/255,b:(n&255)/255};}
function sol(h,o){var p={type:'SOLID',color:hx(h)};if(o!==undefined){p.opacity=o;}return p;}
var FR={'r':'Light','m':'Medium','b':'Bold'};
async function preloadFonts(){for(var k in FR){await pixso.loadFontAsync({family:'HarmonyOS Sans SC',style:FR[k]});}}
function txt(p,s,o){o=o||{};var t=pixso.createText();t.fontName={family:'HarmonyOS Sans SC',style:FR[o.w||'r']};t.characters=String(s);t.fontSize=o.s||15;var lh=o.lh||Math.round((o.s||15)*1.45);t.lineHeight={value:lh,unit:'PIXELS'};if(o.ls){t.letterSpacing={value:o.ls,unit:'PIXELS'};}t.fills=[sol(o.c||T.fg)];if(o.align){t.textAlignHorizontal=o.align;}if(o.op!==undefined){t.opacity=o.op;}if(o.fillW){t.textAutoResize='HEIGHT';}else{t.textAutoResize='WIDTH_AND_HEIGHT';}p.appendChild(t);if(o.fillW){if(p.layoutMode==='HORIZONTAL'){t.layoutGrow=1;}else{t.layoutAlign='STRETCH';}}return t;}
function box(p,o){o=o||{};var f=pixso.createFrame();if(o.name){f.name=o.name;}
 if(o.dir){f.layoutMode=o.dir;f.itemSpacing=o.gap||0;f.paddingTop=o.pt||0;f.paddingBottom=o.pb||0;f.paddingLeft=o.pl||0;f.paddingRight=o.pr||0;f.primaryAxisAlignItems=o.justify||'MIN';f.counterAxisAlignItems=o.align||'MIN';}
 if(o.fill){f.fills=[sol(o.fill,o.fillOp)];}else{f.fills=[];}
 if(o.grad){f.fills=[o.grad];}
 if(o.r){f.cornerRadius=o.r;}if(o.tl){f.topLeftRadius=o.tl;}if(o.tr){f.topRightRadius=o.tr;}if(o.bl){f.bottomLeftRadius=o.bl;}if(o.br){f.bottomRightRadius=o.br;}
 if(o.stroke){f.strokes=[sol(o.stroke)];f.strokeWeight=o.sw||1;f.strokeAlign='INSIDE';if(o.dash){f.dashPattern=[6,6];}}else{f.strokes=[];}
 if(o.shadow){f.effects=[{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:o.shadow},offset:{x:0,y:o.sy===undefined?2:o.sy},radius:o.blur||8,spread:0,visible:true,blendMode:'NORMAL'}];}else{f.effects=[];}
 f.clipsContent=o.clip?true:false;
 p.appendChild(f);
 var w=o.w,h=o.h;
 if(o.dir){
  f.resize(typeof w==='number'?w:(typeof h==='number'?h:100),typeof h==='number'?h:(typeof w==='number'?w:100));
  if(o.dir==='VERTICAL'){
   if(w==='hug'||w===undefined){f.counterAxisSizingMode='AUTO';}else{f.counterAxisSizingMode='FIXED';}
   if(h==='hug'||h===undefined){f.primaryAxisSizingMode='AUTO';}else{f.primaryAxisSizingMode='FIXED';}
  }else{
   if(w==='hug'||w===undefined){f.primaryAxisSizingMode='AUTO';}else{f.primaryAxisSizingMode='FIXED';}
   if(h==='hug'||h===undefined){f.counterAxisSizingMode='AUTO';}else{f.counterAxisSizingMode='FIXED';}
  }
  var pm=p.layoutMode;
  if(w==='fill'){if(pm==='HORIZONTAL'){f.layoutGrow=1;}else{f.layoutAlign='STRETCH';}}
  if(h==='fill'){if(pm==='VERTICAL'){f.layoutGrow=1;}else{f.layoutAlign='STRETCH';}}
 }else{
  f.resize(typeof w==='number'?w:100,typeof h==='number'?h:100);
  if(w==='fill'||h==='fill'){f.layoutAlign='STRETCH';}
 }
 if(o.x!==undefined){f.x=o.x;f.y=o.y||0;}
 return f;}
function icon(p,name,o){o=o||{};var s=o.size||20,c=o.c||T.fg,sw=o.sw||2;var body=(ICONS[name]||'').replace(/FILL/g,c);
 var svg='<svg width="'+s+'" height="'+s+'" viewBox="0 0 24 24" fill="none" stroke="'+c+'" stroke-width="'+sw+'" stroke-linecap="round" stroke-linejoin="round">'+body+'</svg>';
 var n=pixso.createNodeFromSvg(svg);n.name='ic-'+name;n.fills=[];p.appendChild(n);
 if(o.center){n.layoutAlign='CENTER';}
 return n;}
function avatar(p,initial,o){o=o||{};var s=o.size||40;var a=box(p,{w:s,h:s,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:o.fill||T.primary,r:999,name:'avatar'});txt(a,initial,{s:Math.round(s*0.4),w:'m',c:o.c||T.primaryFg});return a;}
function badge(p,s,o){o=o||{};var b=box(p,{dir:'HORIZONTAL',pt:2,pb:2,pl:8,pr:8,fill:o.fill||T.muted,r:999,align:'CENTER',name:'badge'});txt(b,s,{s:o.s||11,w:o.w||'m',c:o.c||T.mutedFg});return b;}
`;

/** 组装单屏脚本：body 为 build() 函数体，负责创建并 return 根 frame */
export function composeScreenBody(tokens, pageName, x, y, body, screenName) {
  return [
    `const T = ${JSON.stringify(tokens)};`,
    `const ICONS = ${JSON.stringify(ICONS)};`,
    `const SCREEN_NAME = ${JSON.stringify(screenName)};`,
    LIB,
    `function targetPage(){var ps=pixso.root.children;for(var i=0;i<ps.length;i++){if(ps[i].name===${JSON.stringify(pageName)}){return ps[i];}}return pixso.currentPage;}`,
    `async function build(){`,
    `await preloadFonts();`,
    body.trim(),
    `}`,
    `const __page = targetPage();`,
    `const __frame = await build();`,
    `__page.appendChild(__frame);`,
    `__frame.x = ${JSON.stringify(x)}; __frame.y = ${JSON.stringify(y)};`,
    `if(!__frame.name || __frame.name === 'Frame'){__frame.name = SCREEN_NAME;}`,
    `return { id: __frame.id, name: __frame.name, w: __frame.width, h: __frame.height };`,
  ].join('\n');
}

/** 运行一个 eval_script 调用，返回工具输出文本 */
export function runEval(script) {
  const payload = path.join(OUT, 'payload-tmp.json');
  fs.writeFileSync(payload, JSON.stringify({ script }), 'utf8');
  const stdout = execFileSync('node', [path.join(ROOT, 'pixso.mjs'), 'callfile', 'eval_script', payload], {
    encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 180000,
  });
  return stdout.trim();
}

/** 运行屏幕构建：构建 + 截图，返回 {id, shotPath} */
export function buildScreen(name, tokens, pageName, x, y, body) {
  const script = composeScreenBody(tokens, pageName, x, y, body, name);
  const out = runEval(script);
  let id = null;
  try { id = JSON.parse(out).id; } catch { /* 构建失败时原样打印 */ }
  if (!id) { throw new Error(`[${name}] 构建失败: ${out.slice(0, 1500)}`); }
  const shot = runShot(id);
  return { id, shot };
}

export function runShot(nodeId) {
  const stdout = execFileSync('node', [path.join(ROOT, 'pixso.mjs'), 'call', 'take_screenshot', JSON.stringify({ nodeId })], {
    encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 120000,
  });
  const m = stdout.match(/\[image saved\] (.+\.png)/);
  return m ? m[1] : null;
}
