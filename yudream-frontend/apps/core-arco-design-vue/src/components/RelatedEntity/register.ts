import DeptEntityPeek from './DeptEntityPeek.vue'
import { registerRelatedEntity } from './registry'
import RoleEntityPeek from './RoleEntityPeek.vue'
import UserEntityPeek from './UserEntityPeek.vue'

/**
 * 内置关联实体预览卡注册。
 * 插件后续经 SDK 注册自己的实体类型（同一 registerRelatedEntity 入口），宿主只负责内置类型与降级。
 */
registerRelatedEntity('user', UserEntityPeek)
registerRelatedEntity('dept', DeptEntityPeek)
registerRelatedEntity('role', RoleEntityPeek)
