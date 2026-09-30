package online.yudream.base.application.platform.mobile.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.mobile.enumerate.MobilePlatform;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 移动 manifest 聚合查询：platform 必填（android/ios），
 * hostVersion/capabilitySet 可空（空 capabilitySet 视为不要求任何原生能力）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileManifestQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private MobilePlatform platform;
    private String hostVersion;
    private Set<String> capabilitySet;
}
