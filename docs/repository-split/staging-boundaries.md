# 拆仓提交边界

这份文档不是功能说明，而是给当前工作树收口用的。

目标只有两个：

1. 把“主体仓继续剥离插件”的改动单独收成提交
2. 尽量不要把当前工作树里的无关改动一起卷进去

## 主体仓建议分组

### 1. 合同包 / CI / 模板 / 说明文档

这一组适合先单独提交，因为它们决定了拆仓后的规则与验收方式。

建议重点检查并分组：

- `.gitlab-ci.yml`
- `ci/`
- `README.md`
- `docs/plugin-system/`
- `docs/repository-split/`
- `templates/plugin-repo/`
- `yudream-plugins/yudream-plugin-spi/pom.xml`
- `yudream-frontend/packages/plugin-sdk/`
- `yudream-frontend/packages/components/package.json`

典型命令：

```powershell
git add .gitlab-ci.yml
git add ci
git add README.md
git add docs/plugin-system docs/repository-split
git add templates/plugin-repo
git add yudream-plugins/yudream-plugin-spi/pom.xml
git add yudream-frontend/packages/plugin-sdk
git add yudream-frontend/packages/components/package.json
```

### 2. 主体前端宿主与 workspace 收口

这一组主要是“主体仓不再直接吃业务插件源码”的宿主侧清理。

建议重点检查并分组：

- `yudream-frontend/pnpm-workspace.yaml`
- `yudream-frontend/pnpm-lock.yaml`
- `yudream-frontend/apps/core-arco-design-vue/`
- `yudream-frontend/packages/plugin-*/` 的删除
- `yudream-plugins/yudream-plugin-*/` 的删除

这一组要特别注意人工复查，因为 `apps/core-arco-design-vue` 当前工作树里可能混有别的前端调整。

## 不建议顺手带上的内容

下面这些路径如果不是这次拆仓必需，建议单独处理：

- `.idea/`
- `.codex/skills/`
- `skills/`
- `docs/plans/`
- 与拆仓无关的业务页面样式或交互调整

## 提交前建议

先跑主体仓一键审计：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/verify-core-split-readiness.sh
```

只看拆仓相关改动时，可以先缩小 `git status` 视野：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/show-core-split-status.sh
```

再用 path 限定复查暂存内容：

```powershell
git diff --cached --stat
git diff --cached
```

## 辅助脚本

如果你想先只暂存“契约 / CI / 模板 / 文档”这一组，可以直接用：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/stage-core-contract-split.sh --dry-run
& 'C:/Program Files/Git/bin/sh.exe' ci/stage-core-contract-split.sh
```

如果你想再单独暂存“宿主清理 / 迁移删除”这一组，可以用：

```powershell
& 'C:/Program Files/Git/bin/sh.exe' ci/stage-core-host-cleanup.sh --dry-run
& 'C:/Program Files/Git/bin/sh.exe' ci/stage-core-host-cleanup.sh
```
