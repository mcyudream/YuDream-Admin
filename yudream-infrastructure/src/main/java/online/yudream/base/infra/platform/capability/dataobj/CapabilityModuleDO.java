package online.yudream.base.infra.platform.capability.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformCapability")
public class CapabilityModuleDO extends BaseDO {

    @Indexed(unique = true)
    private String code;
    private String name;
    private CapabilityType type;
    private String description;
    private String icon;
    private Integer sort;
    private Boolean enabled;
    private Map<String, String> config;
    private List<String> dependencies;
}
