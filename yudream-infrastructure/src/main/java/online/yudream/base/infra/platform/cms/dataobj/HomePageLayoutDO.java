package online.yudream.base.infra.platform.cms.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "platformHomePageLayout")
public class HomePageLayoutDO extends BaseDO {
    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings = new HashMap<>();
    private List<HomeSection> sections = new ArrayList<>();
    private Boolean published;
}
