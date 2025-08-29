
import { requestClient } from '#/api/request';
import type {BasicUserInfo} from "@vben/stores";

/**
 * 获取用户信息
 */
export async function getUserInfoApi() {
  return requestClient.get<BasicUserInfo>('/user/info');
}
