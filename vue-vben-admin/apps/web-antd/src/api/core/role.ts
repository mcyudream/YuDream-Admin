import {requestClient} from "#/api/request";
import type {SearchPageParams, SearchPageResponse} from "#/types/common";
import type {Role} from "#/types/organizationalStructure/role";

/**
 * 分页获取角色信息/
 */
export async function getAllRolesPageApi(searchPageParams: SearchPageParams) {
  return requestClient.post<SearchPageResponse<Role>>('/role/page', searchPageParams);
}

/**
 * 创建角色
 */
export async function createRoleApi(role: Role) {
  return requestClient.put<Role>('/role', role, {
    responseReturn:"body"
  });
}

/**
 * 更新角色信息
 * @param role
 */
export async function editRoleApi(role: Role) {
  return requestClient.post<Role>('/role', role, {
    responseReturn:"body"
  })
}

/**
 * 删除用户信息
 * @param role
 */
export async function deleteRoleApi(role: Role) {
  return requestClient.delete<Role>(`/role/${role.id}`, {
    responseReturn:"body"
  });
}
