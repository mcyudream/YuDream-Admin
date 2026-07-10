package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SatoriConnectionTestRes {
    private boolean success;
    private String impl;
    private String protocolVersion;
    private String adapter;
    private List<String> features;
    private LocalDateTime testedAt;
}
