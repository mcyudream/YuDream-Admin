package online.yudream.base.interfaces.platform.ai.res;

import lombok.Builder;
import lombok.Data;

/** 远端返回的模型条目；kind 与端点用途一致（chat/embedding/rerank）。 */
@Data
@Builder
public class AiDiscoveredModelRes {
    private String code;
    private String name;
    private String kind;
}
