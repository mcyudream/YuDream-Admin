package online.yudream.base.application.platform.capability.cmd;

import lombok.Data;

import java.util.Map;

@Data
public class CapabilityConfigUpdateCmd {
    private String code;
    private Map<String, String> config;
}
