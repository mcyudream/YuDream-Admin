import { registerRelatedEntity } from '@yudream/components'
import DeptEntityPeek from './DeptEntityPeek.vue'
import RoleEntityPeek from './RoleEntityPeek.vue'
import UserEntityPeek from './UserEntityPeek.vue'

/**
 * 内置关联实体预览卡注册（宿主内置类型，owner 为 host）。
 * 插件实体类型经 SDK `sdk.relatedEntity.register(type, provider)` 注册（登记键 `${pluginCode}:${type}`），
 * 插件卸载时由宿主按插件码兜底注销；未注册类型由 YdRelatedEntity 降级为纯文本。
 */
registerRelatedEntity('user', UserEntityPeek)
registerRelatedEntity('dept', DeptEntityPeek)
registerRelatedEntity('role', RoleEntityPeek)
