/**
 * 逐屏构建脚本：node screens.mjs <screen-name|all|setup|list>
 * 每屏生成 eval_script 脚本 → Pixso 画布自动布局图层 → 截图落盘。
 */
import { LIGHT, DARK, runEval, buildScreen } from './lib.mjs';

const P1 = '01 宿主 App';
const P2 = '02 插件页面';
const P3 = '03 深色模式';
const P4 = '04 设计规范';

/** Pixso 文档内已创建的图片资源 hash（经本地 8765 资源服务 createImageAsync 生成） */
const IMAGE_HASHES = {
  logo: '69b8810cadcfc64f1e1f028e51cc13b5d1140a13',
  banner: '07802bda70be73e82adee9913064c89cd3aa8a86',
  castle: '12acf06cefdcc0896e5e3e62950220ee3c6db67a',
  pickaxe: 'a65f67da8248c664d65e514c193c9f25e42c8611',
  steve: '161d1044023151b490ec0b274e90e13ca901b2cd',
  kingdom: 'ed3856d02b2440382b1117070dc0528a2b5b359a',
  shaders: '0b24ebf73147542c0e8b046c78deaafdcbfbf3e0',
};

/** setup：清理探针、建页改名 */
function setup() {
  const script = `
const probe = await pixso.getNodeById('2:1');
if (probe) { probe.remove(); }
const names = ['01 宿主 App', '02 插件页面', '03 深色模式', '04 设计规范'];
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
 if(d.img){img(card,{w:'fill',h:118,hash:d.img,r:10,clip:true,name:'post-img'});}
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
logoMark(mid,88);
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
logoMark(mid,96);
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
logoMark(hero,84);
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
logoMark(brand,44,{r:12});
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
var banner = box(content,{w:'fill',h:148,dir:'VERTICAL',justify:'MAX',r:14,clip:true,name:'banner'});
banner.fills=[{type:'IMAGE',scaleMode:'FILL',imageHash:ASSETS.banner}];
var scrim = box(banner,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pl:16,pr:16,pb:14,gap:8,name:'scrim'});
scrim.fills=[{type:'GRADIENT_LINEAR',gradientTransform:[[0,1,0],[-1,0,1]],gradientStops:[
 {position:0,color:{r:0,g:0,b:0,a:0}},{position:1,color:{r:0,g:0,b:0,a:0.6}}]}];
txt(scrim,'新应用上线：皮肤站移动版',{s:16,w:'b',c:'#ffffff'});
var dots = box(scrim,{dir:'HORIZONTAL',gap:5,name:'dots'});
for (var i=0;i<3;i++){var d=box(dots,{w:i===0?16:6,h:6,r:3,fill:'#ffffff'});if(i>0){d.opacity=0.45;}}
var sec = box(content,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'动态',{s:17,w:'b'});
between(sec);
var more = box(sec,{dir:'HORIZONTAL',gap:2,align:'CENTER'});
txt(more,'查看全部',{s:13,c:T.fg3});
icon(more,'chevron-right',{size:14,c:T.fg3});
feedCard(content,{init:'企',name:'企鹅管理员',time:'5 小时前',tag:'攻略',title:'1.21 服务端 TP 性能调优记录',excerpt:'把 spawn 区块视距降到 6 之后 TPS 稳定回 20，附 Lucener 断代方案与实测数据…',views:216,comments:23,likes:156});
feedCard(content,{init:'梦',name:'YuDream',time:'昨天 21:40',tag:'公告',title:'皮肤站移动版上线：角色皮肤随身管理',excerpt:'现已支持移动端直接浏览与管理角色皮肤，老用户免登录迁移…',img:ASSETS.pickaxe,comments:41,likes:302});
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
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:0,pl:20,pr:20,pt:8,pb:16,name:'content',clip:true});
var scan = box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:14,pt:14,pb:14,fill:T.primary,r:14,name:'scan-entry'});
var sic = box(scan,{w:44,h:44,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.secondary,r:12});
icon(sic,'qr-code',{size:24,c:T.primaryFg,sw:2});
var scol = box(scan,{dir:'VERTICAL',gap:2,w:'fill'});
txt(scol,'扫码添加',{s:16,w:'b',c:T.primaryFg});
txt(scol,'使用相机扫描站点二维码，自动填入并连接',{s:11,c:T.primaryFg,op:0.7,fillW:true});
icon(scan,'chevron-right',{size:18,c:T.primaryFg});
var div = box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pt:16,pb:16,name:'divider'});
var l1 = box(div,{w:'fill',h:1,fill:T.border});
txt(div,'或手动输入',{s:12,c:T.fg3});
var l2 = box(div,{w:'fill',h:1,fill:T.border});
var form = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'form'});
txt(form,'端点地址',{s:13,w:'m',c:T.fg2});
fieldInput(form,'https://mc.example.com');
txt(form,'支持 http(s) 站点地址，首次连接将自动获取站点清单与应用目录。',{s:12,c:T.fg3,fillW:true,lh:18});
var s1 = box(form,{w:1,h:8});
var tb = box(form,{w:'fill',h:42,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10,name:'btn-test'});
txt(tb,'测试连接',{s:14,w:'m',c:T.fg});
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
feedCard(content,{init:'企',name:'企鹅管理员',time:'5 小时前',tag:'攻略',title:'1.21 服务端 TP 性能调优记录',excerpt:'把 spawn 区块视距降到 6 之后 TPS 稳定回 20，附实测数据…',img:ASSETS.castle,views:216,comments:23,likes:156});
feedCard(content,{init:'梦',name:'YuDream',time:'昨天 21:40',tag:'公告',title:'皮肤站移动版上线：角色皮肤随身管理',excerpt:'现已支持移动端直接浏览与管理角色皮肤，老用户免登录迁移…',img:ASSETS.pickaxe,comments:41,likes:302});
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
img(c,{w:'fill',h:150,hash:ASSETS.castle,r:12,clip:true,name:'content-img'});
txt(c,'调优前后对比：TPS 均值 12 → 20，实体占用下降 41%。下面是本次调优的完整数据记录与复现步骤，欢迎在评论区补充你们服的数据。',{s:14,fillW:true,lh:23,c:T.fg});
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

/** —— 插件页面（每个适配插件的具体页面）—— */

/** —— P2 插件页面（逐屏对照 yudream-admin-plugins 真实端点设计，依据 ../PLAN.md）—— */

const wikiHome = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
txt(header,'物品图鉴',{s:20,w:'b'});
fieldInput(header,'搜索物品、方块、生物…',{trail:'search'});
var chips = box(header,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'方块');
chip(chips,'物品');
chip(chips,'生物');
chip(chips,'食物');
chip(chips,'红石');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'热门查询',{s:15,w:'b'});
between(sec);
txt(sec,'数据版本 1.21.1',{s:11,c:T.fg3});
var grid = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'grid'});
var rows=[
 [['橡木原木','oak_log','#7cb342','package'],['工作台','crafting_table','#a1887f','package'],['熔炉','furnace','#8d8d8d','package']],
 [['钻石','diamond','#4dd0e1','zap'],['钻石剑','diamond_sword','#4dd0e1','zap'],['面包','bread','#d4a056','package']],
 [['苦力怕','creeper','#66bb6a','user'],['僵尸','zombie','#7e9b6e','user'],['金苹果','golden_apple','#f2c14e','package']]
];
for (var r=0;r<rows.length;r++){
 var row=box(grid,{w:'fill',dir:'HORIZONTAL',gap:8,name:'row-'+r});
 for (var i=0;i<rows[r].length;i++){
  var it=rows[r][i];
  var cell=box(row,{w:'fill',dir:'VERTICAL',gap:6,align:'CENTER',pl:6,pr:6,pt:10,pb:10,fill:T.surface,r:12,stroke:T.border,sw:1,name:'item-'+it[1]});
  var tile=box(cell,{w:44,h:44,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:10,name:'tile'});
  tile.fills=[sol(it[2],0.18)];
  icon(tile,it[3],{size:22,c:it[2],sw:2});
  txt(cell,it[0],{s:12,w:'m'});
  txt(cell,it[1],{s:9,c:T.fg3});
 }
}
var recipe = box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'recipe-entry'});
var rr = box(recipe,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(rr,'合成配方',{s:15,w:'b'});
between(rr);
txt(rr,'查看全部配方',{s:12,c:T.fg3});
var rr2 = box(recipe,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
var rchip=box(rr2,{dir:'HORIZONTAL',gap:6,align:'CENTER',pl:8,pr:8,pt:5,pb:5,fill:T.muted,r:8});
icon(rchip,'package',{size:13,c:T.fg2,sw:2});
txt(rchip,'工作台',{s:12,c:T.fg2});
txt(rr2,'·',{s:12,c:T.fg3});
var rchip2=box(rr2,{dir:'HORIZONTAL',gap:6,align:'CENTER',pl:8,pr:8,pt:5,pb:5,fill:T.muted,r:8});
icon(rchip2,'zap',{size:13,c:T.fg2,sw:2});
txt(rchip2,'钻石剑',{s:12,c:T.fg2});
between(rr2);
icon(rr2,'chevron-right',{size:15,c:T.fg3,sw:2});
return root;
`;

const wikiItem = `
var root = root8();
statusBar(root);
navBar(root,'物品详情','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var top = box(c,{w:'fill',dir:'HORIZONTAL',gap:14,align:'CENTER',pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,sw:1,name:'item-head'});
var tile = box(top,{w:64,h:64,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:14});
tile.fills=[sol('#4dd0e1',0.18)];
icon(tile,'zap',{size:32,c:'#4dd0e1',sw:2});
var tcol = box(top,{dir:'VERTICAL',gap:4,w:'fill'});
var tr = box(tcol,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
txt(tr,'钻石剑',{s:18,w:'b'});
badge(tr,'物品');
txt(tcol,'minecraft:diamond_sword',{s:12,c:T.fg3});
var tb = box(tcol,{dir:'HORIZONTAL',gap:6});
badge(tb,'Java 1.21.1');
badge(tb,'可合成');
var info = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'info'});
var rows=[['最大堆叠','1'],['稀有度','常见'],['攻击伤害','7（+1.25 攻速）'],['燃料','否']];
for (var k=0;k<rows.length;k++){
 if(k>0){box(info,{w:'fill',h:1,fill:T.border});}
 var it=box(info,{w:'fill',dir:'HORIZONTAL',pl:14,pr:14,pt:11,pb:11});
 var lw=box(it,{w:100,dir:'HORIZONTAL'});txt(lw,rows[k][0],{s:13,c:T.fg3});
 between(it);
 txt(it,rows[k][1],{s:13,w:'m',c:T.fg});
}
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'合成配方',{s:15,w:'b'});
between(sec);
badge(sec,'工作台');
var rec = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',justify:'CENTER',pl:14,pr:14,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,sw:1,name:'recipe'});
var g = box(rec,{dir:'VERTICAL',gap:4,name:'grid3'});
var pat=[[null,'#4dd0e1',null],[null,'#4dd0e1',null],[null,'#a16207',null]];
for (var gy=0;gy<3;gy++){
 var grow=box(g,{dir:'HORIZONTAL',gap:4});
 for (var gx=0;gx<3;gx++){
  var cell=box(grow,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:8,stroke:T.border,sw:1});
  cell.fills=[sol(T.muted)];
  if(pat[gy][gx]){var d=box(cell,{w:14,h:14,r:4});d.fills=[sol(pat[gy][gx])];}
 }
}
var arrow = box(rec,{w:24,h:24,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER'});
icon(arrow,'chevron-right',{size:20,c:T.fg3,sw:2});
var out = box(rec,{w:48,h:48,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:12,stroke:T.border,sw:1});
out.fills=[sol('#4dd0e1',0.15)];
icon(out,'zap',{size:26,c:'#4dd0e1',sw:2});
var also = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'also'});
txt(also,'获取方式',{s:13,c:T.fg3});
var aw = box(also,{w:'fill',dir:'HORIZONTAL',gap:8});
chip(aw,'合成',false,{plain:true});
chip(aw,'村民交易',false,{plain:true});
chip(aw,'战利品箱',false,{plain:true});
return root;
`;

const skinHome = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'我的角色',{s:20,w:'b'});
between(hr);
var addb=box(hr,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:17,stroke:T.border,sw:1,name:'btn-add-role'});
icon(addb,'plus',{size:18,c:T.fg,sw:2});
txt(header,'管理皮肤站角色、默认角色与上传材质',{s:12,c:T.fg3});
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var roles=[
 ['SiberianHusky','经典模型 · 64×64',true],
 ['夜航星','Slim 模型 · 64×64',false]
];
for (var i=0;i<roles.length;i++){
 var r=roles[i];
 var card=box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:12,pr:12,pt:10,pb:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'role-'+r[0]});
 img(card,{w:56,h:70,hash:ASSETS.steve,r:10,clip:true,name:'preview'});
 var col=box(card,{dir:'VERTICAL',gap:4,w:'fill'});
 txt(col,r[0],{s:15,w:'b'});
 txt(col,r[1],{s:12,c:T.fg3});
 var brow=box(col,{dir:'HORIZONTAL',gap:6});
 if(r[2]){badge(brow,'默认角色',{fill:T.primary,c:T.primaryFg});}
 else{var mb=box(brow,{dir:'HORIZONTAL',pt:2,pb:2,pl:8,pr:8,fill:T.muted,r:999,align:'CENTER'});txt(mb,'设为默认',{s:11,w:'m',c:T.mutedFg});}
 var acts=box(card,{dir:'VERTICAL',gap:8,align:'CENTER'});
 var e=box(acts,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:16,name:'btn-equip'});
 icon(e,'shirt',{size:15,c:T.fg,sw:2});
 var del=box(acts,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:16,name:'btn-del'});
 icon(del,'trash-2',{size:15,c:T.fg3,sw:2});
}
var up = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'btn-upload'});
var uic=box(up,{w:38,h:38,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10});
icon(uic,'upload',{size:18,c:T.fg,sw:2});
var uc=box(up,{dir:'VERTICAL',gap:1,w:'fill'});
txt(uc,'上传新材质',{s:14,w:'m'});
txt(uc,'支持 64×64 / 64×32 PNG，存入衣柜',{s:11,c:T.fg3});
icon(up,'chevron-right',{size:16,c:T.fg3,sw:2});
var api = box(c,{w:'fill',dir:'VERTICAL',gap:6,pl:14,pr:14,pt:12,pb:12,fill:T.muted,r:14,name:'csl'});
var ar = box(api,{w:'fill',dir:'HORIZONTAL',align:'CENTER',gap:6});
icon(ar,'info',{size:14,c:T.fg3,sw:2});
txt(ar,'外置皮肤接口（CustomSkinAPI）',{s:12,w:'m',c:T.fg2});
between(ar);
icon(ar,'share',{size:14,c:T.fg3,sw:2});
txt(api,'/csl/SiberianHusky',{s:12,c:T.fg2});
return root;
`;

const skinCloset = `
var root = root8();
statusBar(root);
navBar(root,'衣柜与材质','plus');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var tabs = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'tabs'});
chip(tabs,'我的材质',true);
chip(tabs,'衣柜皮肤',false);
var c0 = box(c,{w:1,h:2});
var textures=[
 ['星夜骑士','64×64 · 上传于 3 天前',true],
 ['极地旅人','64×64 · 上传于 2 周前',false],
 ['旧版备用','64×32 · 上传于 1 个月前',false]
];
for (var i=0;i<textures.length;i++){
 var t=textures[i];
 var card=box(c,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:12,pr:12,pt:10,pb:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'tex-'+t[0]});
 img(card,{w:52,h:64,hash:ASSETS.steve,r:10,clip:true,name:'tex-preview'});
 var col=box(card,{dir:'VERTICAL',gap:4,w:'fill'});
 var nr=box(col,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 txt(nr,t[0],{s:14,w:'b'});
 if(t[2]){badge(nr,'已装备',{fill:T.primary,c:T.primaryFg});}
 txt(col,t[1],{s:11,c:T.fg3});
 var acts=box(col,{dir:'HORIZONTAL',gap:8});
 var ap=box(acts,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:10,pr:10,pt:5,pb:5,fill:T.muted,r:8,name:'btn-apply'});
 icon(ap,'check',{size:12,c:T.fg,sw:2.4});
 txt(ap,'应用到角色',{s:11,w:'m',c:T.fg});
 var dl=box(acts,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:10,pr:10,pt:5,pb:5,fill:T.surface,r:8,stroke:T.border,sw:1,name:'btn-del'});
 icon(dl,'trash-2',{size:12,c:T.fg2,sw:2});
 txt(dl,'删除',{s:11,c:T.fg2});
}
var tip = box(c,{w:'fill',dir:'VERTICAL',gap:4,pl:14,pr:14,pt:12,pb:12,fill:T.muted,r:14,name:'tip'});
txt(tip,'衣柜皮肤保存在皮肤站，启动器与网页宠物通过 CustomSkinAPI 读取。',{s:12,c:T.fg2,fillW:true,lh:18});
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:4});
btnPrimary(foot,'上传新材质到衣柜');
return root;
`;

const panelOverview = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'运维面板',{s:20,w:'b'});
between(hr);
var adminB=box(hr,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:8,pr:8,pt:3,pb:3,fill:T.primary,r:999});
icon(adminB,'shield-check',{size:11,c:T.primaryFg,sw:2.2});
txt(adminB,'管理员',{s:10,w:'m',c:T.primaryFg});
var rf=box(hr,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:17,stroke:T.border,sw:1,name:'btn-refresh'});
icon(rf,'refresh-cw',{size:16,c:T.fg2,sw:2});
var sum = box(header,{w:'fill',dir:'HORIZONTAL',gap:10,name:'summary'});
var sums=[['运行实例','3 / 5'],['在线节点','2 / 2'],['告警','1']];
for (var i=0;i<sums.length;i++){
 var sc=box(sum,{w:'fill',dir:'VERTICAL',gap:2,p:10,fill:T.surface,r:12,stroke:T.border,sw:1});
 txt(sc,sums[i][1],{s:16,w:'b',c:i===2?T.destructive:T.fg});
 txt(sc,sums[i][0],{s:11,c:T.fg3});
}
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var alert = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:12,pr:12,pt:10,pb:10,fill:T.surface,r:12,stroke:T.destructive,sw:1,name:'alert'});
icon(alert,'alert-triangle',{size:16,c:T.destructive,sw:2});
var acol=box(alert,{dir:'VERTICAL',gap:1,w:'fill'});
txt(acol,'小游戏 · 空岛 已终止',{s:13,w:'m',c:T.destructive});
txt(acol,'异常退出 · 12 分钟前 · 节点-02',{s:11,c:T.fg3});
icon(alert,'chevron-right',{size:14,c:T.fg3,sw:2});
var sec1 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec1,'实例',{s:15,w:'b'});
between(sec1);
txt(sec1,'长按进入运维',{s:11,c:T.fg3});
var insts=[
 ['生存服 · 主世界','节点-01',true,'12 / 20','20.0'],
 ['创造服 · 建筑区','节点-01',true,'9 / 30','19.9'],
 ['小游戏 · 空岛','节点-02',false,'0 / 16','—']
];
for (var j=0;j<insts.length;j++){
 var s=insts[j];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:8,pl:14,pr:14,pt:11,pb:11,fill:T.surface,r:14,stroke:T.border,sw:1,name:'inst-'+s[0]});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 box(r1,{w:9,h:9,r:5,fill:s[2]?T.success:T.destructive});
 txt(r1,s[0],{s:14,w:'b'});
 badge(r1,s[1]);
 between(r1);
 var pwb=box(r1,{w:30,h:30,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:s[2]?T.muted:T.primary,r:15,name:'btn-power'});
 icon(pwb,s[2]?'square':'play',{size:14,c:s[2]?T.destructive:T.primaryFg,sw:2});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
 badge(r2,'玩家 '+s[3]);
 badge(r2,s[2]?'TPS '+s[4]:'已停止');
 between(r2);
 icon(r2,'chevron-right',{size:14,c:T.fg3,sw:2});
}
var sec2 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec2,'节点',{s:15,w:'b'});
between(sec2);
txt(sec2,'全部在线',{s:11,c:T.success});
var nl = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'nodes'});
var nodes=[['节点-01 · 主节点','23ms','3 实例',true],['节点-02 · 备用','41ms','2 实例',true]];
for (var n=0;n<nodes.length;n++){
 var nd=nodes[n];
 if(n>0){box(nl,{w:'fill',h:1,fill:T.border});}
 var it=box(nl,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:14,pr:12,pt:11,pb:11});
 var nic=box(it,{w:30,h:30,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:8});
 icon(nic,'hard-drive',{size:15,c:T.fg2,sw:2});
 var ncol=box(it,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(ncol,nd[0],{s:13,w:'m'});
 txt(ncol,'延迟 '+nd[1]+' · '+nd[2],{s:11,c:T.fg3});
 icon(it,'chevron-right',{size:15,c:T.fg3,sw:2});
}
return root;
`;

const panelInstance = `
var root = root8();
statusBar(root);
navBar(root,'生存服 · 主世界','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:10,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var st = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:11,pb:11,fill:T.surface,r:14,stroke:T.border,sw:1,name:'status'});
box(st,{w:10,h:10,r:5,fill:T.success});
var stc = box(st,{dir:'VERTICAL',gap:1,w:'fill'});
txt(stc,'运行中 · 已持续 3 天 4 小时',{s:14,w:'b'});
txt(stc,'节点-01 · Paper 1.21.1',{s:11,c:T.fg3});
between(st);
icon(st,'chevron-right',{size:15,c:T.fg3,sw:2});
var pw = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'power'});
var pws=[
 ['rotate-ccw','重启',T.muted,T.fg2,T.fg],
 ['square','停止',T.surface,T.border,T.destructive],
 ['zap','强制终止',T.surface,T.border,T.destructive]
];
for (var i=0;i<pws.length;i++){
 var b=box(pw,{w:'fill',h:50,dir:'VERTICAL',gap:3,justify:'CENTER',align:'CENTER',fill:pws[i][2],r:12,stroke:pws[i][3],sw:1,name:'btn-'+pws[i][1]});
 icon(b,pws[i][0],{size:16,c:pws[i][4],sw:2});
 txt(b,pws[i][1],{s:10,w:'m',c:pws[i][4]});
}
var mrow1 = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,name:'m1'});
var mrow2 = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,name:'m2'});
var metrics=[[mrow1,'TPS','20.0','正常',T.success,0.99],[mrow1,'在线','12 / 20','容量 60%',T.fg,0.6],[mrow2,'CPU','23%','8 核',T.fg,0.23],[mrow2,'内存','3.2/8G','已用 41%',T.fg,0.41]];
for (var m=0;m<metrics.length;m++){
 var mt=metrics[m];
 var mc=box(mt[0],{w:'fill',dir:'VERTICAL',gap:4,p:12,fill:T.surface,r:12,stroke:T.border,sw:1});
 var vr=box(mc,{w:'fill',dir:'HORIZONTAL',gap:4,align:'CENTER'});
 txt(vr,mt[2],{s:17,w:'b',c:mt[4]});
 between(vr);
 txt(vr,mt[1],{s:9,c:T.fg3});
 var track=box(mc,{w:'fill',h:5,r:3,fill:T.muted,name:'track'});
 var bar=box(track,{w:Math.round(131*mt[5]),h:5,r:3,fill:mt[4],name:'bar'});
 txt(mc,mt[3],{s:10,c:T.fg3});
}
var con = box(c,{w:'fill',dir:'VERTICAL',gap:6,pl:12,pr:12,pt:10,pb:10,fill:'#171717',r:12,clip:true,name:'console'});
var chr = box(con,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
icon(chr,'terminal',{size:12,c:'#8ee27e',sw:2});
txt(chr,'控制台 · 实时日志',{s:11,w:'m',c:'#8ee27e'});
between(chr);
badge(chr,'已连接',{fill:'#262626',c:'#8ee27e',s:9});
txt(con,'[12:30:05] Done (4.2s)! For help, type "help"',{s:10,c:'#d4d4d4',lh:14});
txt(con,'[12:31:12] PlayerHQ joined the game',{s:10,c:'#d4d4d4',lh:14});
txt(con,'[12:32:03] Watchdog: Running 2041ms behind',{s:10,c:'#f0b47e',lh:14});
txt(con,'[12:33:11] Autosave completed.',{s:10,c:'#d4d4d4',lh:14});
var cmd = box(con,{w:'fill',h:38,dir:'HORIZONTAL',gap:8,align:'CENTER',pl:10,pr:10,fill:'#262626',r:9,name:'cmd-input'});
txt(cmd,'say 服务器将于 5 分钟后重启',{s:11,c:'#fafafa'});
between(cmd);
icon(cmd,'send',{size:14,c:'#8ee27e',sw:2});
var quick = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'quick'});
var qs=[['users','玩家 12','12 人在线'],['file-text','文件','只读浏览'],['archive','备份','最近 今天 04:30'],['calendar','计划任务','3 项启用']];
for (var q=0;q<qs.length;q++){
 var qd=qs[q];
 var qb=box(quick,{w:'fill',dir:'VERTICAL',gap:4,align:'CENTER',pt:9,pb:9,fill:T.surface,r:12,stroke:T.border,sw:1,name:'quick-'+qd[0]});
 icon(qb,qd[0],{size:16,c:T.fg,sw:2});
 txt(qb,qd[1],{s:11,w:'m'});
 txt(qb,qd[2],{s:9,c:T.fg3});
}
return root;
`;

const panelOps = `
var root = root8();
statusBar(root);
navBar(root,'备份与任务','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var bk = box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'backup'});
var br = box(bk,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
var bic=box(br,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:9});
icon(bic,'archive',{size:16,c:T.fg,sw:2});
var bcol=box(br,{dir:'VERTICAL',gap:1,w:'fill'});
txt(bcol,'实例备份',{s:14,w:'b'});
txt(bcol,'策略：每日 04:30 · 保留 7 份',{s:11,c:T.fg3});
between(br);
badge(br,'7 份');
var binfo = box(bk,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
badge(binfo,'最近 今天 04:30');
badge(binfo,'2.4 GB');
badge(binfo,'成功',{c:T.success});
var bacts = box(bk,{w:'fill',dir:'HORIZONTAL',gap:8});
var bgo=box(bacts,{w:'fill',h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',gap:6,fill:T.primary,r:10,name:'btn-backup-now'});
icon(bgo,'plus',{size:14,c:T.primaryFg,sw:2.4});
txt(bgo,'立即备份',{s:13,w:'m',c:T.primaryFg});
var brs=box(bacts,{w:'fill',h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',gap:6,fill:T.surface,r:10,stroke:T.border,sw:1,name:'btn-restore'});
icon(brs,'history',{size:14,c:T.fg2,sw:2});
txt(brs,'恢复',{s:13,w:'m',c:T.fg2});
var sec2 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec2,'计划任务',{s:15,w:'b'});
between(sec2);
txt(sec2,'3 项启用',{s:11,c:T.fg3});
var sc = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'schedules'});
var jobs=[
 ['每日自动重启','0 4 * * *','下次 明天 04:00'],
 ['每小时快照','0 * * * *','下次 13:00'],
 ['周结算提醒','0 20 * * 5','周五 20:00']
];
for (var jn=0;jn<jobs.length;jn++){
 var jd=jobs[jn];
 if(jn>0){box(sc,{w:'fill',h:1,fill:T.border});}
 var it=box(sc,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:14,pr:12,pt:10,pb:10});
 var jcol=box(it,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(jcol,jd[0],{s:13,w:'m'});
 txt(jcol,jd[1]+' · '+jd[2],{s:11,c:T.fg3});
 var run=box(it,{w:30,h:30,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:15,name:'btn-run'});
 icon(run,'play',{size:13,c:T.fg,sw:2});
}
var sec3 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec3,'最近审计',{s:15,w:'b'});
between(sec3);
txt(sec3,'查看全部',{s:11,c:T.fg3});
var au = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'audit'});
var logs=[
 ['停止实例 · 小游戏 · 空岛','admin · 今天 12:31'],
 ['上传整合包 v1.4','admin · 昨天 21:07'],
 ['节点-02 完成注册','system · 昨天 18:40']
];
for (var ln=0;ln<logs.length;ln++){
 var lg=logs[ln];
 if(ln>0){box(au,{w:'fill',h:1,fill:T.border});}
 var arow=box(au,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:14,pr:12,pt:10,pb:10});
 box(arow,{w:7,h:7,r:4,fill:T.border});
 var acol=box(arow,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(acol,lg[0],{s:13,w:'m'});
 txt(acol,lg[1],{s:11,c:T.fg3});
}
return root;
`;

const serverList = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'MC 服务器',{s:20,w:'b'});
between(hr);
var rf=box(hr,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:17,stroke:T.border,sw:1,name:'btn-refresh'});
icon(rf,'refresh-cw',{size:16,c:T.fg2,sw:2});
txt(header,'3 台服务器 · 共 23 人在线',{s:12,c:T.fg3});
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var servers=[
 ['生存服 · 主世界','survival.yudream.online',true,'Paper 1.21.1','12 / 20',0.6,'20.0','第 3 周目 · 89 天'],
 ['创造服 · 建筑区','creative.yudream.online',true,'Paper 1.21.1','9 / 30',0.3,'19.9','长期开放'],
 ['小游戏 · 空岛','mini.yudream.online',false,'Spigot 1.20.4','0 / 16',0,'—','维护中']
];
for (var i=0;i<servers.length;i++){
 var s=servers[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:9,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'srv-'+s[0]});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 box(r1,{w:9,h:9,r:5,fill:s[2]?T.success:T.fg3});
 var col=box(r1,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(col,s[0],{s:15,w:'b'});
 txt(col,s[1],{s:11,c:T.fg3});
 icon(r1,'chevron-right',{size:16,c:T.fg3,sw:2});
 if(s[2]){
  var pr=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
  var track=box(pr,{w:225,h:5,r:3,fill:T.muted,name:'track'});
  var bar=box(track,{w:Math.round(225*s[5]),h:5,r:3,fill:T.success,name:'bar'});
  txt(pr,s[4],{s:11,w:'m',c:T.fg2});
 } else {
  badge(card,'离线 · 等待开启');
 }
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
 badge(r2,s[3]);
 if(s[2]){badge(r2,'TPS '+s[6]);}
 between(r2);
 txt(r2,s[7],{s:10,c:T.fg3});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'状态每 30 秒自动刷新 · 已归档服务器见详情',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const serverDetail = `
var root = root8();
statusBar(root);
navBar(root,'生存服 · 主世界','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var st = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'status'});
box(st,{w:10,h:10,r:5,fill:T.success});
var stc = box(st,{dir:'VERTICAL',gap:1,w:'fill'});
txt(stc,'在线 · 已持续 12 天',{s:15,w:'b'});
txt(stc,'Paper 1.21.1 · survival.yudream.online',{s:11,c:T.fg3});
var sec1 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec1,'在线玩家',{s:15,w:'b'});
between(sec1);
txt(sec1,'12 / 20',{s:12,w:'m',c:T.fg2});
var pl = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'players'});
var players=[['PlayerHQ','在线 3.2 小时'],['MoNo','在线 1.5 小时'],['Steve_CN','在线 48 分钟']];
for (var p=0;p<players.length;p++){
 var pd=players[p];
 if(p>0){box(pl,{w:'fill',h:1,fill:T.border});}
 var it=box(pl,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:9,pb:9});
 avatar(it,pd[0].charAt(0),{size:30});
 txt(it,pd[0],{s:13,w:'m'});
 between(it);
 txt(it,pd[1],{s:11,c:T.fg3});
}
txt(pl,'… 以及 9 人',{s:11,c:T.fg3,align:'CENTER'});
var sec2 = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec2,'TPS · 近 24 小时',{s:15,w:'b'});
between(sec2);
txt(sec2,'min 19.2 · avg 19.9',{s:11,c:T.fg3});
var chart = box(c,{w:'fill',dir:'VERTICAL',gap:10,p:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'chart'});
var plot = box(chart,{w:'fill',h:72,dir:'HORIZONTAL',gap:1,align:'MAX',name:'plot'});
var bars=[0.95,0.98,0.96,0.99,0.97,1.0,0.98,0.99,1.0,0.96,0.94,0.9,0.93,0.96,0.98,0.99,1.0,0.98,0.96,0.97];
for (var q=0;q<bars.length;q++){
 box(plot,{w:13,h:Math.round(66*bars[q]),r:2,fill:T.fg});
}
var info = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'info'});
var rows=[['周目','第 3 周目 · 已进行 89 天'],['线路','电信 / 联通 双线'],['世界地图','BlueMap 已公开 · 查看'],['已归档','2 个历史周目']];
for (var k=0;k<rows.length;k++){
 if(k>0){box(info,{w:'fill',h:1,fill:T.border});}
 var it2=box(info,{w:'fill',dir:'HORIZONTAL',pl:14,pr:14,pt:11,pb:11});
 var lw=box(it2,{w:100,dir:'HORIZONTAL'});txt(lw,rows[k][0],{s:13,c:T.fg3});
 between(it2);
 txt(it2,rows[k][1],{s:13,w:'m',c:k===2?T.primary:T.fg});
}
return root;
`;

const walletHome = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'钱包',{s:20,w:'b'});
between(hr);
icon(hr,'bell',{size:19,c:T.fg2,sw:2});
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var bal = box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:18,pr:18,pt:16,pb:16,fill:T.primary,r:16,name:'balance-card'});
txt(bal,'总余额',{s:12,c:T.primaryFg,op:0.7});
var br = box(bal,{w:'fill',dir:'HORIZONTAL',gap:4,align:'CENTER'});
txt(br,'¥ 128.50',{s:30,w:'b',c:T.primaryFg});
between(br);
icon(br,'eye',{size:17,c:T.primaryFg,sw:2});
var bsub = box(bal,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER'});
badge(bsub,'积分 2,480',{fill:'#333333',c:T.primaryFg});
badge(bsub,'本月充值 ¥98',{fill:'#333333',c:T.primaryFg});
var quick = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'quick'});
var qs=[
 ['credit-card','充值','#surface'],
 ['arrow-left-right','转账'],
 ['history','流水'],
 ['list-checks','明细']
];
for (var i=0;i<qs.length;i++){
 var qd=qs[i];
 var qb=box(quick,{w:'fill',dir:'VERTICAL',gap:6,align:'CENTER',pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'quick-'+qd[1]});
 var ic=box(qb,{w:38,h:38,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:19});
 icon(ic,qd[0],{size:18,c:T.fg,sw:2});
 txt(qb,qd[1],{s:12,w:'m'});
}
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'最近流水',{s:15,w:'b'});
between(sec);
txt(sec,'全部',{s:12,c:T.fg3});
var tx = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'transactions'});
var txs=[
 ['+¥30.00','充值 · 支付宝','今天 10:21',true],
 ['积分 -800','积分兑换 · 商城商品','昨天 21:44',false],
 ['积分 +120','在线时长结算 · 10 月 3 日','昨天 00:05',true],
 ['+¥50.00','转账 · 来自 MoNo','3 天前',true]
];
for (var t=0;t<txs.length;t++){
 var td=txs[t];
 if(t>0){box(tx,{w:'fill',h:1,fill:T.border});}
 var it=box(tx,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:11,pb:11});
 var tic=box(it,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:16});
 icon(tic,td[3]?'arrow-up-right':'arrow-down-left',{size:15,c:td[3]?T.success:T.fg2,sw:2});
 var tcol=box(it,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(tcol,td[1],{s:13,w:'m'});
 txt(tcol,td[2],{s:11,c:T.fg3});
 txt(it,td[0],{s:14,w:'b',c:td[3]?T.success:T.fg});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'积分来自在线时长结算与商城收支',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const walletRecharge = `
var root = root8();
statusBar(root);
navBar(root,'充值','history');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var opts = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'options'});
var rows=[
 [['¥6','送 60 积分',false],['¥30','送 300 积分',true],['¥68','送 680 积分',false]],
 [['¥128','送 1,400 积分',false],['¥328','送 3,800 积分',false],['¥648','送 8,000 积分',false]]
];
for (var r=0;r<rows.length;r++){
 var row=box(opts,{w:'fill',dir:'HORIZONTAL',gap:8,name:'opt-row-'+r});
 for (var i=0;i<rows[r].length;i++){
  var o=rows[r][i];
  var cell=box(row,{w:'fill',dir:'VERTICAL',gap:3,align:'CENTER',pt:12,pb:12,fill:o[2]?T.primary:T.surface,r:14,stroke:o[2]?T.primary:T.border,sw:1,name:'opt-'+o[0]});
  txt(cell,o[0],{s:17,w:'b',c:o[2]?T.primaryFg:T.fg});
  txt(cell,o[1],{s:10,c:o[2]?T.primaryFg:T.fg3,op:o[2]?0.8:1});
 }
}
var pay = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'pay-method'});
var pic=box(pay,{w:36,h:36,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:'#e6f7fd',r:10});
icon(pic,'credit-card',{size:18,c:'#1677ff',sw:2});
var pcol=box(pay,{dir:'VERTICAL',gap:1,w:'fill'});
txt(pcol,'支付宝',{s:14,w:'m'});
txt(pcol,'跳转支付宝完成付款',{s:11,c:T.fg3});
var sel=box(pay,{w:20,h:20,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:10});
icon(sel,'check',{size:12,c:T.primaryFg,sw:3});
var c0 = box(c,{w:1,h:2});
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'充值记录',{s:15,w:'b'});
between(sec);
txt(sec,'含支付宝订单',{s:11,c:T.fg3});
var rec = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'records'});
var recs=[
 ['¥30.00','成功 · 今天 10:21',true],
 ['¥68.00','成功 · 3 天前',true],
 ['¥6.00','已关闭 · 1 周前',false]
];
for (var k=0;k<recs.length;k++){
 var rd=recs[k];
 if(k>0){box(rec,{w:'fill',h:1,fill:T.border});}
 var it=box(rec,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:11,pb:11});
 var ric=box(it,{w:32,h:32,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:16});
 icon(ric,'banknote',{size:15,c:T.fg2,sw:2});
 var rcol=box(it,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(rcol,rd[0],{s:13,w:'b'});
 txt(rcol,rd[1],{s:11,c:rd[2]?T.fg3:T.destructive});
 if(rd[2]){badge(it,'到账');}
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:4});
btnPrimary(foot,'立即充值 ¥30');
return root;
`;

const shopPlaza = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'积分商城',{s:20,w:'b'});
between(hr);
var mb=box(hr,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:8,pr:8,pt:4,pb:4,fill:T.muted,r:999});
icon(mb,'coins',{size:12,c:T.fg2,sw:2});
txt(mb,'我的积分 2,480',{s:11,w:'m',c:T.fg2});
fieldInput(header,'搜索商品…',{trail:'search'});
var chips = box(header,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'外观');
chip(chips,'装备');
chip(chips,'消耗品');
chip(chips,'服务');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:10,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var grid = box(c,{w:'fill',dir:'VERTICAL',gap:10,name:'grid'});
var prows=[
 [[ASSETS.kingdom,'限定披风 · 星夜','3,200','MoNo 的小店','库存 8'],[ASSETS.pickaxe,'附魔钻石镐 II','1,500','生存物资站','库存 15']],
 [[ASSETS.shaders,'改名卡','800','官方服务','库存 42'],[ASSETS.castle,'主城传送券','300','官方服务','库存 99']]
];
for (var r=0;r<prows.length;r++){
 var row=box(grid,{w:'fill',dir:'HORIZONTAL',gap:10,name:'prow-'+r});
 for (var i=0;i<prows[r].length;i++){
  var pd=prows[r][i];
  var card=box(row,{w:'fill',dir:'VERTICAL',gap:8,p:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'prod-'+pd[1]});
  img(card,{w:'fill',h:92,hash:pd[0],r:10,clip:true,name:'prod-img'});
  txt(card,pd[1],{s:14,w:'b'});
  var pr=box(card,{w:'fill',dir:'HORIZONTAL',gap:4,align:'CENTER'});
  icon(pr,'coins',{size:13,c:T.fg2,sw:2});
  txt(pr,pd[2],{s:13,w:'b'});
  txt(pr,'积分',{s:10,c:T.fg3});
  between(pr);
  badge(pr,pd[4]);
  var sr=box(card,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
  avatar(sr,pd[3].charAt(0),{size:18});
  txt(sr,pd[3],{s:11,c:T.fg3});
 }
}
return root;
`;

const shopProduct = `
var root = root8();
statusBar(root);
navBar(root,'商品详情','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
img(c,{w:'fill',h:150,hash:ASSETS.kingdom,r:14,clip:true,name:'cover'});
var nr = box(c,{w:'fill',dir:'VERTICAL',gap:5});
var nrow = box(nr,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
txt(nrow,'限定披风 · 星夜',{s:18,w:'b'});
badge(nrow,'外观');
var prow = box(nr,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
icon(prow,'coins',{size:16,c:T.fg,sw:2});
txt(prow,'3,200',{s:20,w:'b'});
txt(prow,'积分 · 库存 8',{s:12,c:T.fg3});
var seller = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:10,pb:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'seller'});
avatar(seller,'M',{size:36});
var scol=box(seller,{dir:'VERTICAL',gap:1,w:'fill'});
txt(scol,'MoNo 的小店',{s:14,w:'m'});
txt(scol,'信用 4.9 · 已售 126',{s:11,c:T.fg3});
icon(seller,'chevron-right',{size:16,c:T.fg3,sw:2});
var info = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'info'});
var rows=[['商品类型','外观 · 限定'],['有效期','永久'],['核销方式','订单核销码 · 卖家当面核销'],['支持','7 天内未核销可取消']];
for (var k=0;k<rows.length;k++){
 if(k>0){box(info,{w:'fill',h:1,fill:T.border});}
 var it=box(info,{w:'fill',dir:'HORIZONTAL',pl:14,pr:14,pt:11,pb:11});
 var lw=box(it,{w:90,dir:'HORIZONTAL'});txt(lw,rows[k][0],{s:13,c:T.fg3});
 between(it);
 txt(it,rows[k][1],{s:13,w:'m',c:T.fg});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:4});
btnPrimary(foot,'3,200 积分购买');
return root;
`;

const shopOrders = `
var root = root8();
statusBar(root);
navBar(root,'我的订单','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var tabs = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,name:'tabs'});
chip(tabs,'买入 · 3',true);
chip(tabs,'卖出 · 1',false);
var ord=[
 [ASSETS.kingdom,'限定披风 · 星夜','3,200 积分','待核销','warning'],
 [ASSETS.pickaxe,'附魔钻石镐 II','1,500 积分','已完成','muted'],
 [ASSETS.castle,'主城传送券','300 积分','已完成','muted']
];
for (var i=0;i<ord.length;i++){
 var o=ord[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:12,pr:12,pt:10,pb:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'order-'+i});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 img(r1,{w:48,h:48,hash:o[0],r:10,clip:true,name:'order-img'});
 var col=box(r1,{dir:'VERTICAL',gap:2,w:'fill'});
 txt(col,o[1],{s:14,w:'b'});
 txt(col,'MoNo 的小店 · 今天 10:32 下单',{s:11,c:T.fg3});
 badge(r1,o[3],{c:o[4]==='warning'?T.warning:T.fg3});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 icon(r2,'coins',{size:13,c:T.fg2,sw:2});
 txt(r2,o[2],{s:13,w:'b'});
 between(r2);
 if(i===0){
  var vb=box(r2,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:10,pr:10,pt:5,pb:5,fill:T.primary,r:8,name:'btn-voucher'});
  icon(vb,'qr-code',{size:12,c:T.primaryFg,sw:2});
  txt(vb,'核销码',{s:11,w:'m',c:T.primaryFg});
  var cb=box(r2,{dir:'HORIZONTAL',pl:10,pr:10,pt:5,pb:5,fill:T.surface,r:8,stroke:T.border,sw:1,name:'btn-cancel'});
  txt(cb,'取消',{s:11,c:T.fg2});
 } else if(i===1){
  var okb=box(r2,{dir:'HORIZONTAL',pl:10,pr:10,pt:5,pb:5,fill:T.muted,r:8,name:'btn-done'});
  txt(okb,'已核销 · 交易完成',{s:11,c:T.fg2});
 }
}
var sell = box(c,{w:'fill',dir:'VERTICAL',gap:8,pl:14,pr:14,pt:12,pb:12,fill:T.muted,r:14,name:'sell-hint'});
var sr = box(sell,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
icon(sr,'shopping-bag',{size:15,c:T.fg2,sw:2});
txt(sr,'我卖出的 · 1 笔进行中',{s:13,w:'m',c:T.fg2});
between(sr);
icon(sr,'chevron-right',{size:14,c:T.fg3,sw:2});
txt(sell,'买家付款后生成核销码，当面核销或发货后确认。',{s:11,c:T.fg3,fillW:true,lh:17});
return root;
`;

const quizHome = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
txt(header,'题库练习',{s:20,w:'b'});
var stat = box(header,{w:'fill',dir:'HORIZONTAL',gap:10,name:'stats'});
var sts=[['已练习','128 题'],['正确率','86%'],['连续','5 天']];
for (var i=0;i<sts.length;i++){
 var sc=box(stat,{w:'fill',dir:'VERTICAL',gap:2,p:10,fill:T.surface,r:12,stroke:T.border,sw:1});
 txt(sc,sts[i][1],{s:16,w:'b'});
 txt(sc,sts[i][0],{s:11,c:T.fg3});
}
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var ent = box(c,{w:'fill',dir:'VERTICAL',gap:10,name:'entries'});
var e1 = box(ent,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.primary,r:14,name:'entry-draw'});
var e1i=box(e1,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:'#333333',r:12});
icon(e1i,'zap',{size:20,c:T.primaryFg,sw:2});
var e1c=box(e1,{dir:'VERTICAL',gap:1,w:'fill'});
txt(e1c,'随机抽题',{s:16,w:'b',c:T.primaryFg});
txt(e1c,'20 题 · 全部分类 · 自动判分',{s:11,c:T.primaryFg,op:0.75});
icon(e1,'chevron-right',{size:16,c:T.primaryFg,sw:2});
var e2 = box(ent,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'entry-papers'});
var e2i=box(e2,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:12});
icon(e2i,'file-text',{size:20,c:T.fg,sw:2});
var e2c=box(e2,{dir:'VERTICAL',gap:1,w:'fill'});
txt(e2c,'我的试卷',{s:15,w:'b'});
txt(e2c,'3 份待完成 · 最近：期中模拟卷',{s:11,c:T.fg3});
icon(e2,'chevron-right',{size:16,c:T.fg3,sw:2});
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'排行榜 · 本周',{s:15,w:'b'});
between(sec);
txt(sec,'完整榜单',{s:12,c:T.fg3});
var lb = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'leaderboard'});
var tops=[
 ['1','MoNo','1,024 分',true],
 ['2','PlayerHQ','986 分',false],
 ['3','SiberianHusky','941 分',true]
];
for (var t=0;t<tops.length;t++){
 var td=tops[t];
 if(t>0){box(lb,{w:'fill',h:1,fill:T.border});}
 var it=box(lb,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:10,pb:10});
 var rank=box(it,{w:24,h:24,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:td[3]?T.primary:T.muted,r:12});
 txt(rank,td[0],{s:12,w:'b',c:td[3]?T.primaryFg:T.fg2});
 avatar(it,td[1].charAt(0),{size:30});
 txt(it,td[1],{s:13,w:td[3]?'b':'m'});
 between(it);
 icon(it,'trophy',{size:13,c:T.fg3,sw:2});
 txt(it,td[2],{s:12,w:'m',c:T.fg2});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'支持随机抽题、组卷练习与自评分',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const quizSession = `
var root = root8();
statusBar(root);
navBar(root,'随机练习 · 3 / 20','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var pbar = box(c,{w:'fill',h:5,r:3,fill:T.muted,name:'progress'});
box(pbar,{w:Math.round(320*0.15),h:5,r:3,fill:T.primary,name:'progress-fill'});
var q = box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:16,pr:16,pt:14,pb:14,fill:T.surface,r:14,stroke:T.border,sw:1,name:'question'});
var qr = box(q,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
badge(qr,'常识');
between(qr);
txt(qr,'答对 12 / 15',{s:11,c:T.success});
txt(q,'地球上最早的生命形式出现于距今约多少年前？',{s:16,w:'b',fillW:true,lh:24});
var opts = box(q,{w:'fill',dir:'VERTICAL',gap:8,name:'options'});
var os=['A. 38 亿年','B. 10 亿年','C. 5,500 万年','D. 200 万年'];
for (var i=0;i<os.length;i++){
 var on=i===0;
 var ob=box(opts,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:12,pr:12,pt:11,pb:11,fill:on?T.muted:T.bg,r:10,stroke:on?T.primary:T.border,sw:1,name:'opt-'+i});
 var lb=box(ob,{w:22,h:22,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:on?T.primary:T.surface,r:11,stroke:on?T.primary:T.border,sw:1});
 txt(lb,os[i].charAt(0),{s:11,w:'b',c:on?T.primaryFg:T.fg3});
 txt(ob,os[i].slice(3),{s:14,w:on?'m':'r',c:on?T.fg:T.fg2});
}
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'答题卡',{s:15,w:'b'});
between(sec);
txt(sec,'已完成 3',{s:11,c:T.fg3});
var sheet = box(c,{w:'fill',dir:'VERTICAL',gap:6,p:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'sheet'});
var states=[2,2,1,0,0,0,0,0,0,0];
for (var r=0;r<2;r++){
 var row=box(sheet,{w:'fill',dir:'HORIZONTAL',gap:5,name:'sheet-row-'+r});
 for (var n=0;n<10;n++){
  var idx=r*10+n;
  var cur=idx===2;
  var dot=box(row,{w:'fill',h:26,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',r:7,fill:cur?T.primary:(idx<3?T.muted:T.bg),stroke:idx<3?T.border:T.input,sw:1,name:'dot-'+idx});
  txt(dot,String(idx+1),{s:10,w:cur?'b':'r',c:cur?T.primaryFg:T.fg3});
 }
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',dir:'HORIZONTAL',gap:10,align:'CENTER',pb:4,name:'actions'});
var prev=box(foot,{w:'fill',h:46,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:12,stroke:T.border,sw:1,name:'btn-prev'});
txt(prev,'上一题',{s:14,w:'m',c:T.fg2});
var next=box(foot,{w:'fill',h:46,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.primary,r:12,name:'btn-next'});
txt(next,'下一题',{s:14,w:'m',c:T.primaryFg});
return root;
`;

const activitySquare = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'活动广场',{s:20,w:'b'});
between(hr);
var mb=box(hr,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:8,pr:8,pt:4,pb:4,fill:T.muted,r:999,name:'chip-mine'});
icon(mb,'user',{size:12,c:T.fg2,sw:2});
txt(mb,'我的活动',{s:11,w:'m',c:T.fg2});
txt(header,'报名活动、完成答题、领取参与证明',{s:12,c:T.fg3});
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var acts=[
 [ASSETS.castle,'中秋建筑大赛','10.1 – 10.8 · 主世界 · 城区',32,50,true],
 [ASSETS.kingdom,'周末生存挑战','10.14 · 生存服',18,40,true],
 [ASSETS.pickaxe,'春季跑酷赛','5.1 – 5.3 · 小游戏',50,50,false]
];
for (var i=0;i<acts.length;i++){
 var a=acts[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:9,p:10,fill:T.surface,r:14,stroke:T.border,sw:1,name:'act-'+i});
 var ir=box(card,{w:'fill',dir:'HORIZONTAL',gap:10});
 img(ir,{w:74,h:64,hash:a[0],r:10,clip:true,name:'act-img'});
 var col=box(ir,{dir:'VERTICAL',gap:4,w:'fill'});
 var tr=box(col,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
 txt(tr,a[1],{s:14,w:'b'});
 badge(tr,a[5]?'报名中':'已结束',{c:a[5]?T.success:T.fg3});
 var mr=box(col,{dir:'HORIZONTAL',gap:4,align:'CENTER'});
 icon(mr,'calendar',{size:12,c:T.fg3,sw:2});
 txt(mr,a[2],{s:10,c:T.fg3});
 var pr2=box(col,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
 var track=box(pr2,{w:250,h:4,r:2,fill:T.muted,name:'track'});
 var bar=box(track,{w:Math.round(250*a[3]/a[4]),h:4,r:2,fill:a[5]?T.success:T.fg3,name:'bar'});
 txt(pr2,a[3]+' / '+a[4],{s:10,w:'m',c:T.fg2});
 var ar=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 badge(ar,'在线答题');
 badge(ar,'盖章证明');
 between(ar);
 if(a[5]){
  var jb=box(ar,{dir:'HORIZONTAL',pl:12,pr:12,pt:6,pb:6,fill:T.primary,r:9,name:'btn-join'});
  txt(jb,'立即报名',{s:11,w:'m',c:T.primaryFg});
 } else {
  var db=box(ar,{dir:'HORIZONTAL',pl:12,pr:12,pt:6,pb:6,fill:T.muted,r:9,name:'btn-ended'});
  txt(db,'查看回顾',{s:11,c:T.fg2});
 }
}
return root;
`;

const activityDetail = `
var root = root8();
statusBar(root);
navBar(root,'中秋建筑大赛','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
img(c,{w:'fill',h:120,hash:ASSETS.castle,r:14,clip:true,name:'banner'});
var nr = box(c,{w:'fill',dir:'VERTICAL',gap:5});
var nrow = box(nr,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
txt(nrow,'中秋建筑大赛',{s:18,w:'b'});
badge(nrow,'报名中',{c:T.success});
var mrow = box(nr,{w:'fill',dir:'HORIZONTAL',gap:12,align:'CENTER'});
var m1=box(mrow,{dir:'HORIZONTAL',gap:4,align:'CENTER'});
icon(m1,'calendar',{size:13,c:T.fg3,sw:2});
txt(m1,'10.1 – 10.8',{s:12,c:T.fg2});
var m2=box(mrow,{dir:'HORIZONTAL',gap:4,align:'CENTER'});
icon(m2,'map-pin',{size:13,c:T.fg3,sw:2});
txt(m2,'主世界 · 城区',{s:12,c:T.fg2});
var join = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'join'});
var jcol=box(join,{dir:'VERTICAL',gap:2,w:'fill'});
txt(jcol,'已报名 32 / 50',{s:14,w:'b'});
txt(jcol,'报名截止 10.8 23:59',{s:11,c:T.fg3});
var jb=box(join,{dir:'HORIZONTAL',pl:16,pr:16,pt:9,pb:9,fill:T.primary,r:10,name:'btn-join'});
txt(jb,'立即报名',{s:13,w:'m',c:T.primaryFg});
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'活动任务',{s:15,w:'b'});
between(sec);
txt(sec,'完成可领证明',{s:11,c:T.fg3});
var tasks = box(c,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'tasks'});
var tks=[
 ['file-check','在线答题','10 题 · 通过线 60 分 · 限 3 次'],
 ['file-text','参与证明','活动结束后可下载盖章 PDF']
];
for (var t=0;t<tks.length;t++){
 var td=tks[t];
 if(t>0){box(tasks,{w:'fill',h:1,fill:T.border});}
 var it=box(tasks,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:12,pt:11,pb:11});
 var tic=box(it,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:10});
 icon(tic,td[0],{size:16,c:T.fg,sw:2});
 var tcol=box(it,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(tcol,td[1],{s:13,w:'m'});
 txt(tcol,td[2],{s:11,c:T.fg3});
 icon(it,'chevron-right',{size:15,c:T.fg3,sw:2});
}
txt(c,'以「团圆」为主题在城区指定地块建造建筑，评委按创意与完成度打分；答题与出勤计入参与证明。',{s:13,c:T.fg2,fillW:true,lh:21});
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',pb:2});
txt(foot,'报名后可在「我的活动」管理参与状态',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const progressMine = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'我的任务',{s:20,w:'b'});
between(hr);
var mb=box(hr,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:8,pr:8,pt:4,pb:4,fill:T.muted,r:999,name:'chip-stats'});
icon(mb,'activity',{size:12,c:T.fg2,sw:2});
txt(mb,'本周打卡 3 / 5',{s:11,w:'m',c:T.fg2});
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var ci = box(c,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER',pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'checkin'});
var cic=box(ci,{w:40,h:40,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.muted,r:12});
icon(cic,'calendar',{size:19,c:T.fg,sw:2});
var ccol=box(ci,{dir:'VERTICAL',gap:1,w:'fill'});
txt(ccol,'今日打卡',{s:14,w:'b'});
txt(ccol,'连续 5 天 · 项目「基建周目 · 3 期」',{s:11,c:T.fg3});
var cb=box(ci,{dir:'HORIZONTAL',pl:14,pr:14,pt:8,pb:8,fill:T.primary,r:10,name:'btn-checkin'});
txt(cb,'打卡',{s:13,w:'m',c:T.primaryFg});
var sec = box(c,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(sec,'进行中 · 3',{s:15,w:'b'});
between(sec);
txt(sec,'待验收 1',{s:11,c:T.warning});
var tasks=[
 ['搭建主城车站','基建周目 · 3 期','剩余 2 天',0.6,true],
 ['铺设南环地铁隧道','基建周目 · 3 期','剩余 5 天',0.25,false],
 ['村庄防御塔修复','市政翻新','已延期 1 天',0.8,false]
];
for (var i=0;i<tasks.length;i++){
 var t=tasks[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:8,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'task-'+i});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 txt(r1,t[0],{s:14,w:'b'});
 between(r1);
 badge(r1,t[2],{c:t[2].indexOf('延')>-1?T.destructive:T.fg3});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:6,align:'CENTER'});
 badge(r2,t[1]);
 between(r2);
 txt(r2,Math.round(t[3]*100)+'%',{s:11,w:'m',c:T.fg2});
 var track=box(card,{w:'fill',h:5,r:3,fill:T.muted,name:'track'});
 var bar=box(track,{w:Math.round(292*t[3]),h:5,r:3,fill:T.primary,name:'bar'});
 var r3=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,justify:'MAX'});
 if(t[4]){
  var sb=box(r3,{dir:'HORIZONTAL',pl:12,pr:12,pt:6,pb:6,fill:T.primary,r:9,name:'btn-submit'});
  txt(sb,'提交验收',{s:11,w:'m',c:T.primaryFg});
 }
 var ub=box(r3,{dir:'HORIZONTAL',pl:12,pr:12,pt:6,pb:6,fill:T.surface,r:9,stroke:T.border,sw:1,name:'btn-upload-ev'});
 icon(ub,'upload',{size:11,c:T.fg2,sw:2});
 txt(ub,'上传成果',{s:11,c:T.fg2});
}
return root;
`;

const progressAdmin = `
var root = root8();
statusBar(root);
navBar(root,'验收审批','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var hd = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
badge(hd,'待验收 2',{c:T.warning});
txt(hd,'提交后 48 小时内需处理',{s:11,c:T.fg3});
var subs=[
 ['PlayerHQ','主城车站 · 站体与站台','今天 09:12',true],
 ['MoNo','南环地铁隧道 · 第一段','昨天 21:40',false]
];
for (var i=0;i<subs.length;i++){
 var s=subs[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'sub-'+i});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 avatar(r1,s[0].charAt(0),{size:34});
 var col=box(r1,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(col,s[0],{s:14,w:'b'});
 txt(col,s[1],{s:12,c:T.fg2});
 between(r1);
 txt(r1,s[2],{s:10,c:T.fg3});
 if(s[3]){
  var imgs = box(card,{w:'fill',dir:'HORIZONTAL',gap:8,name:'evidence'});
  img(imgs,{w:'fill',h:72,hash:ASSETS.castle,r:10,clip:true,name:'ev-1'});
  img(imgs,{w:'fill',h:72,hash:ASSETS.kingdom,r:10,clip:true,name:'ev-2'});
 }
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 badge(r2,'成果 2 份');
 badge(r2,'打卡 12 次');
 between(r2);
 var okb=box(r2,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:12,pr:12,pt:6,pb:6,fill:T.success,r:9,name:'btn-accept'});
 icon(okb,'check',{size:12,c:'#ffffff',sw:2.6});
 txt(okb,'通过',{s:11,w:'m',c:'#ffffff'});
 var no=box(r2,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:12,pr:12,pt:6,pb:6,fill:T.surface,r:9,stroke:T.destructive,sw:1,name:'btn-reject'});
 icon(no,'x',{size:12,c:T.destructive,sw:2.6});
 txt(no,'驳回',{s:11,w:'m',c:T.destructive});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'驳回需填写原因，将通知提交者',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const verifyAdmin = `
var root = root8();
statusBar(root);
navBar(root,'学历认证审批','ellipsis');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var hd = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
badge(hd,'待审核 2',{c:T.warning});
txt(hd,'人工通道 · 邮箱与学信网自动通过',{s:11,c:T.fg3});
var reqs=[
 ['林晚','高校学历认证 · 本科','人工审核 · 今天 08:55',true],
 ['陈屿','高校学历认证 · 硕士','人工审核 · 昨天 19:22',false]
];
for (var i=0;i<reqs.length;i++){
 var q=reqs[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:10,pl:14,pr:14,pt:12,pb:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'req-'+i});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 avatar(r1,q[0].charAt(0),{size:34});
 var col=box(r1,{dir:'VERTICAL',gap:1,w:'fill'});
 txt(col,q[0],{s:14,w:'b'});
 txt(col,q[1],{s:12,c:T.fg2});
 between(r1);
 badge(r1,'人工',{c:T.warning});
 var mrow = box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 var doc=box(mrow,{w:'fill',h:64,dir:'HORIZONTAL',gap:8,align:'CENTER',justify:'CENTER',fill:T.muted,r:10,name:'material-doc'});
 icon(doc,'file-text',{size:18,c:T.fg2,sw:2});
 var dcol=box(doc,{dir:'VERTICAL',gap:1,align:'CENTER'});
 txt(dcol,'毕业证书.jpg',{s:11,w:'m',c:T.fg2});
 txt(dcol,'点击查看',{s:10,c:T.fg3});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 txt(r2,'提交于 '+q[2],{s:11,c:T.fg3});
 between(r2);
 var okb=box(r2,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:12,pr:12,pt:6,pb:6,fill:T.success,r:9,name:'btn-approve'});
 icon(okb,'check',{size:12,c:'#ffffff',sw:2.6});
 txt(okb,'通过',{s:11,w:'m',c:'#ffffff'});
 var no=box(r2,{dir:'HORIZONTAL',gap:4,align:'CENTER',pl:12,pr:12,pt:6,pb:6,fill:T.surface,r:9,stroke:T.destructive,sw:1,name:'btn-reject'});
 icon(no,'x',{size:12,c:T.destructive,sw:2.6});
 txt(no,'驳回',{s:11,w:'m',c:T.destructive});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'操作将写入审计日志并通知申请人',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const mcNews = `
var root = root8();
statusBar(root);
var header = box(root,{w:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:8,pb:4,name:'header'});
var hr = box(header,{w:'fill',dir:'HORIZONTAL',align:'CENTER'});
txt(hr,'新闻动态',{s:20,w:'b'});
between(hr);
var sb=box(hr,{w:34,h:34,dir:'HORIZONTAL',justify:'CENTER',align:'CENTER',fill:T.surface,r:17,stroke:T.border,sw:1,name:'btn-subscribe'});
icon(sb,'bell',{size:16,c:T.fg2,sw:2});
fieldInput(header,'搜索新闻…',{trail:'search'});
var chips = box(header,{w:'fill',dir:'HORIZONTAL',gap:8,name:'chips'});
chip(chips,'全部',true);
chip(chips,'官网更新');
chip(chips,'快照');
chip(chips,'活动');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var news=[
 [ASSETS.shaders,'官网更新','Minecraft 1.21.2「共振之裂」现已发布','新方块、新生物与试炼密室改版同步上线 · minecraft.net','2 小时前'],
 [ASSETS.kingdom,'活动','中秋双周活动：月满建筑祭','限时活动与登录奖励已开启 · 官方活动页','昨天 18:00'],
 [null,'快照','25w41a 快照：大量性能与红石修复','可从启动器安装快照版本体验 · feedback.minecraft.net','3 天前']
];
for (var i=0;i<news.length;i++){
 var n=news[i];
 var card=box(c,{w:'fill',dir:'VERTICAL',gap:8,p:12,fill:T.surface,r:14,stroke:T.border,sw:1,name:'news-'+i});
 var r1=box(card,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
 badge(r1,n[1]);
 between(r1);
 txt(r1,n[4],{s:11,c:T.fg3});
 txt(card,n[2],{s:15,w:'b',fillW:true,lh:21});
 var r2=box(card,{w:'fill',dir:'HORIZONTAL',gap:10,align:'CENTER'});
 if(n[0]){
  img(r2,{w:76,h:56,hash:n[0],r:8,clip:true,name:'news-img'});
 }
 txt(r2,n[3],{s:12,c:T.fg2,fillW:true,lh:17});
}
var foot = box(c,{w:'fill',h:'fill',dir:'VERTICAL',justify:'MAX',align:'CENTER',pb:4});
txt(foot,'聚合自 MC 官网与社区源 · 可在订阅中配置推送',{s:11,c:T.fg3,align:'CENTER'});
return root;
`;

const mcNewsDetail = `
var root = root8();
statusBar(root);
navBar(root,'新闻详情','share');
var c = box(root,{w:'fill',h:'fill',dir:'VERTICAL',gap:12,pl:20,pr:20,pt:4,pb:14,name:'content',clip:true});
var nr = box(c,{w:'fill',dir:'VERTICAL',gap:6});
var r1 = box(nr,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER'});
badge(r1,'官网更新');
txt(r1,'2 小时前 · minecraft.net',{s:11,c:T.fg3});
txt(nr,'Minecraft 1.21.2「共振之裂」现已发布',{s:19,w:'b',fillW:true,lh:26});
img(c,{w:'fill',h:130,hash:ASSETS.shaders,r:12,clip:true,name:'cover'});
txt(c,'本次更新带来了下界与主世界的新共振方块族群，试炼密室加入全新变种房间，并为红石玩家重新调整了侦测器的时序行为。',{s:14,fillW:true,lh:23,c:T.fg});
txt(c,'Java 版与基岩版将陆续推送；第三方服务端可继续使用 1.21.1，插件兼容性不受影响。完整更新日志见官网。',{s:14,fillW:true,lh:23,c:T.fg});
var link = box(c,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:14,pr:14,pt:11,pb:11,fill:T.surface,r:12,stroke:T.border,sw:1,name:'source-link'});
icon(link,'globe',{size:16,c:T.fg2,sw:2});
var lcol=box(link,{dir:'VERTICAL',gap:1,w:'fill'});
txt(lcol,'阅读原文',{s:13,w:'m'});
txt(lcol,'minecraft.net',{s:11,c:T.fg3});
icon(link,'chevron-right',{size:15,c:T.fg3,sw:2});
var rel = box(c,{w:'fill',dir:'VERTICAL',gap:8,name:'related'});
txt(rel,'相关推荐',{s:15,w:'b'});
var rl = box(rel,{w:'fill',dir:'VERTICAL',gap:0,fill:T.surface,r:14,stroke:T.border,sw:1,name:'related-list'});
var rels=['25w40a 快照：试炼密室新变体','基岩版 1.21.2 修复清单','1.21.2 服务端兼容性说明'];
for (var i=0;i<rels.length;i++){
 if(i>0){box(rl,{w:'fill',h:1,fill:T.border});}
 var it=box(rl,{w:'fill',dir:'HORIZONTAL',gap:8,align:'CENTER',pl:14,pr:12,pt:11,pb:11});
 txt(it,rels[i],{s:13,w:'m',c:T.fg,fillW:true});
 icon(it,'chevron-right',{size:14,c:T.fg3,sw:2});
}
return root;
`;

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
  wikiHome: [LIGHT, P2, 840, 0, wikiHome],
  wikiItem: [LIGHT, P2, 1260, 0, wikiItem],
  skinHome: [LIGHT, P2, 1680, 0, skinHome],
  skinCloset: [LIGHT, P2, 0, 900, skinCloset],
  panelOverview: [LIGHT, P2, 420, 900, panelOverview],
  panelInstance: [LIGHT, P2, 840, 900, panelInstance],
  panelOps: [LIGHT, P2, 1260, 900, panelOps],
  serverList: [LIGHT, P2, 1680, 900, serverList],
  serverDetail: [LIGHT, P2, 0, 1800, serverDetail],
  walletHome: [LIGHT, P2, 420, 1800, walletHome],
  walletRecharge: [LIGHT, P2, 840, 1800, walletRecharge],
  shopPlaza: [LIGHT, P2, 1260, 1800, shopPlaza],
  shopProduct: [LIGHT, P2, 1680, 1800, shopProduct],
  shopOrders: [LIGHT, P2, 0, 2700, shopOrders],
  quizHome: [LIGHT, P2, 420, 2700, quizHome],
  quizSession: [LIGHT, P2, 840, 2700, quizSession],
  activitySquare: [LIGHT, P2, 1260, 2700, activitySquare],
  activityDetail: [LIGHT, P2, 1680, 2700, activityDetail],
  progressMine: [LIGHT, P2, 0, 3600, progressMine],
  progressAdmin: [LIGHT, P2, 420, 3600, progressAdmin],
  verifyAdmin: [LIGHT, P2, 840, 3600, verifyAdmin],
  mcNews: [LIGHT, P2, 1260, 3600, mcNews],
  mcNewsDetail: [LIGHT, P2, 1680, 3600, mcNewsDetail],
  homeDark: [DARK, P3, 0, 0, darkHome],
  profileDark: [DARK, P3, 420, 0, darkProfile],
  tokens: [LIGHT, P4, 0, 0, tokensSheet],
  components: [LIGHT, P4, 420, 0, componentsSheet],
};

export function buildByName(name) {
  const def = DEFS[name];
  if (!def) throw new Error('未知屏幕: ' + name + '（可选: ' + Object.keys(DEFS).join(', ') + '）');
  const [tokens, page, x, y, body] = def;
  return buildScreen(name, tokens, page, x, y, EXTRA + '\n' + body, IMAGE_HASHES);
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
