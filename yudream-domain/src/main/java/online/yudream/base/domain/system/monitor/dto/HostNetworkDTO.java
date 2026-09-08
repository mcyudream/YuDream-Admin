package online.yudream.base.domain.system.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostNetworkDTO {

    private String name;

    private String displayName;

    private String ipv4;

    private String mac;

    private Long recvBytes;

    private Long sentBytes;

    private Long recvBytesPerSec;

    private Long sentBytesPerSec;
}
