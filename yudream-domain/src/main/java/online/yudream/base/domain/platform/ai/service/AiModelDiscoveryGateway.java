package online.yudream.base.domain.platform.ai.service;

import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryQuery;
import online.yudream.base.domain.platform.ai.valobj.AiModelDiscoveryResult;

/**
 * 端点模型发现端口：按端点协议调用远端模型列表接口，供管理端「获取模型」使用。
 * 实现方负责按协议选择地址与鉴权方式，且不得记录或回传凭据。
 */
public interface AiModelDiscoveryGateway {

    AiModelDiscoveryResult discover(AiModelDiscoveryQuery query);
}
