package online.yudream.base.application.platform.ai.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.common.exception.BizException;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

/** 系统预算运行单独授权；普通助手只观察自己的运行。 */
@Service
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "yudream.platform.capabilities.ai", name = "enabled", havingValue = "true")
public class AiSystemRunAppService {
    private final AiRunAppService runs;

    public List<AiRunDTO> list(Long actorId, List<String> permissions, boolean activeOnly) {
        authorize(actorId, permissions, "view");
        return runs.list(0L, null, activeOnly);
    }
    public AiRunDTO get(Long actorId, List<String> permissions, String id) {
        authorize(actorId, permissions, "view");
        return runs.get(0L, id);
    }
    public AiRunDTO cancel(Long actorId, List<String> permissions, String id) {
        authorize(actorId, permissions, "edit");
        return runs.cancel(0L, id);
    }
    public AiRunDTO answer(Long actorId, List<String> permissions, String id, String requestId, Boolean approved, Map<String, Object> output) {
        authorize(actorId, permissions, "accept");
        return runs.respond(0L, id, requestId, approved, output, permissions);
    }
    private static void authorize(Long actorId, List<String> permissions, String action) {
        if (actorId == null || actorId == 0) throw new BizException("当前用户未登录");
        if (permissions == null || (!permissions.contains("*") && !permissions.contains("platform:ai:system:" + action)))
            throw new BizException("没有系统 AI 运行管理权限");
    }
}
