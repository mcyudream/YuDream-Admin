
import { requestClient } from '#/api/request';
import type {SystemInfo} from "#/types/systemInfo";

/**
 * 获取服务器信息
 */
export async function getSystemInfoApi() {
  return requestClient.get<SystemInfo>('/system');
}
