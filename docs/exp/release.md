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
- **规避**：失败 tag 不移动；修复后按已验证流程使用新的后缀 tag 重跑；确认 `authorize-main` 等短时授权仍有效。
- **来源**：`plugin-ci-release-tags`、`CI tag-only pipeline and push guard`。

## 2. GitLab CI 行为

### Bash heredoc 中反斜杠转义在工具通道被折叠

- **现象**：Python heredoc 正则写 `\\u...` 后报 `re.PatternError`，或字符串字面量触发 unicodeescape SyntaxError。
- **根因**：ZCode Bash 命令通道会把命令字符串里的双反斜杠折为单反斜杠，heredoc 内容也受影响。
- **规避**：构造含反斜杠文本用 `chr(92)` 拼接，命令串中不要出现字面量反斜杠加 `u`；优先使用专用文件编辑工具。
- **来源**：`bash-heredoc-backslash-fold`。

### 推分支没有流水线

- **现象**：master push 后看不到 CI run。
- **根因**：主仓 CI 只对 tag 触发，分支推送不运行流水线；插件仓可能也使用 tag-only 规则。
- **规避**：不要把无流水线误判为推送失败；本地按仓库验证清单执行等价检查；确认 tag 规则后再触发发布流程。
- **来源**：`ci-tag-only-pipeline-and-push-guard`。

### 发布产物版本和源码属性不一致

- **现象**：根 pom/模块 pom 看似更新，但插件源码仍编译旧 SPI，或 pipeline 发布了错误版本。
- **根因**：漏改 SPI 模块版本与主仓 `yudream.plugin.spi.version` 双点之一，或版本来自陈旧记忆。
- **规避**：每次从真实源文件读取；SPI 两处同步；tag 规则与实际变更包匹配；等待 verify 通过后再同步插件仓。
- **来源**：`docs/dev/contract-release.md`。
