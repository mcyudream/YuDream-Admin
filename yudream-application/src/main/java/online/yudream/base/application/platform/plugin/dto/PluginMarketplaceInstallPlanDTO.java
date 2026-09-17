package online.yudream.base.application.platform.plugin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/** 市场插件安装计划：按依赖顺序列出全部前置依赖及安装动作，供安装确认弹窗勾选。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketplaceInstallPlanDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String releaseVersion;
    private boolean installable;
    private String installDisabledReason;
    /** 依赖条目按「被依赖者在前」排序，批量安装按此顺序执行。 */
    private List<EntryDTO> entries;

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntryDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private String code;
        private String displayName;
        /** true=硬依赖（必须安装/已满足才能安装目标），false=软依赖（可选）。 */
        private boolean required;
        private String range;
        private boolean installed;
        private String installedVersion;
        private boolean versionSatisfied;
        private boolean storeAvailable;
        private String storeVersion;
        private String storeSourceCode;
        private String storeSourceName;
        /** 候选市场版本自身是否可安装（兼容性与其必需依赖）。 */
        private boolean installable;
        private String installDisabledReason;
    }
}
