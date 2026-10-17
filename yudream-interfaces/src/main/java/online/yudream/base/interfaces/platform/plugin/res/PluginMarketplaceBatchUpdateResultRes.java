package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 批量更新结果：成功清单、逐项失败原因与是否需要重启。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketplaceBatchUpdateResultRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Builder.Default
    private List<String> updatedCodes = new ArrayList<>();

    @Builder.Default
    private List<FailureRes> failures = new ArrayList<>();

    private boolean requiresRestart;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailureRes implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String reason;
    }
}
