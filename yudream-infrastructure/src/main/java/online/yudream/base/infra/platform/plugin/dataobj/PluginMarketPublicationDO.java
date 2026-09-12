package online.yudream.base.infra.platform.plugin.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationChannel;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/** pluginVersion 是插件语义版本；BaseDO.version 是乐观锁版本，两者不可混用。 */
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(def = "{'code': 1, 'pluginVersion': 1}", unique = true)
@Document(collection = "platformPluginMarketPublication")
public class PluginMarketPublicationDO extends BaseDO {

    private String code;
    private String pluginVersion;
    private String displayName;
    private String description;
    private String mainClass;
    private List<String> dependencies;
    private List<String> softDependencies;
    private String icon;
    private String releaseNotes;
    private String license;
    private String category;
    private List<String> tags;
    private String compatibilityJson;
    private String publisherJson;
    private String descriptorJson;
    private String jarPath;
    private String sha256;
    private Long sizeBytes;
    private Long downloadCount;
    private Long publisherUserId;
    private PluginPublicationChannel channel;
    private PluginPublicationStatus status;
    private String reviewNote;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
}
