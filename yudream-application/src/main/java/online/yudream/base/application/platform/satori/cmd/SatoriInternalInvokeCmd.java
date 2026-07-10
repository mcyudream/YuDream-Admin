package online.yudream.base.application.platform.satori.cmd;

import lombok.Data;

import java.util.Map;

@Data
public class SatoriInternalInvokeCmd {
    private Long connectionId;
    private String platform;
    private String userId;
    private String method;
    private Map<String, Object> payload;
}
