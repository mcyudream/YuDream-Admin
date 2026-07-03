package online.yudream.base.interfaces.platform.cms.request;

import lombok.Data;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HomePageLayoutSaveRequest {
    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings = new HashMap<>();
    private List<HomeSection> sections = new ArrayList<>();
    private Boolean published;
}
