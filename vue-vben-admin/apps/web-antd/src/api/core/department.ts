import {requestClient} from "#/api/request";
import type {CreateDepartment, Department} from "#/types/organizationalStructure/department";
import type {SearchPageParams} from "#/types/common";

/**
   * 创建部门
 */
export async function createDepartmentApi(department: CreateDepartment) {
  return requestClient.put<String>('/department', department);
}

/**
 * 获取部门
 * @param searchParams 获取筛选后的所有部门
 */
export async function getAllDepartments(searchParams: SearchPageParams) {
  return requestClient.post<Department[]>('/department/getall', searchParams);
}

/**
 * 删除部门
 * @param department 部门
 */
export async function deleteDepartmentApi(department: Department) {
  return requestClient.delete(`/department/${department.id}`, {responseReturn:"body"});
}

/**
 * 编辑部门
 * @param department
 */
export async function updateDepartmentApi(department: Department) {
  return requestClient.post(`/department`, department, {
    responseReturn:"body"
  });
}
