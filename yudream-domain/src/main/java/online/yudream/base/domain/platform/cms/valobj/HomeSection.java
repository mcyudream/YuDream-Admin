package online.yudream.base.domain.platform.cms.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.cms.enumerate.HomeSectionType;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeSection implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private HomeSectionType type;
    private String title;
    private String subtitle;
    private String mediaUrl;
    private String actionText;
    private String actionUrl;
    private Map<String, String> settings = new HashMap<>();
    private Integer sort;
    private Boolean visible;
}
