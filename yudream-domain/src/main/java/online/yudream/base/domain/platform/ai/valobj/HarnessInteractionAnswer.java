package online.yudream.base.domain.platform.ai.valobj;

import java.util.Map;

/** confirmation 必须给 approved；external_execution 必须给真实 output，不能制造确认过的工具 ID。 */
public record HarnessInteractionAnswer(String requestId, Boolean approved, Map<String, Object> output) {
    public HarnessInteractionAnswer {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("待办标识不能为空");
        }
        output = output == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(output));
    }
}
