package online.yudream.base.application.platform.mobile.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 移动插件清单条目：一个已启用且声明 mobile 支持的插件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileManifestEntryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;
    private String version;
    private String assetRevision;
    private String remoteEntrySha256;
    private String remoteEntryUrl;
    private String minHostVersion;
    private String styleUrl;

    @Builder.Default
    private List<String> platforms = new ArrayList<>();

    @Builder.Default
    private List<String> requiredNativeCapabilities = new ArrayList<>();
}
