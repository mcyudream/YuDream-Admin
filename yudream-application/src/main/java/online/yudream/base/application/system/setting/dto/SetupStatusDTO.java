package online.yudream.base.application.system.setting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
     * 系统初始化状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStatusDTO {

    /**
     * 是否已完成初始化。
     */
    private boolean setupCompleted;
}
