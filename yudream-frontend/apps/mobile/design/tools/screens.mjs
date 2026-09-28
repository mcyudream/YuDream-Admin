/**
 * 逐屏构建脚本：node screens.mjs <screen-name|all|setup|list>
 * 每屏生成 eval_script 脚本 → Pixso 画布自动布局图层 → 截图落盘。
 */
import { LIGHT, DARK, runEval, buildScreen } from './lib.mjs';

const P1 = '01 宿主 App';
const P2 = '02 论坛插件 · 设想';
const P3 = '03 深色模式';
const P4 = '04 设计规范';

/** setup：清理探针、建页改名 */
function setup() {
  const script = `
const probe = await pixso.getNodeById('2:1');
if (probe) { probe.remove(); }
const names = ['01 宿主 App', '02 论坛插件 · 设想', '03 深色模式', '04 设计规范'];
pixso.currentPage.name = names[0];
const ids = [pixso.currentPage.id];
for (let i = 1; i < names.length; i++) {
  let p = null;
  for (const c of pixso.root.children) { if (c.name === names[i]) { p = c; break; } }
  if (!p) { p = pixso.createPage(); p.name = names[i]; }
  ids.push(p.id);
}
return { pages: ids };
`;
  console.log(runEval(script));
}

/** 屏幕公共零件（追加在 LIB 之后注入） */
const EXTRA = `
function root8(o){o=o||{};var f=box(pixso.currentPage,{w:o.w||360,h:o.h||800,fill:o.bg||T.bg,dir:'VERTICAL',name:SCREEN_NAME,clip:true});return f;}
function between(p){var sp=box(p,{w:'fill',h:1,dir:'HORIZONTAL',name:'spacer'});return sp;}
function statusBar(p,timeStr){
 var sb=box(p,{w:'fill',h:62,dir:'HORIZONTAL',justify:'SPACE_BETWEEN',align:'CENTER',pl:24,pr:24,name:'status-bar'});
 txt(sb,timeStr||'12:30',{s:15,w:'m',c:T.fg});
 var r=box(sb,{dir:'HORIZONTAL',gap:7,align:'CENTER',name:'indicators'});
 icon(r,'signal-bars',{size:16,c:T.fg,sw:2});
 icon(r,'wifi',{size:16,c:T.fg,sw:2});
 icon(r,'battery',{size:22,c:T.fg,sw:2});
 return sb;}
function tabBar(p,active){
 var wrap=box(p,{w:'fill',dir:'VERTICAL',pt:12,pl:21,pr:21,pb:21,gap:0,name:'tabbar-wrap'});
 var pill=box(wrap,{w:'fill',h:62,dir:'HORIZONTAL',fill:T.surface,r:36,stroke:T.border,sw:1,pl:4,pr:4,pt:4,pb:4,gap:0,name:'tabbar-pill'});
 var tabs=[['home','首页'],['layout-grid','应用'],['user','我的']];
 for (var i=0;i<tabs.length;i++){
  var on=tabs[i][0]===active;
  var item=box(pill,{w:'fill',h:'fill',dir:'VERTICAL',gap:3,justify:'CENTER',align:'CENTER',r:26,name:'tab-'+tabs[i][0]+(on?'-on':'')});
  if(on){item.fills=[sol(T.primary)];}
  icon(item,tabs[i][0],{size:18,c:on?T.primaryFg:T.fg3});
  txt(item,tabs[i][1],{s:10,w:on?'m':'r',c:on?T.primaryFg:T.fg3,ls:0.5});
 }
 return wrap;}
function navBar(p,title,rightIcon){
 var nb=box(p,{w:'fill',h:52,dir:'HORIZONTAL',align:'CENTER',pl:12,pr:12,gap:4,name:'nav'});
 var b=box(nb,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:20,name:'nav-back'});
 icon(b,'chevron-left',{size:22,c:T.fg});
 var t=txt(nb,title,{s:17,w:'b',c:T.fg,fillW:true,align:'CENTER'});
 var r=box(nb,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',name:'nav-right'});
 if(rightIcon){icon(r,rightIcon,{size:20,c:T.fg});}
 return nb;}
function chip(p,label,on,o){o=o||{};var c=box(p,{dir:'HORIZONTAL',pt:6,pb:6,pl:14,pr:14,align:'CENTER',r:999,name:'chip'+(on?'-on':'')});
 if(on){c.fills=[sol(T.primary)];}else{c.fills=[sol(o.fill||T.surface)];if(!o.plain){c.strokes=[sol(T.border)];c.strokeWeight=1;c.strokeAlign='INSIDE';}}
 txt(c,label,{s:13,w:on?'m':'r',c:on?T.primaryFg:(o.c||T.fg2)});
 return c;}
function fieldInput(p,ph,o){o=o||{};var f=box(p,{w:'fill',h:46,dir:'HORIZONTAL',align:'CENTER',pl:12,pr:12,gap:8,fill:o.fill||T.surface,r:10,name:'input'});
 f.strokes=[sol(o.stroke||T.input)];f.strokeWeight=o.sw||1;f.strokeAlign='INSIDE';
 txt(f,ph,{s:15,c:o.c||T.fg3});
 if(o.trail){icon(f,o.trail,{size:18,c:T.fg3});}
 return f;}
function btnPrimary(p,label,o){o=o||{};var b=box(p,{w:'fill',h:o.h||48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:o.fill||T.primary,r:o.r===undefined?10:o.r,name:'btn-'+label});
 txt(b,label,{s:15,w:'m',c:o.c||T.primaryFg});
 return b;}
function feedCard(p,d){
 var card=box(p,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,name:'post-card'});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 avatar(r1,d.init,{size:36});
 var col=box(r1,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(col,d.name,{s:14,w:'m'});
 txt(col,d.time,{s:12,c:T.fg3});
 if(d.tag){badge(r1,d.tag,{s:11});}
 txt(card,d.title,{s:15,w:'b',fillW:true,lh:22});
 if(d.excerpt){txt(card,d.excerpt,{s:13,c:T.fg2,fillW:true,lh:19});}
 var foot=box(card,{w:'fill',dir:'HORIZONTAL',gap:14,align:'CENTER'});
 function stat(nm,n){var s=box(foot,{dir:'HORIZONTAL',gap:4,align:'CENTER'});icon(s,nm,{size:14,c:T.fg3,sw:2});txt(s,String(n),{s:12,c:T.fg3});}
 if(d.views){stat('eye',d.views);}
 stat('message-circle',d.comments);
 stat('thumbs-up',d.likes);
 return card;}
`;

/** —— 各屏 body（build() 函数体）—— */

const cover = `
var root = root8();
var mid = box(root,{w:'fill',h:'fill',dir:'VERTICAL',justify:'CENTER',align:'CENTER',gap:0,name:'hero',pl:20,pr:20});
var logo = box(mid,{w:88,h:88,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:24,name:'logo'});
txt(logo,'Y',{s:44,w:'b',c:T.primaryFg});
var sp1 = box(mid,{w:1,h:28});
txt(mid,'YuDream Admin',{s:28,w:'b'});
var sp2 = box(mid,{w:1,h:6});
txt(mid,'移动端 UI 设计',{s:15,c:T.fg2});
var sp3 = box(mid,{w:1,h:16});
badge(mid,'FaTheme0 · 360 × 800 · Android 优先',{s:12});
var sp4 = box(mid,{w:1,h:28});
var pal = box(mid,{dir:'HORIZONTAL',gap:8,align:'CENTER',name:'palette'});
var cols=[T.primary,T.surface,T.border,T.fg2,T.bg];
for (var i=0;i<cols.length;i++){var sw=box(pal,{w:28,h:28,r:8,fill:cols[i],stroke:T.border,sw:1});}
var foot = box(root,{w:'fill',dir:'VERTICAL',gap:4,align:'CENTER',pb:40,name:'foot'});
txt(foot,'封面 · 宿主 App / 论坛插件 / 深色模式 / 设计规范',{s:11,c:T.fg3});
txt(foot,'2026-09',{s:11,c:T.fg3});
return root;
`;

const splash = `
var root = root8();
statusBar(root);
var mid = box(root,{w:'fill',h:'fill',dir:'VERTICAL',justify:'CENTER',align:'CENTER',gap:0,name:'hero'});
var logo = box(mid,{w:96,h:96,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:28,name:'logo'});
txt(logo,'Y',{s:48,w:'b',c:T.primaryFg});
var sp1 = box(mid,{w:1,h:24});
txt(mid,'YuDream Admin',{s:22,w:'b'});
var sp2 = box(mid,{w:1,h:6});
txt(mid,'多域站点 · 插件应用 · 移动直连',{s:13,c:T.fg3});
var foot = box(root,{w:'fill',dir:'VERTICAL',align:'CENTER',gap:0,pb:28,name:'foot'});
txt(foot,'v1.4.19 · Android',{s:12,c:T.fg3});
return root;
`;

const welcome = `
var root = root8();
statusBar(root);
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:0,pl:20,pr:20,name:'content'});
var hero = box(c,{w:'fill',dir:'VERTICAL',align:'CENTER',gap:0,pt:36,pb:28,name:'hero'});
var logo = box(hero,{w:84,h:84,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:24,name:'logo'});
txt(logo,'Y',{s:40,w:'b',c:T.primaryFg});
var s1 = box(hero,{w:1,h:18});
txt(hero,'YuDream Admin',{s:24,w:'b'});
var s2 = box(hero,{w:1,h:6});
txt(hero,'一站式管理你的游戏社区站点',{s:14,c:T.fg2});
var feats = box(c,{w:'fill',dir:'VERTICAL',gap:14,name:'features'});
var data=[
 ['globe','多域站点管理','一个 App 管理所有已接入的站点域'],
 ['layout-grid','插件应用热加载','应用即装即用，无需重装客户端'],
 ['bell','实时通知','站内通知与推送直达手机']
];
for (var i=0;i<data.length;i++){
 var row=box(feats,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,name:'feature-'+i});
 var tile=box(row,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10});
 icon(tile,data[i][0],{size:20,c:T.fg});
 var col=box(row,{dir:'VERTICAL',gap:2,w:'fill'});
 txt(col,data[i][1],{s:15,w:'m'});
 txt(col,data[i][2],{s:12,c:T.fg3,fillW:true});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',gap:12,pb:8,name:'foot'});
btnPrimary(foot,'开始使用');
var alt = box(foot,{w:'fill',dir:'HORIZONTAL',justify:'CENTER',align:'CENTER'});
txt(alt,'已有站点？直接登录',{s:14,c:T.fg2});
return root;
`;

const login = `
var root = root8();
statusBar(root);
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:0,pl:20,pr:20,pt:4,name:'content'});
var brand = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pb:20,name:'brand'});
var logo = box(brand,{w:44,h:44,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:12});
txt(logo,'Y',{s:22,w:'b',c:T.primaryFg});
var bcol = box(brand,{dir:'VERTICAL',gap:1,w:'fill'});
txt(bcol,'YuDream Admin',{s:17,w:'b'});
txt(bcol,'余梦站 · yudream.online',{s:12,c:T.fg3});
var sw = box(brand,{dir:'HORIZONTAL',gap:2,align:'CENTER'});
txt(sw,'切换',{s:13,c:T.fg2});
icon(sw,'chevron-right',{size:14,c:T.fg3});
var h1 = txt(c,'欢迎回来',{s:22,w:'b'});
var s1 = box(c,{w:1,h:4});
txt(c,'使用站点账号登录当前域',{s:13,c:T.fg2});
var s2 = box(c,{w:1,h:16});
var form = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'form'});
txt(form,'用户名',{s:13,w:'m',c:T.fg2});
fieldInput(form,'用户名 / 邮箱');
var g1 = box(form,{w:1,h:6});
txt(form,'密码',{s:13,w:'m',c:T.fg2});
fieldInput(form,'••••••••',{trail:'eye'});
var aux = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER',pt:12});
var rem = box(aux,{dir:'HORIZONTAL',gap:6,align:'CENTER'});
var cb = box(rem,{w:16,h:16,r:4,fill:T.primary,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER'});
icon(cb,'check',{size:11,c:T.primaryFg,sw:3});
txt(rem,'记住我',{s:13,c:T.fg2});
between(aux);
txt(aux,'忘记密码？',{s:13,c:T.fg2});
var s3 = box(c,{w:1,h:20});
btnPrimary(c,'登 录',{h:48});
var s4 = box(c,{w:1,h:14});
var div = box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',name:'divider'});
var l1 = box(div,{w:'fill',h:1,fill:T.border});
txt(div,'或',{s:12,c:T.fg3});
var l2 = box(div,{w:'fill',h:1,fill:T.border});
var s5 = box(c,{w:1,h:14});
var guest = box(c,{w:'fill',dir:'HORIZONTAL',justify:'CENTER'});
txt(guest,'先逛逛 · 游客模式',{s:14,c:T.fg2});
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:6,name:'foot'});
txt(foot,'登录即代表同意《用户协议》与《隐私政策》',{s:11,c:T.fg3,align:'CENTER',fillW:true});
return root;
`;

const home = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:10,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
var hw = box(hr,{dir:'VERTICAL',gap:2});
txt(hw,'早上好，SiberianHusky',{s:18,w:'b'});
var dom = box(hw,{dir:'HORIZONTAL',gap:3,align:'CENTER'});
txt(dom,'余梦站',{s:12,c:T.fg2});
icon(dom,'chevron-down',{size:13,c:T.fg3});
between(hr);
var hbtns = box(hr,{dir:'HORIZONTAL',gap:8});
var b1 = box(hbtns,{w:36,h:36,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:18,stroke:T.border,sw:1});
icon(b1,'layout-grid',{size:18,c:T.fg});
var b2 = box(hbtns,{w:36,h:36,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:18,stroke:T.border,sw:1});
icon(b2,'refresh-cw',{size:16,c:T.fg});
var content = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:14,pl:20,pr:20,pt:6,pb:14,name:'content',clip:true});
var banner = box(content,{w:'fill',h:148,dir:'VERTICAL',justify:'MAX',r:14,clip:true,name:'banner',
 grad:{type:'GRADIENT_LINEAR',gradientTransform:[[0,1,0],[-1,0,1]],gradientStops:[
  {position:0,color:{r:0.62,g:0.64,b:0.68,a:1}},{position:1,color:{r:0.13,g:0.13,b:0.15,a:1}}]}});
var btxt = box(banner,{w:'fill',dir:'VERTICAL',gap:8,pl:16,pr:16,pb:14});
txt(btxt,'新应用上线：皮肤站移动版',{s:16,w:'b',c:'#ffffff'});
var dots = box(btxt,{dir:'HORIZONTAL',gap:5,name:'dots'});
for (var i=0;i<3;i++){var d=box(dots,{w:i===0?16:6,h:6,r:3,fill:'#ffffff'});if(i>0){d.opacity=0.45;}}
var sec = box(content,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'动态',{s:17,w:'b'});
between(sec);
var more = box(sec,{dir:'HORIZONTAL',gap:2,align:'CENTER'});
txt(more,'查看全部',{s:13,c:T.fg3});
icon(more,'chevron-right',{size:14,c:T.fg3});
feedCard(content,{init:'企',name:'企鹅管理员',time:'5 小时前',tag:'攻略',title:'1.21 服务端 TP 性能调优记录',excerpt:'把 spawn 区块视距降到 6 之后 TPS 稳定回 20，附 Lucener 断代方案与实测数据…',views:216,comments:23,likes:156});
feedCard(content,{init:'梦',name:'YuDream',time:'昨天 21:40',tag:'公告',title:'皮肤站移动版上线：角色皮肤随身管理',excerpt:'现已支持移动端直接浏览与管理角色皮肤，老用户免登录迁移…',comments:41,likes:302});
feedCard(content,{init:'星',name:'星空下的旅人',time:'2 天前',title:'分享一套生存服的基建党',excerpt:'从此告别火柴盒，附主题色变量表…',comments:12,likes:88});
tabBar(root,'home');
return root;
`;

const apps = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
txt(header,'应用',{s:20,w:'b'});
fieldInput(header,'搜索应用',{trail:'search'});
var chips = box(header,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'社区');
chip(chips,'游戏');
chip(chips,'工具');
chip(chips,'资料');
var content = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var list = [
 ['book-open','论坛','社区 · 23 新帖'],
 ['skin','皮肤站','工具 · 角色皮肤管理'],
 ['server','服务器面板','游戏 · 3 台在线'],
 ['book-open','MC 百科','资料 · 数据手册'],
 ['download','下载站','工具 · 整合包分发'],
 ['monitor','监控大屏','工具 · TPS 实时']
];
for (var r=0;r<3;r++){
 var row = box(content,{w:'fill',dir:'HORIZONTAL',gap:12,name:'grid-row-'+r});
 for (var k=0;k<2;k++){
  var it = list[r*2+k];
  var card = box(row,{w:'fill',dir:'VERTICAL',gap:8,pl:12,pr:12,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,name:'app-'+it[1]});
  var tile = box(card,{w:44,h:44,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:12});
  icon(tile,it[0],{size:22,c:T.fg});
  txt(card,it[1],{s:14,w:'m'});
  txt(card,it[2],{s:11,c:T.fg3});
 }
}
tabBar(root,'layout-grid');
return root;
`;

const profile = `
var root = root8();
statusBar(root);
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:16,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var uc = box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,name:'user-card'});
avatar(uc,'S',{size:56});
var ucol = box(uc,{dir:'VERTICAL',gap:5,w:'fill'});
var nrow = box(ucol,{dir:'HORIZONTAL',gap:6,align:'CENTER'});
txt(nrow,'SiberianHusky',{s:17,w:'b'});
badge(nrow,'站点管理员',{fill:T.primary,c:T.primaryFg,mutedFg:T.primaryFg,s:10});
var drow = box(ucol,{dir:'HORIZONTAL',gap:4,align:'CENTER'});
icon(drow,'globe',{size:12,c:T.fg3,sw:2});
txt(drow,'余梦站 · yudream.online',{s:12,c:T.fg3});
var appsec = box(c,{w:'fill',dir:'VERTICAL',gap:10,name:'my-apps'});
txt(appsec,'我的应用',{s:15,w:'b'});
var arow = box(appsec,{w:'fill',dir:'HORIZONTAL',gap:0,justify:'SPACE_BETWEEN',pl:4,pr:4});
var aps=[['message-circle','论坛'],['skin','皮肤站'],['server','面板'],['download','下载']];
for (var i=0;i<aps.length;i++){
 var ac=box(arow,{dir:'VERTICAL',gap:6,align:'CENTER'});
 var tile=box(ac,{w:52,h:52,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:14,stroke:T.border,sw:1});
 icon(tile,aps[i][0],{size:22,c:T.fg});
 txt(ac,aps[i][1],{s:11,c:T.fg2});
}
var group = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,name:'list-group'});
var items=[
 ['server','域管理','站点域与应用统一管理'],
 ['monitor','外观','当前：跟随系统'],
 ['trash-2','清除应用缓存','清空当前域的应用下载缓存'],
 ['info','关于','宿主版本 v1.4.19']
];
for (var j=0;j<items.length;j++){
 if(j>0){var line=box(group,{w:'fill',h:1,pl:0,fill:T.border});}
 var it=box(group,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:12,pt:13,pb:13,name:'li-'+items[j][1]});
 icon(it,items[j][0],{size:20,c:T.fg2,sw:2});
 var col=box(it,{dir:'VERTICAL',gap:2,w:'fill'});
 txt(col,items[j][1],{s:14,w:'m'});
 txt(col,items[j][2],{s:11,c:T.fg3});
 icon(it,'chevron-right',{size:16,c:T.fg3,sw:2});
}
var s2 = box(c,{w:1,h:0});
var lo = box(c,{w:'fill',h:46,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',gap:8,fill:T.surface,r:10,stroke:T.border,sw:1,name:'logout'});
icon(lo,'log-out',{size:16,c:T.destructive,sw:2});
txt(lo,'退出登录',{s:15,w:'m',c:T.destructive});
tabBar(root,'user');
return root;
`;

const domains = `
var root = root8();
statusBar(root);
navBar(root,'域管理');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:16,name:'content',clip:true});
txt(c,'管理已接入的站点域，激活域决定首页内容与应用列表。',{s:12,c:T.fg3,fillW:true,lh:18});
var ds=[
 ['余梦站','yudream.online',true,'4 应用'],
 ['联合测试站','test.yudream.online',false,'2 应用']
];
for (var i=0;i<ds.length;i++){
 var d=ds[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:d[2]?T.primary:T.border,sw:d[2]?1.5:1,name:'domain-'+d[0]});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 var col=box(r1,{dir:'VERTICAL',gap:2,w:'fill'});
 txt(col,d[0],{s:15,w:'b'});
 txt(col,d[1],{s:12,c:T.fg3});
 var radio=box(r1,{w:18,h:18,r:9,stroke:d[2]?T.primary:T.border,sw:d[2]?5.5:1.5,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',name:'radio'});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 badge(r2,d[3]);
 if(d[2]){badge(r2,'已激活',{fill:T.primary,c:T.primaryFg,mutedFg:T.primaryFg});}
}
var add=box(c,{w:'fill',h:54,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',gap:8,r:14,stroke:T.border,sw:1,dash:true,name:'add-domain'});
icon(add,'circle-plus',{size:18,c:T.fg3,sw:2});
txt(add,'添加站点域',{s:14,c:T.fg2});
var foot=box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:4});
txt(foot,'提示：长按域卡片可移除或切换激活。',{s:11,c:T.fg3,align:'CENTER',fillW:true});
return root;
`;

const domainAdd = `
var root = root8();
statusBar(root);
navBar(root,'添加站点域');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:8,pl:20,pr:20,pt:8,pb:16,name:'content',clip:true});
txt(c,'端点地址',{s:13,w:'m',c:T.fg2});
fieldInput(c,'https://mc.example.com');
txt(c,'支持 http(s) 站点地址，首次连接将自动获取站点清单与应用目录。',{s:12,c:T.fg3,fillW:true,lh:18});
var s1 = box(c,{w:1,h:8});
var tb = box(c,{w:'fill',h:42,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10,name:'btn-test'});
txt(tb,'测试连接',{s:14,w:'m',c:T.fg});
var s2 = box(c,{w:1,h:8});
var res = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:12,pr:12,pt:12,pb:12,fill:T.surface,r:12,stroke:T.border,sw:1,name:'test-result'});
var ok = box(res,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:16});
icon(ok,'check',{size:16,c:T.success,sw:2.5});
var col = box(res,{dir:'VERTICAL',gap:2,w:'fill'});
txt(col,'连接成功 · YuDream Admin',{s:13,w:'m'});
txt(col,'v1.4.19 · 4 个应用 · 延迟 42ms',{s:11,c:T.fg3});
var s3 = box(c,{w:1,h:12});
btnPrimary(c,'保存');
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:4});
txt(foot,'连接信息经端到端加密后保存在本机。',{s:11,c:T.fg3,align:'CENTER',fillW:true});
return root;
`;

const pluginHost = `
var root = root8();
statusBar(root);
navBar(root,'论坛','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:12,name:'content',clip:true});
var chips = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'攻略');
chip(chips,'水楼');
chip(chips,'公告');
feedCard(c,{init:'企',name:'企鹅管理员',time:'5 小时前',tag:'攻略',title:'1.21 服务端 TP 性能调优记录',excerpt:'把 spawn 区块视距降到 6 之后 TPS 稳定回 20，附实测数据…',views:216,comments:23,likes:156});
feedCard(c,{init:'梦',name:'YuDream',time:'昨天 21:40',tag:'公告',title:'皮肤站移动版上线：角色皮肤随身管理',excerpt:'现已支持移动端直接浏览与管理角色皮肤…',comments:41,likes:302});
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:6,name:'host-foot'});
var sep = box(foot,{w:'fill',h:1,fill:T.border});
var s1 = box(foot,{w:1,h:10});
txt(foot,'由「论坛」插件提供 · Module Federation',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const forumList = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
txt(header,'论坛',{s:20,w:'b'});
fieldInput(header,'搜索帖子',{trail:'search'});
var chips = box(header,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'攻略');
chip(chips,'水楼');
chip(chips,'公告');
chip(chips,'交易');
var content = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
feedCard(content,{init:'企',name:'企鹅管理员',time:'5 小时前',tag:'攻略',title:'1.21 服务端 TP 性能调优记录',excerpt:'把 spawn 区块视距降到 6 之后 TPS 稳定回 20，附 Lucene 断代方案与实测数据…',views:216,comments:23,likes:156});
feedCard(content,{init:'梦',name:'YuDream',time:'昨天 21:40',tag:'公告',title:'皮肤站移动版上线：角色皮肤随身管理',excerpt:'现已支持移动端直接浏览与管理角色皮肤，老用户免登录迁移…',comments:41,likes:302});
feedCard(content,{init:'星',name:'星空下的旅人',time:'2 天前',tag:'水楼',title:'分享一套生存服的基建党',excerpt:'从此告别火柴盒，附主题色变量表与存档下载…',views:890,comments:12,likes:88});
return root;
`;

const forumDetail = `
var root = root8();
statusBar(root);
navBar(root,'帖子详情','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:6,pb:10,name:'content',clip:true});
txt(c,'1.21 服务端 TP 性能调优记录',{s:18,w:'b',fillW:true,lh:26});
var ar = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
avatar(ar,'企',{size:36});
var acol = box(ar,{dir:'VERTICAL',gap:1,w:'fill'});
txt(acol,'企鹅管理员',{s:14,w:'m'});
txt(acol,'5 小时前 · 攻略',{s:12,c:T.fg3});
badge(ar,'楼主',{s:11});
txt(c,'最近服务器卡顿明显，排查发现 spawn 区块加载压力过大。把视距从 10 降到 6、并按 Lucene 断代整理区块后，TPS 从 12 回到 20。',{s:14,fillW:true,lh:23,c:T.fg});
txt(c,'以下是本次调优的完整数据记录与复现步骤，欢迎在评论区补充你们服的数据。',{s:14,fillW:true,lh:23,c:T.fg});
var tags = box(c,{w:'fill',dir:'HORIZONTAL',gap:8});
chip(tags,'#性能优化',false,{plain:true});
chip(tags,'#1.21',false,{plain:true});
var stats = box(c,{w:'fill',dir:'HORIZONTAL',gap:16,align:'CENTER',name:'stats'});
function stat(nm,n,label){var s=box(stats,{dir:'HORIZONTAL',gap:4,align:'CENTER'});icon(s,nm,{size:15,c:T.fg3,sw:2});txt(s,label+' '+n,{s:12,c:T.fg3});}
stat('eye',216,'浏览');
stat('message-circle',23,'评论');
stat('thumbs-up',156,'赞');
var sep = box(c,{w:'fill',h:1,fill:T.border});
txt(c,'评论 23',{s:15,w:'b'});
var cms=[
 ['星','星空下的旅人','1 小时前','我们服也遇到同样问题，视距 6 之后确实稳了。',6],
 ['梦','YuDream','33 分钟前','已置顶加精，感谢整理。',3]
];
for (var i=0;i<cms.length;i++){
 var m=cms[i];
 var cm=box(c,{w:'fill',dir:'HORIZONTAL',gap:10,name:'comment-'+i});
 avatar(cm,m[0],{size:28});
 var col=box(cm,{dir:'VERTICAL',gap:4,w:'fill'});
 var r1=box(col,{dir:'HORIZONTAL',gap:6,align:'CENTER'});
 txt(r1,m[1],{s:13,w:'m'});
 txt(r1,m[2],{s:11,c:T.fg3});
 txt(col,m[3],{s:13,fillW:true,lh:20,c:T.fg});
 var r2=box(col,{dir:'HORIZONTAL',gap:12,align:'CENTER'});
 txt(r2,'回复',{s:11,c:T.fg3});
 var lk=box(r2,{dir:'HORIZONTAL',gap:3,align:'CENTER'});
 icon(lk,'thumbs-up',{size:12,c:T.fg3,sw:2});
 txt(lk,String(m[4]),{s:11,c:T.fg3});
}
var bar = box(root,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:8,pb:8,fill:T.surface,stroke:T.border,sw:1,name:'reply-bar'});
var inp = box(bar,{w:'fill',h:38,dir:'HORIZONTAL',align:'CENTER',pl:12,fill:T.muted,r:19});
txt(inp,'说点什么…',{s:13,c:T.fg3});
var send = box(bar,{w:38,h:38,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:19});
icon(send,'send',{size:16,c:T.primaryFg,sw:2});
return root;
`;

const tokensSheet = `
var root = root8({h:1560});
statusBar(root);
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:18,pl:20,pr:20,pt:6,pb:20,name:'content',clip:true});
var head=box(c,{w:'fill',dir:'VERTICAL',gap:4});
txt(head,'FaTheme0 · 设计 Token',{s:20,w:'b'});
txt(head,'shadcn neutral 中性单色系 · 与宿主 packages/themes 同源换算',{s:12,c:T.fg3,fillW:true});
function swatchRow(title,rows){
 var sec=box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'sec-'+title});
 txt(sec,title,{s:14,w:'b'});
 for (var i=0;i<rows.length;i+=2){
  var row=box(sec,{w:'fill',dir:'HORIZONTAL',gap:10});
  for (var k=0;k<2&&i+k<rows.length;k++){
   var it=rows[i+k];
   var cell=box(row,{w:'fill',dir:'VERTICAL',gap:6});
   var sw=box(cell,{w:'fill',h:40,r:10,fill:it[1],stroke:T.border,sw:1});
   txt(cell,it[0],{s:11,w:'m'});
   txt(cell,it[1],{s:10,c:T.fg3});
  }
 }
}
swatchRow('Light 亮色',[['background','#ffffff'],['foreground','#0a0a0a'],['primary','#171717'],['primary-foreground','#fafafa'],['secondary','#f5f5f5'],['muted-foreground','#737373'],['border','#e5e5e5'],['destructive','#dc2626'],['page 区','#f2f2f2'],['ring','#a3a3a3']]);
swatchRow('Dark 暗色',[['background','#0a0a0a'],['card','#171717'],['primary','#e5e5e5'],['foreground','#fafafa'],['muted','#262626'],['border','#262626']]);
var ty=box(c,{w:'fill',dir:'VERTICAL',gap:10,name:'type'});
txt(ty,'字体阶梯 · HarmonyOS Sans SC',{s:14,w:'b'});
var ladder=[['Display 28 Bold',28,'b'],['Title 20 Bold',20,'b'],['Large 17 Regular',17,'r'],['Body 15 Regular',15,'r'],['Small 13 Regular',13,'r'],['Caption 11 Regular',11,'r']];
for (var j=0;j<ladder.length;j++){
 var row=box(ty,{w:'fill',dir:'HORIZONTAL',justify:'SPACE_BETWEEN',align:'CENTER'});
 txt(row,ladder[j][0],{s:ladder[j][1],w:ladder[j][2]});
 txt(row,ladder[j][1]+' / '+(ladder[j][2]==='b'?'Bold':'Regular'),{s:11,c:T.fg3});
}
var sp=box(c,{w:'fill',dir:'VERTICAL',gap:10,name:'spacing'});
txt(sp,'间距 · 圆角',{s:14,w:'b'});
var radii=box(sp,{w:'fill',dir:'HORIZONTAL',gap:10});
var rr=[['sm 6',6],['md 10',10],['lg 16',16],['full',999]];
for (var q=0;q<rr.length;q++){
 var cell=box(radii,{dir:'VERTICAL',gap:4,align:'CENTER',w:'fill'});
 box(cell,{w:'fill',h:36,r:rr[q][1],fill:T.muted,stroke:T.border,sw:1});
 txt(cell,rr[q][0],{s:10,c:T.fg3,align:'CENTER'});
}
return root;
`;

const componentsSheet = `
var root = root8({h:1560});
statusBar(root);
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:16,pl:20,pr:20,pt:6,pb:20,name:'content',clip:true});
txt(c,'组件样例 · FaTheme0',{s:20,w:'b'});
function secTitle(s){txt(c,s,{s:13,w:'m',c:T.fg3});}
secTitle('按钮 Button');
btnPrimary(c,'主按钮 Primary');
var b2=box(c,{w:'fill',h:48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10});txt(b2,'次按钮 Secondary',{s:15,w:'m',c:T.fg});
var b3=box(c,{w:'fill',h:48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:10,stroke:T.border,sw:1});txt(b3,'描边按钮 Outline',{s:15,w:'m',c:T.fg});
var b4=box(c,{w:'fill',h:48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.destructive,r:10});txt(b4,'危险按钮 Destructive',{s:15,w:'m',c:'#ffffff'});
var b5=box(c,{w:'fill',h:48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10});txt(b5,'禁用 Disabled',{s:15,w:'m',c:T.fg3});
secTitle('输入框 Input');
fieldInput(c,'用户名 / 邮箱');
fieldInput(c,'聚焦态',{stroke:T.ring,sw:2,c:T.fg2});
secTitle('标签 Chip / 徽章 Badge');
var crow=box(c,{w:'fill',dir:'HORIZONTAL',gap:8});
chip(crow,'全部',true);
chip(crow,'攻略');
badge(c,'默认徽章');
secTitle('列表项 List Item');
var li=box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:12,pt:13,pb:13,fill:T.surface,r:14,stroke:T.border,sw:1});
icon(li,'server',{size:20,c:T.fg2,sw:2});
var lic=box(li,{dir:'VERTICAL',gap:2,w:'fill'});
txt(lic,'域管理',{s:14,w:'m'});
txt(lic,'站点域与应用统一管理',{s:11,c:T.fg3});
icon(li,'chevron-right',{size:16,c:T.fg3,sw:2});
secTitle('分段控制 Segmented');
var seg=box(c,{w:'fill',dir:'HORIZONTAL',gap:0,fill:T.muted,r:10,pl:3,pr:3,pt:3,pb:3});
var segs=[['浅色',true],['深色',false],['跟随系统',false]];
for (var i=0;i<segs.length;i++){
 var sg=box(seg,{w:'fill',h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:8});
 if(segs[i][1]){sg.fills=[sol(T.surface)];sg.effects=[{type:'DROP_SHADOW',color:{r:0,g:0,b:0,a:0.08},offset:{x:0,y:1},radius:3,spread:0,visible:true,blendMode:'NORMAL'}];}
 txt(sg,segs[i][0],{s:13,w:segs[i][1]?'m':'r',c:segs[i][1]?T.fg:T.fg2});
}
secTitle('开关 Switch');
var swrow=box(c,{w:'fill',dir:'HORIZONTAL',gap:16,align:'CENTER'});
var on=box(swrow,{w:46,h:26,r:13,fill:T.primary,dir:'HORIZONTAL',align:'CENTER',justify:'MAX',pl:3,pr:3});
box(on,{w:20,h:20,r:10,fill:T.surface});
var off=box(swrow,{w:46,h:26,r:13,fill:T.border,dir:'HORIZONTAL',align:'CENTER',pl:3,pr:3});
box(off,{w:20,h:20,r:10,fill:T.surface});
secTitle('卡片 Card');
var cd=box(c,{w:'fill',dir:'VERTICAL',gap:8,pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,sw:1,shadow:0.06});
txt(cd,'卡片标题',{s:15,w:'m'});
txt(cd,'卡片描述文本，FaTheme0 使用 1px 边框与轻投影分层。',{s:12,c:T.fg3,fillW:true,lh:18});
secTitle('底部导航 Tab Bar');
tabBar(c,'home');
return root;
`;

const darkHome = home;
const darkProfile = profile;

/** 屏幕注册表：name → [tokens, page, x, y, body] */
export const DEFS = {
  cover: [LIGHT, P1, 0, 0, cover],
  splash: [LIGHT, P1, 420, 0, splash],
  welcome: [LIGHT, P1, 840, 0, welcome],
  login: [LIGHT, P1, 1260, 0, login],
  home: [LIGHT, P1, 1680, 0, home],
  apps: [LIGHT, P1, 2100, 0, apps],
  profile: [LIGHT, P1, 2520, 0, profile],
  domains: [LIGHT, P1, 2940, 0, domains],
  domainAdd: [LIGHT, P1, 3360, 0, domainAdd],
  pluginHost: [LIGHT, P1, 3780, 0, pluginHost],
  forumList: [LIGHT, P2, 0, 0, forumList],
  forumDetail: [LIGHT, P2, 420, 0, forumDetail],
  homeDark: [DARK, P3, 0, 0, darkHome],
  profileDark: [DARK, P3, 420, 0, darkProfile],
  tokens: [LIGHT, P4, 0, 0, tokensSheet],
  components: [LIGHT, P4, 420, 0, componentsSheet],
};

export function buildByName(name) {
  const def = DEFS[name];
  if (!def) throw new Error('未知屏幕: ' + name + '（可选: ' + Object.keys(DEFS).join(', ') + '）');
  const [tokens, page, x, y, body] = def;
  return buildScreen(name, tokens, page, x, y, EXTRA + '\n' + body);
}

// CLI
const EXTRA_INJECT = EXTRA;
if (process.argv[1] && process.argv[1].endsWith('screens.mjs')) {
  const arg = process.argv[2];
  if (arg === 'setup') { setup(); }
  else if (arg === 'list') { console.log(Object.keys(DEFS).join('\n')); }
  else if (arg && arg !== 'all') {
    const r = buildByName(arg);
    console.log(JSON.stringify(r));
  } else if (arg === 'all') {
    for (const name of Object.keys(DEFS)) {
      try {
        const r = buildByName(name);
        console.log(name, '→', JSON.stringify(r));
      } catch (e) {
        console.error('[' + name + '] 失败:', String(e.message || e).slice(0, 800));
        process.exitCode = 1;
        break;
      }
    }
  } else {
    console.log('用法: node screens.mjs setup | list | <screen> | all');
  }
}
