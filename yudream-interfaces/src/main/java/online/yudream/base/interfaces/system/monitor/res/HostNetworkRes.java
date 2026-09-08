package online.yudream.base.interfaces.system.monitor.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HostNetworkRes {
    private String name;
    private String displayName;
    private String ipv4;
    private String mac;
    private Long recvBytes;
    private Long sentBytes;
    private Long recvBytesPerSec;
    private Long sentBytesPerSec;
}
