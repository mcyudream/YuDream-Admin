package online.yudream.base.application.platform.satori.cmd;

import lombok.Data;

@Data
public class SatoriConnectionUpdateCmd {
    private Long id;
    private String name;
    private String baseUrl;
    /** An empty value preserves the current credential. */
    private String token;
}
