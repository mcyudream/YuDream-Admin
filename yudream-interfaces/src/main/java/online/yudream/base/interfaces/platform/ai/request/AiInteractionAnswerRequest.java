package online.yudream.base.interfaces.platform.ai.request;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record AiInteractionAnswerRequest(Boolean approved, @Size(max = 20) Map<String, Object> output) { }
