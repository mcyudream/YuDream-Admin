package online.yudream.base.domain.platform.wiki.service;

import java.util.Map;

/**
 * 视觉 caption 端口：调用视觉模型为图片生成事实性描述。
 */
public interface WikiVisionCaptionGateway {

    String caption(String providerCode, String modelCode, Map<String, String> config, String imageDataUrl);
}
