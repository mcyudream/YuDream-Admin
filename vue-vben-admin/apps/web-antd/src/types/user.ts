import type {Department} from "#/types/organizationalStructure/department";
import type {Role} from "#/types/organizationalStructure/role";

export interface User {
  id?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status?: number;
  createTime?: Date;
  updateTime?: Date;
  departmentRoles?: DepartmentRoleEntity[];
}

export interface DepartmentRoleEntity {
  department?: Department;
  role?: Role,
  dataRange: string;
}
