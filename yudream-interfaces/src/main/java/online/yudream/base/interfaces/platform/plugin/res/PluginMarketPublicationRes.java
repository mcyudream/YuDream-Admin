package online.yudream.base.interfaces.platform.plugin.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationChannel;
import online.yudream.base.domain.platform.plugin.enumerate.PluginPublicationStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PluginMarketPublicationRes implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
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
    private Map<String, String> compatibility;
    private String sha256;
    private Long sizeBytes;
    private Long downloadCount;
    private String publisherUserId;
    private PluginPublicationChannel channel;
    private PluginPublicationStatus status;
    private String reviewNote;
    private String reviewerUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
}
