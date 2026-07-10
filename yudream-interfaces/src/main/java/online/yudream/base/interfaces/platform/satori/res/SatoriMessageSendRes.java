package online.yudream.base.interfaces.platform.satori.res;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SatoriMessageSendRes {
    private List<String> messageIds;
    private boolean rendered;
    private boolean degraded;
}
