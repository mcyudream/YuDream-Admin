import type {Department} from "#/types/organizationalStructure/department";

export interface User {
  id?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  status?: number;
  createTime?: Date;
  updateTime?: Date;
  departments?: Department[];
}
