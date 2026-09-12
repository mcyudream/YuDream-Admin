# 落地案例

YuDream Admin 可以作为成品部署：打开 CMS 与公开站点、装上 SITE 主题和官方业务插件、在后台填写栏目与文案，就能上线对外官网，不必分叉宿主源码，也不必为单个站点写定制后端。

下面两所高校 Minecraft 社团官网是同一套宿主、同一套主题能力的生产部署，属于**非二次开发**案例。导航、Hero、配图和模块开关按社团自行配置，形成「MC 社团一站式官网」。

## 西南科技大学 Minecraft 星空社

[https://www.swustmc.cn/site](https://www.swustmc.cn/site)

公开站覆盖首页、复原工程、服务器、活动平台、百科、大事记、插件市场与知识库。首页由主题接管导航与版式，社团介绍与长期项目在 CMS 区块中维护。

[![西南科技大学 Minecraft 星空社公开站首页](/showcase/swustmc.jpg)](https://www.swustmc.cn/site)

## 塔里木大学胡杨方块社

[https://hall.mc.taru.xj.cn/site](https://hall.mc.taru.xj.cn/site)

同一套主题与站点能力的另一份部署。导航收敛为服务器、活动平台、百科、大事记与知识库，首页文案与配图按社团自行替换，用于招新与服务器状态公示。

[![塔里木大学胡杨方块社公开站首页](/showcase/taru-mc.jpg)](https://hall.mc.taru.xj.cn/site)

## 这类站点用到什么

| 能力 | 作用 |
| --- | --- |
| CMS / 公开站 | 页面、首页布局、SEO 与访客路由 `/site` |
| SITE 主题 | `homeComponent` / `chromeComponent` 接管大厅与导航；内容按 `themeCode` 隔离 |
| 官方业务插件 | 服务器列表、活动平台、百科、大事记、插件市场、知识库等按需开关 |
| 插件市场源 | 本机源由 `plugin-market-source` 提供；远程源订阅不依赖该能力 |

需要更深的业务差异时，再走独立插件，而不是改宿主。模式选择见 [两种使用方式](/guide/usage-modes)。
