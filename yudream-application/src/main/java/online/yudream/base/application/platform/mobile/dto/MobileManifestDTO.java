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
 * GET /api/mobile/manifest 的聚合结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileManifestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String platform;

    @Builder.Default
    private List<MobileManifestEntryDTO> entries = new ArrayList<>();
}
