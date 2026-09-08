/** 客户端错误与开发模式关闭不应无限重连；网络层失败（无 status）走有限次瞬态重试 */
export function shouldStopSseReconnect(status?: number) {
  return status === 400 || status === 401 || status === 403 || status === 404 || status === 405
}
