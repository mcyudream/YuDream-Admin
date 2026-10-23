# 发布经验：契约包、Nexus 与流水线

> **适用范围**：SPI、plugin-sdk、components 发布与下游插件仓同步。
> **目标**：记录版本/注册表/流水线失误的诊断点，执行流程以 `docs/dev/contract-release.md` 为准。

## 1. 契约版本与下游

### 插件仓使用了本地能编译但 Nexus 不存在的契约版本

- **现象**：主仓 reactor 编译通过，插件仓独立构建失败或运行时缺契约。
- **根因**：SPI 只在本地 Maven reactor/install 可见，尚未发布到 Nexus；`-pl` 单模块构建也可能没带 reactor 依赖。
- **规避**：主仓校验带 `-am`；插件仓只升级已发布且验签通过的版本；contract-release 的顺序不能提前下游同步。
- **来源**：`yudream-plugin-spi` Maven reactor 约束；`docs/dev/contract-release.md`。

### npm 契约验证卡住或凭据错误

- **现象**：验签长时间等待 npmjs，或 Nexus 登录 401。
- **根因**：错误地直连公共 npm registry，或 CI 变量对 `$` 展开。
- **规避**：`@yudream` 只走 Nexus `npm-public`；验签脚本走 Nexus 不走 npmjs；CI 凭据中如 `robot$push` 必须以 raw 变量创建，禁止被 `$VAR` 默认展开。
- **来源**：`contract verify registry + idempotent publish`、`GitLab CI variable raw expansion`；`docs/dev/contract-release.md`。

### 失败 tag 重推仍无法发布

- **现象**：同一 tag 对应的发布流程失败，修复后不能再次触发或 evidence 校验异常。
- **根因**：Git tag 不可移动，CI 对 tag 的发布事实需要可审计；一次性授权令牌也可能已过期。
- **规避**：失败 tag 不移动；历史上按带后缀 tag 重跑（historical，已沉淀进规范）；现规则为源码变化**必须**发新版本，**禁止**再新建后缀 tag；确认 `authorize-main` 等短时授权仍有效。
- **来源**：`plugin-ci-release-tags`、`CI tag-only pipeline and push guard`；`docs/dev/release-versioning.md` 第 4 节。

## 2. GitLab CI 行为

### Bash heredoc 中反斜杠转义在工具通道被折叠

- **现象**：Python heredoc 正则写 `\\u...` 后报 `re.PatternError`，或字符串字面量触发 unicodeescape SyntaxError。
- **根因**：ZCode Bash 命令通道会把命令字符串里的双反斜杠折为单反斜杠，heredoc 内容也受影响。
- **规避**：构造含反斜杠文本用 `chr(92)` 拼接，命令串中不要出现字面量反斜杠加 `u`；优先使用专用文件编辑工具。
- **来源**：`bash-heredoc-backslash-fold`。

### 推分支没有流水线

- **现象**：master push 后看不到 CI run（或反之看到一条极轻量流水线）。
- **根因**：workflow 规则控制哪些事件建流水线；任务建立后默认分支/MR 会跑一条只含 `validate:host-release-history` 的轻量流水线，其余作业仍按各自的 tag 规则触发。
- **规避**：不要把轻量流水线误判为全量构建在跑；作业级 rules 决定各作业是否执行；本地按仓库验证清单执行等价检查。
- **来源**：`ci-workflow-rules-vs-job-rules`；`docs/dev/release-versioning.md` 第 5 节。

### POM 注释里写了 --write 导致整棵 Maven 树解析失败

- **现象**：`mvn` 直接报 `Non-parseable POM: in comment after two dashes`，并连带 reactor 全模块构建失败。
- **根因**：XML 注释内部不允许出现 `--`（如把 CLI 用法 `prepare --write` 写进 pom 注释），这是 XML 语法硬约束。
- **规避**：pom/任何 XML 的注释中禁止出现连续两个连字符；描述 CLI 用法时写「prepare 落盘」「D 参数覆盖」等无 `--` 的表述。
- **来源**：`xml-comment-double-dash`；`pom.xml` / `yudream-bootstrap/pom.xml`（宿主版本注释）。

### 发布产物版本和源码属性不一致

- **现象**：根 pom/模块 pom 看似更新，但插件源码仍编译旧 SPI，或 pipeline 发布了错误版本。
- **根因**：漏改 SPI 模块版本与主仓 `yudream.plugin.spi.version` 双点之一，或版本来自陈旧记忆。
- **规避**：每次从真实源文件读取；SPI 两处同步；tag 规则与实际变更包匹配；等待 verify 通过后再同步插件仓。
- **来源**：`docs/dev/contract-release.md`。

## 3. 仓库历史与发版基线

### 历史重写后发版窗口断裂与 tag 迁移

- **现象**：filter-repo 清理历史后，已发布 tag 留在旧线，`check-changes` 的窗口 `v1.5.1..HEAD` 变成全部重写提交（上千笔“未覆盖”）；手改 `release/host-release.json` 的 baseline.commit 指到重写提交会触发 `E-BASELINE-INTEGRITY`（工具强制校验 baseline.commit 与 tag 实际指向一致）。
- **根因**：发版工具的增量窗口锚定 tag 名解析提交；重写产生互不相交的 DAG，旧 tag 与新 master 无共同祖先。
- **规避**：重写必须连 tag 一起迁移——用 `filter-repo/commit-map` 把每个 tag 重建到内容等价的新提交（annotated tag 保留原 message），强推后窗口自动恢复；重写前先 `git bundle create --all` 留审计备份，映射表与 bundle 成对保存。镜像仓（GitHub）被历史大文件（>100MB）拦截时，只有剔除该 blob 的重写历史能推上去。
- **来源**：`history-purge-tag-migration-20261023`、`gh001-large-file-mirror-block`。

### 历史垃圾与误提交凭据

- **现象**：`tmp/`（含 257MB 运行时 jar、日志、浏览器 Profile）、`.pixso-replica/`、`.idea/ApifoxUploaderProjectSetting.xml`（真实 Apifox 令牌）、`.pixso-replica/husky-password-backup.txt`（明文口令）等曾被跟踪提交。
- **根因**：忽略规则缺失（tmp/ 曾被跟踪导致整体无法简单忽略）+ 调试产物随手入库。
- **规避**：filter-repo 以 `--invert-paths` 清除后，`.gitignore` 补 `tmp/`、`.pixso-replica/`、`.idea/`、`*.log`、`node_modules/` 等整段规则；被清除的令牌/口令视为已泄露必须轮换（历史对象仍存在于旧 bundle、旧服务端对象库与本地 reflog）。
- **来源**：`history-purge-junk-paths-20261023`；AOCI 凭据产物备忘。
