package online.yudream.base.application.platform.cms.cmd;

import lombok.Data;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class HomePageLayoutSaveCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title;
    private String subtitle;
    private String theme;
    private String heroImageUrl;
    private Map<String, String> settings = new HashMap<>();
    private List<HomeSection> sections = new ArrayList<>();
    private Boolean published;
}
