package online.yudream.base.domain.platform.milky.service;

import online.yudream.base.domain.platform.milky.model.MilkyModels.Context;

/**
 * 协议无关的原始调用端口。共享 API 名（如 send_group_message）由传输适配器映射到具体协议；
 * 官方 OpenAPI 路径也可作为特异化入口直接透传。
 */
public interface MilkyApiGateway {
    Object invoke(Context context, String api, Object body);
}
