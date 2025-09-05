import {requestClient} from "#/api/request";
import type {Permission} from "#/types/organizationalStructure/permission";
import type {Role} from "#/types/organizationalStructure/role";

/**
 * 获取角色权限
 */
export async function getPermissionByRoleApi(role: Role) {
  return requestClient.get<string[]>(`/permission/${role.id}`, );
}

/**
 * 获取所有权限
 */
export async function getAllPermissionsApi() {
  return requestClient.get<Permission[]>(`/permission`);
}

/**
 * 设置权限
 * @param role
 * @param permissions
 */
export async function setPermissionApi(role: Role, permissions: string[]) {
  return requestClient.post<string>(`/permission/${role.id}`, permissions, {
    responseReturn:"body"
  });
}

/**
 * 新增权限
 * @param permission
 */
export async function addPermissionApi(permission: Permission) {
  return requestClient.put<string>(`/permission`, permission, {
    responseReturn:"body"
  })
}
