package online.yudream.base.infra.platform.plugin.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformPlugin")
public class PluginModuleDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private String name;
    private String pluginVersion;
    private String description;
    private String icon;
    private String mainClass;
    private String jarPath;
    private String gitUrl;
    private String backupJarPath;
    private String backupName;
    private String backupPluginVersion;
    private String backupDescription;
    private String backupMainClass;
    private String backupSha256;
    private List<String> backupDependencies;
    private List<String> backupSoftDependencies;
    private PluginStatus backupStatus;
    private String backupErrorMessage;
    private LocalDateTime backupLoadedAt;
    private LocalDateTime backupEnabledAt;
    private Boolean backupMenusInitialized;
    private Boolean backupRestoreIntentActive;
    private Boolean restoreIntentActive;
    private List<String> dependencies;
    private List<String> softDependencies;
    private PluginStatus status;
    private String errorMessage;
    private LocalDateTime loadedAt;
    private LocalDateTime enabledAt;
    private Boolean menusInitialized;
    private List<String> themeScopes;
    private String marketSourceCode;
    /** plugin.yml mobile 块：android/ios 小写 token 列表；未声明移动支持时为 null。 */
    private List<String> mobilePlatforms;
    /** plugin.yml mobile 块：宿主最低版本；未声明时为 null。 */
    private String mobileMinHostVersion;
    /** plugin.yml mobile 块：要求的原生能力；未声明时为 null。 */
    private List<String> mobileRequiredNativeCapabilities;
}
