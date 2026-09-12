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
    private String releaseNotes;
    private String license;
    private String sha256;
    private Long sizeBytes;
    private Long publisherUserId;
    private PluginPublicationChannel channel;
    private PluginPublicationStatus status;
    private String reviewNote;
    private Long reviewerUserId;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
}
