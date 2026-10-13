<script setup lang="ts">
import type { PermissionItem, RoleManageItem, RoleStatus } from '@/api/modules/system-role'
import { permissionCategoryMeta, permissionCategoryOf, permissionResourceOf } from '@/utils/permission-catalog'
import {
  MATRIX_PERMISSION_PAGE_SIZE,
  MATRIX_ROLE_PAGE_SIZE,
  MATRIX_UNASSIGNED_DEPT,
  matrixGrantLabel,
  matrixGrantState,
  matrixPage,
  matrixPermissions,
  matrixRoles,
} from './permission-matrix'

const props = defineProps<{
  roles: RoleManageItem[]
  permissions: PermissionItem[]
  loading: boolean
}>()

const emit = defineEmits<{
  editRole: [role: RoleManageItem]
}>()

const { auth } = useAppAuth()
const canEdit = computed(() => auth('system:role:edit'))
const permissionKeyword = ref('')
const roleKeyword = ref('')
const selectedDomain = ref('')
const selectedResource = ref('')
const selectedCategory = ref('')
const selectedDept = ref('')
const selectedStatus = ref<RoleStatus | ''>('')
const rolePage = ref(1)
const permissionPage = ref(1)
const rolePageSize = ref(MATRIX_ROLE_PAGE_SIZE)
const permissionPageSize = ref(MATRIX_PERMISSION_PAGE_SIZE)

const domainOptions = computed(() => {
  const domains = new Map<string, string>()
  matrixPermissions(props.permissions).forEach((item) => {
    const resource = permissionResourceOf(item)
    domains.set(resource.domainKey, resource.domainLabel)
  })
  return [{ label: '全部业务域', value: '' }, ...Array.from(domains, ([value, label]) => ({ label, value }))]
})
const resourceOptions = computed(() => {
  const resources = new Map<string, string>()
  matrixPermissions(props.permissions, { domain: selectedDomain.value }).forEach((item) => {
    const resource = permissionResourceOf(item)
    resources.set(resource.key, resource.label)
  })
  return [{ label: '全部资源', value: '' }, ...Array.from(resources, ([value, label]) => ({ label, value }))]
})
const categoryOptions = [{ label: '全部动作分类', value: '' }, ...permissionCategoryMeta.map(item => ({ label: item.label, value: item.key }))]

const deptOptions = computed(() => {
  const depts = new Map<string, string>()
  props.roles.forEach((role) => {
    if (role.deptId) {
      depts.set(role.deptId, role.deptName || '未命名部门')
    }
  })
  return [
    { label: '全部部门', value: '' },
    { label: '未关联部门', value: MATRIX_UNASSIGNED_DEPT },
    ...Array.from(depts, ([value, label]) => ({ label, value })),
  ]
})

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DEPRECATED' },
]

const filteredRoles = computed(() => matrixRoles(props.roles, {
  keyword: roleKeyword.value,
  deptId: selectedDept.value,
  status: selectedStatus.value,
}))
const filteredPermissions = computed(() => matrixPermissions(props.permissions, {
  domain: selectedDomain.value,
  resource: selectedResource.value,
  category: selectedCategory.value,
  keyword: permissionKeyword.value,
}))
const visibleRoles = computed(() => matrixPage(filteredRoles.value, rolePage.value, MATRIX_ROLE_PAGE_SIZE))
const visiblePermissions = computed(() => matrixPage(filteredPermissions.value, permissionPage.value, MATRIX_PERMISSION_PAGE_SIZE))
const groups = computed(() => {
  const grouped = new Map<string, { key: string, label: string, domainLabel: string, permissions: PermissionItem[] }>()
  visiblePermissions.value.forEach((permission) => {
    const resource = permissionResourceOf(permission)
    const group = grouped.get(resource.key) || { key: resource.key, label: resource.label, domainLabel: resource.domainLabel, permissions: [] }
    group.permissions.push(permission)
    grouped.set(resource.key, group)
  })
  return Array.from(grouped.values())
})
const grantSets = computed(() => new Map(visibleRoles.value.map(role => [role.id, new Set(role.permissions)])))
const emptyMessage = computed(() => {
  if (!props.roles.length) {
    return '暂无角色，可通过新增角色建立权限配置。'
  }
  if (!props.permissions.some(item => item.status === 'ACTIVE')) {
    return '暂无可用权限。'
  }
  return '没有符合筛选条件的角色或权限，请调整筛选条件。'
})

watch([roleKeyword, selectedDept, selectedStatus], () => rolePage.value = 1)
watch([permissionKeyword, selectedDomain, selectedResource, selectedCategory], () => permissionPage.value = 1)
watch(selectedDomain, () => selectedResource.value = '')
watch(() => filteredRoles.value.length, (total) => {
  rolePage.value = Math.min(rolePage.value, Math.max(1, Math.ceil(total / MATRIX_ROLE_PAGE_SIZE)))
})
watch(() => filteredPermissions.value.length, (total) => {
  permissionPage.value = Math.min(permissionPage.value, Math.max(1, Math.ceil(total / MATRIX_PERMISSION_PAGE_SIZE)))
})

function stateOf(role: RoleManageItem, permission: PermissionItem) {
  return matrixGrantState(role, permission.code, grantSets.value.get(role.id))
}

function editRole(role: RoleManageItem) {
  if (canEdit.value) {
    emit('editRole', role)
  }
}

function resetFilters() {
  permissionKeyword.value = ''
  roleKeyword.value = ''
  selectedDomain.value = ''
  selectedResource.value = ''
  selectedCategory.value = ''
  selectedDept.value = ''
  selectedStatus.value = ''
}
</script>

<template>
  <section class="permission-matrix" aria-label="只读权限矩阵" :aria-busy="loading">
    <div class="matrix-intro">
      <div>
        <h2 class="text-base font-semibold">
          权限配置矩阵
        </h2>
        <p class="matrix-description">
          对照角色保存的权限配置。用户实际权限按当前所选部门和角色判定；停用角色的配置未生效。
        </p>
      </div>
      <YdTag variant="outline">
        只读
      </YdTag>
    </div>

    <div class="matrix-filters">
      <label class="matrix-filter">
        <span>角色</span>
        <YdInput v-model="roleKeyword" clearable placeholder="角色名称 / 编码" aria-label="搜索角色名称或编码" class="w-full" />
      </label>
      <div class="matrix-filter">
        <span>部门</span>
        <YdSelect v-model="selectedDept" :options="deptOptions" aria-label="筛选角色所属部门" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>角色状态</span>
        <YdSelect v-model="selectedStatus" :options="statusOptions" aria-label="筛选角色状态" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>业务域</span>
        <YdSelect v-model="selectedDomain" :options="domainOptions" aria-label="筛选权限业务域" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>资源</span>
        <YdSelect v-model="selectedResource" :options="resourceOptions" aria-label="筛选权限资源" class="w-full" />
      </div>
      <div class="matrix-filter">
        <span>动作分类</span>
        <YdSelect v-model="selectedCategory" :options="categoryOptions" aria-label="筛选权限动作分类" class="w-full" />
      </div>
      <label class="matrix-filter">
        <span>权限</span>
        <YdInput v-model="permissionKeyword" clearable placeholder="权限名称 / 编码" aria-label="搜索权限名称或编码" class="w-full" />
      </label>
      <YdButton variant="outline" class="self-end" @click="resetFilters">
        重置
      </YdButton>
    </div>

    <div class="matrix-legend" aria-label="矩阵图例">
      <span><YdIcon name="morph:check" aria-hidden="true" /> 已授权</span>
      <span><YdIcon name="morph:minus" aria-hidden="true" /> 未授权</span>
      <span><YdIcon name="morph:shield-check" aria-hidden="true" /> 超级管理员 · 全部权限</span>
      <span class="matrix-description">点击角色标题进入原有编辑与权限树</span>
    </div>

    <div v-if="loading" class="matrix-empty" role="status">
      <YdIcon name="morph:loader-circle" class="animate-spin" aria-hidden="true" />
      正在加载权限矩阵…
    </div>
    <div v-else-if="!filteredRoles.length || !filteredPermissions.length" class="matrix-empty" role="status">
      <YdIcon name="morph:grid-2x2" aria-hidden="true" />
      {{ emptyMessage }}
    </div>
    <template v-else>
      <!-- 权限矩阵有双向表头与逐列角色操作，使用语义 table 保持固定标题和屏幕阅读器关联。 -->
      <div class="matrix-scroll" tabindex="0" role="region" aria-label="角色与权限对照表，可横向和纵向滚动">
        <table class="matrix-table">
          <caption class="sr-only">
            只读角色权限配置。行是权限，列是角色；停用角色的配置未生效。
          </caption>
          <thead>
            <tr>
              <th scope="col" class="matrix-permission-heading">
                <span>权限 / 角色</span>
                <small>{{ filteredPermissions.length }} 项权限 · {{ filteredRoles.length }} 个角色</small>
              </th>
              <th v-for="role in visibleRoles" :key="role.id" scope="col" :class="{ 'matrix-disabled': role.status === 'DEPRECATED' }">
                <YdButton
                  variant="ghost"
                  size="sm"
                  class="matrix-role-button"
                  :disabled="!canEdit"
                  :aria-label="canEdit ? `编辑角色 ${role.name}，打开原有权限树` : `角色 ${role.name}，无编辑权限`"
                  :title="canEdit ? `编辑 ${role.name}` : '当前账号没有角色编辑权限'"
                  @click="editRole(role)"
                >
                  <span class="truncate">{{ role.name }}</span>
                  <YdIcon v-if="canEdit" name="morph:pencil" class="shrink-0 size-3" aria-hidden="true" />
                </YdButton>
                <small class="matrix-role-code" :title="role.code">{{ role.code }}</small>
                <small>{{ role.deptName || '未关联部门' }}</small>
                <YdTag v-if="role.status === 'DEPRECATED'" variant="secondary" class="mt-1">
                  停用 · 配置未生效
                </YdTag>
                <YdTag v-else-if="role.systemType === 'SUPER_ADMIN'" variant="outline" class="mt-1">
                  超级管理员
                </YdTag>
              </th>
            </tr>
          </thead>
          <tbody v-for="group in groups" :key="group.key">
            <tr class="matrix-module-row">
              <th scope="rowgroup" :colspan="visibleRoles.length + 1">
                <span>{{ group.domainLabel }} / {{ group.label }}</span>
              </th>
            </tr>
            <tr v-for="permission in group.permissions" :key="permission.code">
              <th scope="row" class="matrix-permission-cell" :title="permission.desc || permission.name">
                <div class="flex flex-wrap gap-2 items-center">
                  <span>{{ permission.name }}</span>
                  <YdTag :variant="permissionCategoryOf(permission.code).variant">
                    {{ permissionCategoryOf(permission.code).label }}
                  </YdTag>
                </div>
                <small>{{ permission.code }}</small>
              </th>
              <td
                v-for="role in visibleRoles"
                :key="role.id"
                :class="{ 'matrix-disabled': role.status === 'DEPRECATED' }"
              >
                <span
                  class="matrix-grant"
                  :class="{ 'matrix-granted': stateOf(role, permission) !== 'unassigned' }"
                  role="img"
                  :aria-label="matrixGrantLabel(role, permission, stateOf(role, permission))"
                  :title="matrixGrantLabel(role, permission, stateOf(role, permission))"
                >
                  <YdIcon
                    :name="stateOf(role, permission) === 'super-admin' ? 'morph:shield-check' : stateOf(role, permission) === 'assigned' ? 'morph:check' : 'morph:minus'"
                    aria-hidden="true"
                  />
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="matrix-pagination">
        <div class="matrix-page-group">
          <span>角色列</span>
          <YdPagination v-model:page="rolePage" v-model:size="rolePageSize" :total="filteredRoles.length" layout="total, pager" :text-templates="{ total: total => `共 ${total} 个角色` }" />
        </div>
        <div class="matrix-page-group">
          <span>权限行</span>
          <YdPagination v-model:page="permissionPage" v-model:size="permissionPageSize" :total="filteredPermissions.length" layout="total, pager" :text-templates="{ total: total => `共 ${total} 项权限` }" />
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.permission-matrix {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.matrix-intro {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
}

.matrix-description {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.matrix-filters {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 160px), 1fr));
  gap: 12px;
}

.matrix-filter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  color: var(--color-text-2);
}

.matrix-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
}

.matrix-legend > span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.matrix-legend .matrix-description {
  margin: 0;
}

.matrix-empty {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  padding: 24px;
  color: var(--color-text-3);
  text-align: center;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-scroll {
  max-height: 620px;
  overflow: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.matrix-table {
  width: 100%;
  font-size: 13px;
  border-spacing: 0;
  border-collapse: separate;
}

.matrix-table th,
.matrix-table td {
  padding: 10px 12px;
  vertical-align: middle;
  border-right: 1px solid var(--color-border-1);
  border-bottom: 1px solid var(--color-border-1);
}

.matrix-table tr > :last-child {
  border-right: 0;
}

.matrix-table tbody:last-child tr:last-child > * {
  border-bottom: 0;
}

.matrix-table thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  min-width: 176px;
  max-width: 208px;
  font-weight: 500;
  text-align: center;
  background: var(--color-bg-2);
}

.matrix-table .matrix-permission-heading {
  left: 0;
  z-index: 3;
  min-width: 250px;
  text-align: left;
}

.matrix-table small {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 400;
  color: var(--color-text-3);
}

.matrix-role-button {
  width: 100%;
  max-width: 184px;
}

.matrix-role-code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.matrix-module-row th {
  padding-top: 8px;
  padding-bottom: 8px;
  font-size: 12px;
  color: var(--color-text-2);
  text-align: left;
  background: var(--color-fill-2);
}

.matrix-module-row span {
  position: sticky;
  left: 12px;
}

.matrix-table .matrix-permission-cell {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 250px;
  max-width: 300px;
  font-weight: 500;
  text-align: left;
  overflow-wrap: anywhere;
  background: var(--color-bg-2);
}

.matrix-table td {
  text-align: center;
  background: var(--color-bg-2);
}

.matrix-table tbody tr:hover td,
.matrix-table tbody tr:hover .matrix-permission-cell {
  background: var(--color-fill-1);
}

.matrix-grant {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--color-text-4);
  border: 1px solid transparent;
  border-radius: 8px;
}

.matrix-granted {
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}

.matrix-disabled .matrix-granted {
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-style: dashed;
}

.matrix-pagination {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  justify-content: space-between;
}

.matrix-page-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
