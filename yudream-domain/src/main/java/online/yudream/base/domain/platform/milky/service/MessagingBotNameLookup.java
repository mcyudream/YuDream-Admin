package online.yudream.base.domain.platform.milky.service;

import java.util.Optional;

/**
 * 已启用连接上的机器人展示名。官方来自 READY 缓存的 username，未就绪时为空。
 */
public interface MessagingBotNameLookup {
    Optional<String> botName(Long connectionId);
}
