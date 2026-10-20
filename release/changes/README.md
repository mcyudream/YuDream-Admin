# release/changes —— 宿主发版变更记录

每笔影响宿主行为的已提交变更（Java/Vue/pom 依赖等）都要有一条记录；仅文档（`docs/`、`**/*.md`）、
CI（`ci/`、`.gitlab-ci.yml`）与发版辅助（`release/`）的提交自动按 `none` 处理，无需记录。
记录文件在对应代码提交之后单独提交（需要引用提交哈希）。逐条 schema 由 `ci/host-release.mjs` 强校验。

## 文件名

`YYYY-MM-DD-<slug>.json`（日期前缀保证聚合顺序稳定）。

## Schema

```json
{
  "id": "site-studio-basics",
  "level": "minor",
  "title": "站点工作室基础能力",
  "commits": ["1a2b3c4"],
  "notes": ["新增站点工作室可视化编辑与页面构建能力"]
}
```

| 字段 | 要求 |
|---|---|
| `id` | 非空字符串，记录唯一标识 |
| `level` | `major` / `minor` / `patch` / `none`；聚合取最高档位决定升版（`none` 仍按 patch 兜底出正式版） |
| `title` | 非空中文标题，进入 release notes |
| `commits` | 非空数组，本记录覆盖的提交哈希（7-40 位前缀均可）；哈希必须真实存在 |
| `notes` | 非空字符串数组，中文发布说明条目，进入 release notes |

## 命令速查

```bash
node ci/host-release.mjs check-changes                 # 基线..HEAD 覆盖与定级检查
node ci/host-release.mjs prepare                       # dry-run：算下一版本 + notes 预览
node ci/host-release.mjs prepare --write               # 落盘：notes/状态/pom 升版（提交后打 tag）
node ci/host-release.mjs prepare --rc --write          # 候选版：只升 pom 正式段，rc 序号自动递增
node ci/host-release.mjs validate --tag v1.5.0         # 发布全量校验（CI validate 作业）
node ci/host-release.mjs build-version --tag v1.5.0    # 输出精确版本；--dotenv 写 HOST_BUILD_VERSION
```

规则要点：tag 与根 pom `yudream.host.version` 必须一致；已打 tag 的版本输入冻结不可改
（重试按冻结说明重算比对）；rc 必须递增、正式转化必须包含最新 rc；发布时 tracked 源码必须干净
（未跟踪文件与构建产物忽略，本地演练可 `--allow-dirty`）。
