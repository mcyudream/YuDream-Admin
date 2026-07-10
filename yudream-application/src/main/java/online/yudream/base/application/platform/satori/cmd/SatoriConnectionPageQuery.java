package online.yudream.base.application.platform.satori.cmd;

import lombok.Data;

@Data
public class SatoriConnectionPageQuery {
    private String keyword;
    private int page = 1;
    private int size = 20;
}
