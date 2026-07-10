# GitLab 私有包路径迁移说明（已废弃）

本文保留旧文件名仅用于兼容历史链接。GitLab Package Registry 的 Maven/npm 发布和拉取路径已经废弃，不得继续配置或作为 Nexus 的回退源。

GitLab 当前只承担：

- 源码托管
- CI 流水线执行
- CI artifacts 暂存

## 当前统一 Nexus 路径

| 用途 | 仓库 | 地址 |
| --- | --- | --- |
| YuDream Maven 制品拉取 | `maven-public` | `https://nexus.yudream.online/repository/maven-public/` |
| 第三方 Maven 依赖与插件 | 阿里云公共仓库 | `https://maven.aliyun.com/repository/public` |
| Maven release 发布 | `maven-releases` | `https://nexus.yudream.online/repository/maven-releases/` |
| Maven snapshot 发布 | `maven-snapshots` | `https://nexus.yudream.online/repository/maven-snapshots/` |
| `@yudream` npm 发布和拉取 | `npm-public` | `https://nexus.yudream.online/repository/npm-public/` |

发布写入统一使用以下受保护、掩码的 CI 变量；读取使用 Nexus 匿名访问：

- `NEXUS_USERNAME`
- `NEXUS_PASSWORD`

## Maven 消费示例

插件仓只依赖正式发布的 SPI 版本：

```xml
<dependency>
    <groupId>online.yudream.base</groupId>
    <artifactId>yudream-plugin-spi</artifactId>
    <version>1.0.1</version>
</dependency>
```

`settings.xml` 将阿里云放在前面，Nexus 作为 YuDream 制品与缺失依赖的后备仓库：

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
    <profiles>
        <profile>
            <id>yudream-repositories</id>
            <repositories>
                <repository>
                    <id>aliyun-public</id>
                    <url>https://maven.aliyun.com/repository/public</url>
                </repository>
                <repository>
                    <id>nexus-public</id>
                    <url>https://nexus.yudream.online/repository/maven-public/</url>
                </repository>
            </repositories>
        </profile>
    </profiles>
    <activeProfiles>
        <activeProfile>yudream-repositories</activeProfile>
    </activeProfiles>
</settings>
```

发布时由 SPI POM 的 `distributionManagement` 根据版本类型选择 `maven-releases` 或 `maven-snapshots`，不要向 `maven-public` 执行 Maven deploy。

## npm 消费和发布示例

```bash
printf '%s\n' \
  'registry=https://registry.npmmirror.com/' \
  '@yudream:registry=https://nexus.yudream.online/repository/npm-public/' > .npmrc
```

`@yudream/plugin-sdk` 与 `@yudream/components` 的 `publishConfig.registry` 和安装 registry 必须保持为同一个 Nexus `npm-public` 地址。

## 历史配置处置

以下配置只属于迁移前历史，不得继续使用：

- GitLab 项目级 Maven endpoint
- GitLab 项目级 npm endpoint
- `CI_JOB_TOKEN` / Deploy Token 的 Package Registry 认证
- Job Token package allowlist
- npmjs 发布 token 和 npmjs 发布开关

已有 GitLab/npmjs 包可作为历史版本留存，但任何新的 YuDream 制品只能发布到 Nexus，所有构建也只能从 Nexus 拉取 YuDream 制品。第三方 npm 依赖继续使用 npmjs 或 npmmirror。
