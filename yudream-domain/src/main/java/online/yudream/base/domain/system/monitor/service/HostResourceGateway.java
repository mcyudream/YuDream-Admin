package online.yudream.base.domain.system.monitor.service;

import online.yudream.base.domain.system.monitor.dto.HostResourceSnapshotDTO;

public interface HostResourceGateway {

    HostResourceSnapshotDTO snapshot();
}
