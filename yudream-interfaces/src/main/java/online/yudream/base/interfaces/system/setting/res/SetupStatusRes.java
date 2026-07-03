package online.yudream.base.interfaces.system.setting.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统初始化状态响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupStatusRes {

    /**
     * 是否已完成初始化。
     */
    private boolean setupCompleted;
}
