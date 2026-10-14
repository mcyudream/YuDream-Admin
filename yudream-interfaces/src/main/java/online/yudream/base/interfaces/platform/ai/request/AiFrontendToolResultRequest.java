package online.yudream.base.interfaces.platform.ai.request;

import jakarta.validation.constraints.Size;
import java.util.Map;

public record AiFrontendToolResultRequest(boolean ok, @Size(max = 20) Map<String, Object> result,
                                          @Size(max = 500) String error) { }
