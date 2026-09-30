# 插件移动端页面设计计划（页面「02 插件页面」）

> 2026-09-28 制定。依据：对 `yudream-admin-plugins` 31 个插件的端点/权限/页面实地调查
> （Explore 报告），每屏必须对应插件真实存在的端点或页面，**不发明功能**。

## 设计原则（本次修正的出发点）

1. **严格对照插件真实功能面**——此前皮肤画成"热门皮肤市场"、百科画成"建筑词条"、
   下载站无对应插件，全部推倒按真实端点重画。
2. **管理员运维优先**——面板（mcpanel）按"手机上快速完成运维动作"设计：
   状态一眼可见、电源操作直达、日志/命令随手可查、审批一键通过/驳回。
3. **宿主 FaTheme0 设计语言**——复用 `lib.mjs` token 与 helper（状态栏/导航/卡片/
   徽章/chip/输入框/主按钮），插件页只做内容差异，不引入新风格。
4. 信息流/首页卡片由宿主「01 宿主 App」承载（mobile.home.feed / homeCards 契约），
   本页只设计插件自身的二级页面。

## 插件盘点结论（31 个）

| 分档 | 插件 | 结论 |
| --- | --- | --- |
| P0 用户侧 | wallet、shop、forum、minecraft-server、mc-wiki、questionbank、minecraft-activity-proof、yudream-skin、project-progress | 各出移动端页面 |
| P1 管理运维 | mcpanel（重点）、project-progress 管理端、edu-verify、minecraft-activity-proof 管理端 | 出运维/审批页 |
| P2 可选 | mc-news、material、timeline、world-map、playtime-points（并入钱包展示） | 本轮先做 mc-news，其余待定 |
| 不做 | ai-chatbot、qqbot-automation、web-card、codex-task-notify、authlib-injector、launcher-adapter、ymcl-adapter、ymcl-content、neco-pixel、student-info、mc-pet、mcguess、pony、wordle、qq-binding | 玩法在 QQ 群 / 桌面启动器协议 / 纯后台复杂表单 / Web 主题，无手机可见面 |

- alipay 充值单并入钱包充值页；playtime-points 明细并入钱包积分维度，不单独出页面。
- forum 已有 2 屏（帖子列表/详情）符合定位，保留微调。

## 页面「02 插件页面」屏幕清单（25 屏，360×800，栅格 420）

| # | 屏名 | 插件 | 内容（对应真实端点/页面） | 状态 |
| --- | --- | --- | --- | --- |
| 0 | forumList | forum | 帖子列表：分类 chip + 信息流卡片（/public/posts） | 保留 |
| 1 | forumDetail | forum | 帖子详情：正文/评论/点赞收藏（/public/posts/{id}、comments） | 保留 |
| 2 | wikiHome | mc-wiki | 物品图鉴：搜索 + 分类 chip + 物品宫格（/public/items、/public/meta） | 重画 |
| 3 | wikiItem | mc-wiki | 物品详情：图标/ID/命名空间/stack、获取方式、合成配方 3×3（/public/items/{id}、/public/recipes） | 重画（原 wikiArticle） |
| 4 | skinHome | yudream-skin | 我的角色：角色列表（默认标记/改名/删除）、设默认、上传材质（/me/players、/me/default-player、/me/textures） | 重画 |
| 5 | skinCloset | yudream-skin | 材质库与衣柜：我的材质列表（上传/删除/应用）、衣柜皮肤（/me/textures、/me/closet） | 重画（原 skinDetail） |
| 6 | panelOverview | mcpanel | 运维总览：节点健康、实例电源速操作、异常实例置顶（/admin/overview、/admin/nodes、/admin/instances） | 重画（原 panelList） |
| 7 | panelInstance | mcpanel | 实例运维：电源四键（start/stop/restart/kill）、控制台命令输入+日志流、在线玩家（power、command、output、players） | 重画（原 panelConsole） |
| 8 | panelOps | mcpanel | 运维切片：备份（trigger/restore）、计划任务（schedules+run）、审计入口（audit） | 重画（原 monitor） |
| 9 | serverList | minecraft-server | 服务器列表：状态/版本/在线玩家卡（/servers） | 新增 |
| 10 | serverDetail | minecraft-server | 服务器详情：状态历史、在线玩家、周目信息（/servers/{id}、status/history、players） | 新增 |
| 11 | walletHome | wallet(+playtime-points) | 余额+积分卡、四宫格入口、最近流水（/me/balances、/me/transactions、/me/summary） | 新增 |
| 12 | walletRecharge | wallet(+alipay) | 充值：档位选择、支付宝支付、充值记录（/me/recharge/options、/me/recharges、/me/orders） | 新增 |
| 13 | shopPlaza | shop | 积分商城广场：商品网格、积分价（/plaza/products） | 新增 |
| 14 | shopProduct | shop | 商品详情：积分价、购买、卖家、库存（商品详情+下单） | 新增 |
| 15 | shopOrders | shop | 我的订单：买入/卖出 tab、状态、核销码 voucher、取消/确认（/me/orders、/me/purchases、/me/sales） | 新增 |
| 16 | quizHome | questionbank | 练习：随机抽题/试卷入口、练习统计、排行榜（/me/practice/sessions、/draw、/me/quiz/leaderboard） | 新增 |
| 17 | quizSession | questionbank | 作答：题目卡（单选/判断）、答题卡、交卷（sessions 作答/submit） | 新增 |
| 18 | activitySquare | minecraft-activity-proof | 活动广场：活动卡（时间/名额/状态）（/me/activities，Square 页） | 新增 |
| 19 | activityDetail | minecraft-activity-proof | 活动详情：报名/取消、在线答题入口、参与证明盖章 PDF（join/cancel/quiz/attempt、exports stamped-pdf） | 新增 |
| 20 | progressMine | project-progress | 我的任务：待领/进行中、打卡、提交验收、统计（/me/tasks、/me/check-ins、/me/statistics） | 新增 |
| 21 | progressAdmin | project-progress | 验收审批：待验收列表、成果预览、通过/驳回（acceptance pending） | 新增 |
| 22 | verifyAdmin | edu-verify | 学历认证审批：待审列表、材料查看、通过/驳回（/admin/verifications/{id}/approve\|reject） | 新增 |
| 23 | mcNews | mc-news | 新闻列表：来源聚合、时间、订阅入口（NewsListPage、/me/subscription） | 新增（替换 downloadList） |
| 24 | mcNewsDetail | mc-news | 新闻阅读：正文、原文链接（/public/… 详情） | 新增（替换 downloadDetail） |

**删除**：downloadList、downloadDetail（无任何插件提供"下载站"功能；整合包/启动器分发
属桌面启动器协议 ymcl-*，无手机面）。

## 执行顺序

1. mcpanel 三屏（运维重点）→ 2. mc-wiki 两屏 → 3. yudream-skin 两屏 →
4. minecraft-server 两屏 → 5. wallet 两屏 → 6. shop 三屏 → 7. questionbank 两屏 →
8. activity 两屏 → 9. progress 两屏 + verifyAdmin → 10. mc-news 两屏 →
11. 更新 README 屏幕清单、暗色页与设计规范页补充插件组件样例（时间允许）。

每屏 `node screens.mjs <name>` 构建 + 截图目检后进入下一屏；全部完成后整页截图复核。
