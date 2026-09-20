package online.yudream.base.application.installer.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 中间件自动发现结果：按可达性与延迟排序的候选列表，供向导预填表单。
 */
@Data
@Builder
public class MiddlewareDiscoveryDTO {

    private List<MiddlewareProbeDTO> mongo;
    private List<MiddlewareProbeDTO> redis;
}
