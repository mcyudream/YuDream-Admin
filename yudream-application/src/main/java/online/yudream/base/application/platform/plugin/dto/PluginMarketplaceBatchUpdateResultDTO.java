package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量更新结果：逐项更新，失败的记录原因而不中断其余项。
 * 已完成的更新不回滚（JAR 已被替换），前端据此提示“部分成功”。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketplaceBatchUpdateResultDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<String> updatedCodes = new ArrayList<>();

    @Builder.Default
    private List<FailureDTO> failures = new ArrayList<>();

    private boolean requiresRestart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String reason;
    }
}
