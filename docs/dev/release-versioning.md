# 宿主版本与发布版本规范

> **适用范围**：主仓宿主（平台整体）版本号、发布 tag、镜像发布与双仓发布校验。
> **目标**：一份规范说清宿主版本如何定级、如何记录、如何发布与校验；契约包（SPI/plugin-sdk/components）发布流程仍按 [contract-release.md](contract-release.md) 执行。

本文件是宿主版本与发布 tag 规则的**唯一权威**。插件 SemVer 细则见插件仓 `docs/dev/conventions.md`，与本文件冲突时以本文件为准。

## 1. SemVer 定级

宿主版本号采用 SemVer `MAJOR.MINOR.PATCH`，按本次发布包含的最高变更级别定级：

| 变更类型 | 定级 | 示例 |
|---|---|---|
| 缺陷修复、安全修复、稳定化 | PATCH | `1.4.10` → `1.4.11` |
| 向后兼容的新功能、新端点、新配置 | MINOR | `1.4.11` → `1.5.0` |
| 不兼容的 API/数据/配置/权限/行为变化 | MAJOR | `1.5.0` → `2.0.0` |

- **最高级别聚合**：一次发布包含多类变更时，**必须**按其中最高级别定级，不得拆小。

  - ✅ 同时含 bug 修复与新功能 → 升 MINOR。
  - ❌ 同时含新功能与破坏性变更 → 只升 MINOR（正确是 MAJOR）。
- **纯文档 none**：仅文档、注释、格式化且不改任何产物行为的变更**禁止**升版、**禁止**单独发版。

## 2. 唯一版本源与 Maven 坐标

- 根 `pom.xml` 新增属性 `yudream.host.version`，是宿主版本的**唯一**读取来源；发版只改这一处，**禁止**从记忆或其他文件推断版本。
- **Maven 坐标不动**：宿主各模块 Maven 坐标（groupId/artifactId 与既有项目 version）保持现状，`yudream.host.version` 只承载发布版本语义，不改变构建坐标。
- **契约独立**：`yudream.plugin.spi.version` 与 SDK/components 版本由 [contract-release.md](contract-release.md) 管理，与宿主版本互不锁步；宿主发版**不必**连带升契约，契约发布**不必**连带发宿主。
- 版本源落地与发布工具实现由对应实现任务执行；本文只定规则。

## 3. tag 与发布通道

| tag 形态 | 性质 | 行为 |
|---|---|---|
| `v<SemVer>` | 正式发布 | 构建镜像、更新 `latest`、发正式通道 |
| `v<SemVer>-rc.N` | 候选发布 | 独立版本、独立验证记录；**不更新** `latest` |
| `d<...>` | 部署/测试标签 | 构建镜像供部署测试；**不得**更新 `latest`、**不得**进入正式发布物通道 |

- **RC 独立**：RC 是独立版本，编号从 1 递增（`v1.5.0-rc.1`、`v1.5.0-rc.2`，禁止 `rc.0` 与前导零），有自己的 tag、源码快照与验证记录；RC 转正式**必须**另发 `v<SemVer>` 最终 tag，不得把 RC tag 改名或复用。
- **d 不得 latest**：`latest` 只随 `v*` 正式标签更新；`d*` 部署标签推送镜像时**禁止**附带 `latest`。`latest` 语义等价「最新正式版」，与部署环境用什么版本无关。
- **稳定不倒退**：已发布的稳定版本**禁止**被更低版本覆盖或回退；`latest` **必须**始终指向不低于当前最新正式版的构建。

## 4. tag 不可变与后缀重发（historical）

1. **已 tag 不可移动**：任何已推送的发布 tag（含失败 tag）**禁止**删除、重打或改指；发布事实必须可审计。
2. **源码变化需新版本**：tag 发布失败或验证后源码又有变化，**必须**按第 1 节定级发新版本，**禁止**用 `-1`、`-2` 等后缀重打新 tag。
3. **后缀重发仅历史有效**：`v1.4.9-1` 等带后缀 tag 是历史补救手段，其发布产物在 Nexus/镜像源中继续有效、记录保留，但**禁止**再以任何理由新建后缀 tag。
4. 发布失败时的处理顺序：保留失败 tag 与记录 → 排查修复（源码变化则提交修复）→ 按新版本重新发版。

## 5. 变更记录与发布门禁

- 每次宿主发布**必须**先记录变更（面向用户的中文变更条目），再触发发布；无变更记录的版本**禁止**发布。
- 发布工具已落地：

  ```bash
  node ci/host-release.mjs check-changes                 # 基线..HEAD 覆盖与定级检查
  node ci/host-release.mjs prepare                       # dry-run：算下一版本 + notes 预览
  node ci/host-release.mjs prepare --write               # 落盘：notes/状态/pom 升版（提交后打 tag）
  node ci/host-release.mjs prepare --rc --write          # 候选版：只升 pom 正式段，rc 序号自动递增（从 1 起）
  node ci/host-release.mjs validate --tag v1.5.0         # 发布全量校验（CI 作业 validate:host-release）
  node ci/host-release.mjs build-version --tag v1.5.0    # 输出精确版本；--dotenv 写 HOST_BUILD_VERSION
  sh ci/publish-host-images.sh                           # 镜像发布（双仓/通道/防倒退细则见脚本头注释）
  ```

- 测试：`node ci/host-release.test.mjs`（31 例）、`node --test ci/test/publish-host-images.test.mjs`（14 例，docker 由 `ci/test/mock-docker.mjs` 模拟离线运行）。
- CI 接线：`validate:host-release-history`（默认分支/MR/d*）、`validate:host-release`（v*）、`publish:host-images`（v*/d* 镜像发布）；契约发布/验签作业对 `-rc.*` 标签一律跳过。

- 门禁**必须**校验：版本号来自 `yudream.host.version` 源文件、tag 与版本一致、变更记录存在且与定级匹配。
- 工具尚未落地前，上述命令仅为约定接口；**禁止**在工具落地前宣称已完成对应校验。

## 6. 双仓校验与汇报

- 宿主发版涉及插件仓消费时，主仓发布验证通过后才同步插件仓；汇报**必须**包含精确版本号（**禁止**写「最新」「大概」或凭记忆报版本）。
- 发布汇报**必须**注明：精确版本、tag 名、发布/验证流水线结果、插件仓是否已同步验证；未验证的事项如实标注「未验证」，**禁止**把计划中的校验描述成已通过。
- 正式版本只允许指向受保护远端（Nexus 正式仓、Harbor 正式项目）；测试部署产物走测试通道。远端保护配置（不可覆盖策略、受保护 tag 等）由部署配置承担，本规范只约束「什么产物允许进哪个通道」，不重复定义具体配置项。

## 7. 自检清单

1. 定级是否取本次变更的最高级别？纯文档是否未升版？
2. 版本是否只读自 `yudream.host.version`？Maven 坐标与契约版本是否未被误动？
3. `d*` 是否未更新 `latest`？RC 是否独立发版？
4. 是否存在移动已发布 tag 或新建后缀 tag 的行为？
5. 变更记录是否先于发布完成并通过门禁？
6. 汇报是否给出精确版本与真实验证状态？
