# GitLab 私有 SPI / SDK 发布与消费

这份文档聚焦“GitLab 私有包路径”本身。
当前默认事实是：

- Maven SPI 继续通过 GitLab Package Registry 发布
- `@yudream/plugin-sdk` 与 `@yudream/components` 的默认公网消费路径已经切到 npmjs
- 如果你仍需要内网 npm 源，核心仓也保留了 GitLab npm 发布任务作为可选路径

GitLab 私有路径下当前支持的制品包括：

- Maven: `online.yudream.base:yudream-plugin-spi:1.0-SNAPSHOT`
- npm: `@yudream/plugin-sdk`
- npm: `@yudream/components`

## 1. 当前仓库内的发布配置

已配置的发布入口：

- Maven 发布任务：`publish:maven-plugin-spi`
- Maven 回读校验任务：`verify:maven-plugin-spi`
- 契约包完整性校验任务：`validate:contract-packages`
- npm 发布任务：`publish:npm-plugin-sdk`
- npm 发布任务：`publish:npm-components`

触发方式：

- GitLab tag 流水线，tag 规则与当前主流水线保持一致：`^v`

发布认证：

- Maven 使用 GitLab 内置 `CI_JOB_TOKEN`
- npm 使用 GitLab 内置 `CI_JOB_TOKEN`

Maven 发布地址约束：

- `yudream-plugin-spi` 发布必须使用项目级 endpoint，且 `project_id` 必须是数值 ID
- 当前核心仓 GitLab Maven endpoint 为 `https://gitlab.yudream.online/api/v4/projects/12/packages/maven`
- 不要把发布地址写成 `projects/yudream%2Fyudreamadmin/packages/maven` 这种 path-encoded 形式；该形式可在部分读取场景中工作，但不应作为正式 publish 配置

说明：

- `yudream-plugin-spi` 已改为独立 POM，不再继承根 `pom.xml`
- `yudream-plugin-spi/pom.xml` 已内置 `distributionManagement` / `snapshotRepository`，由核心仓 tag 流水线直接执行 `mvn deploy`
- 核心仓已新增 `ci/verify-plugin-spi-registry.sh`，tag 流水线会在发布后用空白 Maven 本地仓重新解析一次 SPI
- 核心仓已新增 `ci/verify-contract-packages.sh`，用于校验 npm 契约包发布所需的关键入口文件和默认 registry
- `@yudream/plugin-sdk` 已具备独立版本、独立校验脚本和 GitLab npm 发布配置
- `@yudream/components` 已具备独立版本、独立校验脚本和 GitLab npm 发布配置

## 2. 插件仓消费 Maven SPI

插件仓不再依赖核心仓根 parent，而是直接依赖正式版本：

```xml
<dependency>
    <groupId>online.yudream.base</groupId>
    <artifactId>yudream-plugin-spi</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

插件仓 `settings.xml` 示例：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>gitlab-maven</id>
            <username>${env.CORE_PACKAGE_USER}</username>
            <password>${env.CORE_PACKAGE_TOKEN}</password>
        </server>
    </servers>

    <profiles>
        <profile>
            <id>gitlab-private</id>
            <repositories>
                <repository>
                    <id>gitlab-maven</id>
                    <url>https://gitlab.example.com/api/v4/projects/CORE_PROJECT_ID/packages/maven</url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>gitlab-maven</id>
                    <url>https://gitlab.example.com/api/v4/projects/CORE_PROJECT_ID/packages/maven</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>gitlab-private</activeProfile>
    </activeProfiles>
</settings>
```

推荐在 GitLab 中为核心仓或上级 group 创建只读 Deploy Token，并在插件仓配置：

- `CORE_PACKAGE_USER`
- `CORE_PACKAGE_TOKEN`

如果你已经在核心仓开启了 Job Token allowlist，允许 `yudream-admin-plugins` 读取核心仓包，也可以不单独发 Deploy Token：

- Maven 用户名保持 `gitlab-ci-token`
- Maven / npm Token 直接使用插件仓流水线内置 `CI_JOB_TOKEN`

当前状态：

- 核心仓 `yudream/yudreamadmin` 已经把 `yudream/yudream-admin-plugins` 加入 CI Job Token allowlist
- 插件仓可以在不配置额外 `CORE_PACKAGE_TOKEN` 的情况下，直接回退使用本仓 `CI_JOB_TOKEN` 读取核心 Maven 包

## 3. 插件仓消费 npm SDK / UI 组件包

默认推荐直接消费 npmjs 公网包，具体见：

- [npmjs 公网包发布说明](npmjs-public-packages.md)

如果你明确需要 GitLab 私有 npm 源，可使用下面这套可选配置：

插件仓 `package.json`：

```json
{
  "dependencies": {
    "@yudream/plugin-sdk": "1.0.1-snapshot",
    "@yudream/components": "1.0.0-snapshot"
  }
}
```

插件仓 `.npmrc` 示例：

```ini
@yudream:registry=https://gitlab.example.com/api/v4/projects/CORE_PROJECT_ID/packages/npm/
//gitlab.example.com/api/v4/projects/CORE_PROJECT_ID/packages/npm/:_authToken=${NPM_TOKEN}
always-auth=true
```

推荐在插件仓 GitLab CI 中配置：

- `CORE_NPM_REGISTRY`
- `CORE_PACKAGE_TOKEN`

可直接使用核心仓或上级 group 的 Deploy Token / Project Access Token，只要有 `read_package_registry` 权限即可；如果使用 Job Token allowlist，则 `CORE_PACKAGE_TOKEN` 可以留空，流水线会回退到 `CI_JOB_TOKEN`。

## 4. 版本建议

为了让拆仓后的消费更稳定，建议这样管理版本：

1. `yudream-plugin-spi` 继续使用 Maven 语义版本，例如 `1.0-SNAPSHOT`、`1.0.0`
2. `@yudream/plugin-sdk` 使用 npm 语义版本，例如 `1.0.1-snapshot`、`1.0.0`
3. `@yudream/components` 使用 npm 语义版本，例如 `1.0.0-snapshot`、`1.0.0`
4. 每次发布 tag 前，先显式更新对应版本号，再触发 GitLab tag 流水线

## 5. 当前验证结果

截至当前状态，发布与消费链路已经完成一次真实验证：

1. `yudream-plugin-spi` 已通过项目级 GitLab Maven endpoint `https://gitlab.yudream.online/api/v4/projects/12/packages/maven` 发布成功
2. 使用空白临时 Maven 本地仓，可以重新拉取 `online.yudream.base:yudream-plugin-spi:1.0-SNAPSHOT`
3. 插件仓使用空白临时 Maven 本地仓和 GitLab settings 后，可以完成整仓 `mvn package -DskipTests`

## 6. 后续拆仓建议

当业务插件迁到独立仓库后：

1. 删除插件仓对核心仓 root parent 的依赖
2. Maven 只依赖 `yudream-plugin-spi`
3. 前端通过正式版本依赖 `@yudream/plugin-sdk`
4. 需要复用主框架组件时，通过正式版本依赖 `@yudream/components`
