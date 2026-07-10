package online.yudream.base.application.platform.satori.cmd;

import lombok.Data;

@Data
public class SatoriConnectionCreateCmd {
    private String name;
    private String baseUrl;
    private String token;
}
