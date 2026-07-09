# YuDream Admin Core

这是 YuDream Admin 的核心仓整理版，职责聚焦在：

- 核心后端模块
- 核心前端模块
- 插件运行时
- 插件契约发布

业务插件未来应逐步迁移到独立插件仓，不再和核心仓共用同一条构建与发布链路。

## 当前目录结构

```text
yudream-domain/                     核心领域层
yudream-application/                核心应用层
yudream-infrastructure/             核心基础设施层与插件运行时
yudream-interfaces/                 核心接口层
yudream-bootstrap/                  核心启动模块
yudream-plugins/yudream-plugin-spi/ 插件 SPI 契约模块
yudream-frontend/                   核心前端与插件 SDK
plugins/                            运行时外部插件 JAR 目录
ci/                                 核心 CI 配置
docs/repository-split/              拆仓说明
templates/plugin-repo/              独立插件仓模板
```

说明：

- `yudream-plugins/` 下其他业务插件源码目前仍留在工作区中作为迁移缓冲，但已不再属于根 Maven reactor
- 核心 GitLab CI 也不再负责构建业务插件前端与业务插件 JAR

## 核心构建

后端：

```powershell
mvn -pl yudream-bootstrap -am -DskipTests compile
```

前端：

```powershell
cd yudream-frontend
pnpm install
pnpm --filter @fantastic-admin/core-arco-design-vue build
```

## 私有包发布

当前核心仓负责发布两个给独立插件仓消费的私有包：

- Maven: `online.yudream.base:yudream-plugin-spi`
- npm: `@yudream/plugin-sdk`

具体见：

- [GitLab 私有包发布说明](/D:/code/yudream-admim/docs/plugin-system/gitlab-private-packages.md)

## 插件运行时

核心运行时默认只扫描：

```text
plugins/
```

独立插件仓构建出的 JAR 应复制或挂载到该目录，再由核心系统加载。

具体见：

- [外部插件目录说明](/D:/code/yudream-admim/plugins/README.md)

## 拆仓说明

当前推荐的仓库拆分方式见：

- [拆仓说明](/D:/code/yudream-admim/docs/repository-split/README.md)

如果你要马上新建插件仓，可直接参考：

- [插件仓模板说明](/D:/code/yudream-admim/templates/plugin-repo/README.md)
- [插件仓 CI 模板](/D:/code/yudream-admim/templates/plugin-repo/.gitlab-ci.yml.example)
- [插件仓 Maven 设置模板](/D:/code/yudream-admim/templates/plugin-repo/settings.xml.example)
- [插件仓 npm 设置模板](/D:/code/yudream-admim/templates/plugin-repo/.npmrc.example)
