package online.yudream.base.application.platform.milky.port;

import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;

/**
 * 官方机器人 Webhook 入口。应用层只负责校验连接，协议解析在基础设施完成。
 */
public interface OfficialQqBotWebhookGateway {
    Object handle(MilkyConnection connection, String rawBody, String signature, String timestamp);
}
