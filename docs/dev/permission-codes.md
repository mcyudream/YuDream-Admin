# 权限码：结构、动作词表与展示规范

> **适用范围**：宿主与全部插件的权限码注册、权限树/矩阵/溯源展示。
> **目标**：权限码结构稳定、动作词表统一、历史授权永不因整理丢失。

## 1. 权限码结构

- 结构：`{域}:{资源}:{动作}`（必要时 `{域}:{子域}:{资源}:{动作}`）；插件统一 `plugin:{pluginCode}:{动作}`。
- `@PermissionRegister` 的 `module` 使用中文模块名。
- 新权限按资源拆分为独立的 `view`/`edit`/`delete` 等权限码，**禁止**只注册一个 `manage` 再在代码内二次判断。

## 2. 末段动作词表（唯一来源）

末段动作词决定角色权限树的业务分组与色彩，**必须**从下表选择，**禁止**自造同义词：

| 分组 | 动作词 |
|---|---|
| 查看 | `view`、`export`、`download` |
| 操作 | `create`、`edit`、`import`、`upload`、`use`、`generate`、`publish`、`connect`、`send`、`test`、`run`、`invoke`、`execute`、`enable`、`disable`、`assign-*`、`dataset`、`report`、`accept` |
| 管理 | `manage`、`config` |
| 危险 | `delete`、`purge`、`kickout`、`revoke`、`impersonate` |

- 词表外动作归入「其他」分组。
- `purge` 专指**永久删除且不可恢复**（连账号级联数据一起回收）。`delete` 在历史用法里同时承载「真实删除」（菜单）与「停用」（用户/部门/角色的 `:delete` 实为停用）两种口径，语义不明的资源新增删除能力时用 `purge` 把不可恢复语义写死。
- 确需新动作词时，**在同一提交**同步三处：本文件本节、集中词表 `yudream-frontend/apps/core-arco-design-vue/src/utils/permission-catalog.ts` 的 `permissionCategoryMeta`、插件仓对应规范。

## 3. 展示与目录规范

- 权限管理统一按「业务域 → 稳定资源码 → 动作分类 → 权限」展示；权限树/矩阵/溯源共享 `permission-catalog.ts`，`assign-*` 匹配完整规范后缀，不以中文模块名作资源 key，`resourceAliases` 不按中文同名猜测。
- 历史入口码与接口动作按 `SystemMenuModule` 真实绑定归同资源展示但保留独立叶子；插件历史嵌套码归稳定 owner；历史非标准码列入「其他」并兼容保留。整理分组**不重命名历史权限码**，不丢失搜索隐藏或暂失效的插件授权。
- 目录归一顺序：ANNOTATION → 实际注册 MANUAL → MENU 补缺，同码元数据稳定归一，菜单不得覆盖端点登记；CATEGORY/LAYOUT 不生成独立权限。
- 全景用户/菜单分别受 `system:user:view`/`system:menu:view` 控制；统计由仓储聚合全量数据，不能依据当前分页；菜单关系复用真实可见码算法。只有本人使用当前 tokenSession，其他用户明确显示默认部门/角色上下文（`contextSource=DEFAULT`），禁止暗示其真实在线会话。

## 4. 授权与编辑语义

- 矩阵默认只读，明确开启编辑后提交差量；快捷授权与用户角色分配以原完整数组 CAS，保留未点名撤销的历史码，冲突**不得**自动覆盖；用户新增角色校验已有部门并在 CAS 中复核。系统超管与历史 `*` 角色锁定单项操作。
- 权限树按叶子分页叠加虚拟窗口，目录/搜索索引缓存、搜索防抖；勾选只合并当前页可见叶，保留其他页及未知授权；全选全部与本页操作明确区分。
- 角色保存/分配不校验权限码存在性：失效码原样入库、仅展示层隐藏，插件重载/权限重现后授权自动生效。
- 重建只操作 `sysPermission`：先可回读备份、完整计划验证、写失败恢复，并核对角色/用户/菜单指纹不变；不重启插件、不重新播种授权。
- 角色分配是部门作用域：先切部门再选该部门角色；`GET /api/user/me/roles` 只回当前部门角色，`switch-role` 拒绝部门外角色；切部门后会话角色移到新部门首个可用角色；`PermissionAppService`/`SaTokenPermissionProvider` 部门优先解析生效角色并回落到持有角色， legacy 数据不失权限。
- 完整细则见 `docs/system/permission-management.md`。
