package online.yudream.base.domain.system.monitor.repo;

import online.yudream.base.domain.system.monitor.dto.ResourceMetricPointDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ResourceMetricRepo {

    void save(ResourceMetricPointDTO point);

    List<ResourceMetricPointDTO> findSince(LocalDateTime from);
}
