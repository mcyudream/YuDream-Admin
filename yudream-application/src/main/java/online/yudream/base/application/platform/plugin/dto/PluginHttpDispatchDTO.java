package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginHttpDispatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int status;
    private String contentType;
    private Object body;
    private boolean wrapped;

    @Builder.Default
    private Map<String, String> headers = new HashMap<>();
}
