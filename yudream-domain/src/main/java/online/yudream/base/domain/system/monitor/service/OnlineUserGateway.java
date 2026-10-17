package online.yudream.base.domain.system.monitor.service;

import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.system.monitor.dto.OnlineUserDTO;

import java.util.List;

/**
 * 在线会话网关：会话来自 Sa-Token 令牌存储，不属于仓储可下推的持久化数据，
 * 因此分页由网关在全量枚举上切片，禁止前端按页拼接。
 */
public interface OnlineUserGateway {

    /** 分页会话，按最近活跃优先。 */
    PageResult<OnlineUserDTO> page(String keyword, int page, int size);

    /** 全量会话（导出等一次性取数场景），最多 limit 条。 */
    List<OnlineUserDTO> list(String keyword, int limit);

    void kickout(String token);

    /** 强制某账号全部会话下线（账号删除等身份已失效场景，按登录号一次性注销）。 */
    void kickoutByUserId(Long userId);
}
