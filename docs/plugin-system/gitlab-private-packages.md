# GitLab 私有 SPI / SDK 发布与消费

本文约定当前核心仓库通过 GitLab Package Registry 发布两类私有制品：

- Maven: `online.yudream.base:yudream-plugin-spi:1.0-SNAPSHOT`
- npm: `@yudream/plugin-sdk`

## 1. 当前仓库内的发布配置

已配置的发布入口：

- Maven 发布任务：`publish:maven-plugin-spi`
- npm 发布任务：`publish:npm-plugin-sdk`

触发方式：

- GitLab tag 流水线，tag 规则与当前主流水线保持一致：`^v`

发布认证：

- Maven 使用 GitLab 内置 `CI_JOB_TOKEN`
- npm 使用 GitLab 内置 `CI_JOB_TOKEN`

说明：

- `yudream-plugin-spi` 已改为独立 POM，不再继承根 `pom.xml`
- `@yudream/plugin-sdk` 已具备独立版本、独立校验脚本和 GitLab npm 发布配置

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
            <username>${env.CI_DEPLOY_USER}</username>
            <password>${env.CI_DEPLOY_PASSWORD}</password>
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
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>gitlab-private</activeProfile>
    </activeProfiles>
</settings>
```

推荐在 GitLab 中为核心仓或上级 group 创建只读 Deploy Token，并在插件仓配置：

- `CI_DEPLOY_USER`
- `CI_DEPLOY_PASSWORD`

## 3. 插件仓消费 npm SDK

插件仓 `package.json`：

```json
{
  "dependencies": {
    "@yudream/plugin-sdk": "1.0.0-snapshot"
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

- `NPM_TOKEN`

可直接使用核心仓或上级 group 的 Deploy Token / Project Access Token，只要有 `read_package_registry` 权限即可。

## 4. 版本建议

为了让拆仓后的消费更稳定，建议这样管理版本：

1. `yudream-plugin-spi` 继续使用 Maven 语义版本，例如 `1.0-SNAPSHOT`、`1.0.0`
2. `@yudream/plugin-sdk` 使用 npm 语义版本，例如 `1.0.0-snapshot`、`1.0.0`
3. 每次发布 tag 前，先显式更新对应版本号，再触发 GitLab tag 流水线

## 5. 后续拆仓建议

当业务插件迁到独立仓库后：

1. 删除插件仓对核心仓 root parent 的依赖
2. Maven 只依赖 `yudream-plugin-spi`
3. 前端只依赖 `@yudream/plugin-sdk`
4. UI 侧如仍需复用主框架组件，再单独发布 `@fantastic-admin/components` 或更窄的 `@yudream/plugin-ui`
