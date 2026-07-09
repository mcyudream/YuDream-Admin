# 仓库拆分现状

当前仓库已经按 `core` 方向完成了第一步整理：

1. 根 `pom.xml` 只保留核心模块和 `yudream-plugin-spi`
2. 核心 GitLab CI 不再构建业务插件前端和业务插件 JAR
3. 运行时默认只扫描 `plugins/` 目录中的外部插件 JAR
4. `yudream-plugin-spi` 和 `@yudream/plugin-sdk` 已具备单独发布到 GitLab 私有包仓库的能力

## 当前仓库的定位

当前仓库应视为：

- 核心后端仓
- 核心前端仓
- 插件运行时仓
- 插件契约发布仓

也就是说，这个仓库未来建议重命名为：

```text
yudream-admin-core
```

## 仍留在当前仓库中的插件源码

`yudream-plugins/` 下除 `yudream-plugin-spi` 外的业务插件源码目前仍在仓库里，主要用于迁移缓冲和历史参考。

这些业务插件源码已经不再是：

- 根 Maven reactor 的正式成员
- 核心 GitLab CI 的构建对象
- 核心运行时默认本地 target 扫描目录

换句话说，它们还“放在这里”，但这个仓库已经不再把它们当成 core 的一部分。

## 推荐创建的仓库

### 必需

1. `yudream-admin-plugins`

用途：

- 承载官方业务插件后端模块
- 承载官方插件前端包
- 消费核心仓发布的 `yudream-plugin-spi` 和 `@yudream/plugin-sdk`
- 单独产出插件 JAR

建议首批迁入：

- `yudream-sample-plugin`
- `yudream-plugin-wallet`
- `yudream-plugin-alipay`
- `yudream-plugin-yudream-skin`
- `yudream-plugin-authlib-injector`
- `yudream-plugin-minecraft-server`
- `yudream-plugin-minecraft-activity-proof`
- `yudream-plugin-student-info`
- `yudream-plugin-project-progress`

### 建议尽快补齐

2. `yudream-admin-plugin-ui` 或者继续在核心仓发布 npm 包 `@fantastic-admin/components`

原因：

当前各插件前端除了依赖 `@yudream/plugin-sdk`，还依赖 `@fantastic-admin/components`。如果不把这层 UI 依赖发布出来，插件前端仓即使拆出去也还不能独立安装构建。

二选一即可：

- 路线 A：继续以 npm 私有包形式从核心仓发布 `@fantastic-admin/components`
- 路线 B：抽出更窄的插件 UI 包，单独建仓，例如 `yudream-admin-plugin-ui`

### 可选

3. `yudream-admin-plugin-examples`

如果你希望把示例插件和官方生产插件分开维护，可以把 `yudream-sample-plugin` 独立放到示例仓；如果没这个需求，直接放在 `yudream-admin-plugins` 就够了。

## 当前推荐的最终仓库布局

最简可落地版本：

```text
yudream-admin-core
yudream-admin-plugins
```

更完整的长期版本：

```text
yudream-admin-core
yudream-admin-plugins
yudream-admin-plugin-ui
```

## 迁移顺序建议

1. 保持当前仓库作为 `core`
2. 新建 `yudream-admin-plugins`
3. 用模板目录 `templates/plugin-repo/` 初始化插件仓
4. 先迁移一个插件验证链路，推荐 `yudream-plugin-authlib-injector` 或 `yudream-plugin-wallet`
5. 再批量迁移其余业务插件
6. 最后决定 UI 依赖是继续发布还是单独拆仓
