
import { requestClient } from '#/api/request';
import type {BasicUserInfo} from "@vben/stores";
import type {SearchPageParams, SearchPageResponse} from "#/types/common";
import type {User} from "#/types/user";

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<BasicUserInfo>('/user/info');
}

/**
 * 分页查询用户
 * @param searchParams
 */
export async function getUsersPageApi(searchParams: SearchPageParams){
  return requestClient.post<SearchPageResponse<User>>('/user/page', searchParams);
}

/**
 * 修改用户信息[nickname, email, phone]
 * @param user
 */
export async function editUserApi(user: User){
  return requestClient.post(`/user`, user);
}

/**
 * 删除用户
 * @param user
 */
export async function deleteUserApi(user: User){
  return requestClient.delete(`/user/${user.id}`);
}


export async function addToDepartmentApi(user: User, department: string){
  return requestClient.post<User>(`/user/department`, {
    username: user.id,
    department: department,
  }, {
    responseReturn:"body"
  });
}

export async function deleteUserDepartmentApi(user: User, department: string){
  return requestClient.post<User>(`/user/department/d`, {
    username: user.id,
    department: department,
  }, {
    responseReturn:"body"
  });
}
