package online.yudream.base.application.platform.cms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import online.yudream.base.domain.platform.cms.valobj.HomeSection;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 插件主题自带首页方案的 JSON 载荷。所有字段可空：
 * 未声明的字段与 settings 键在导入时保留站点现状，已声明的覆盖。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginHomePresetPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String title;
    private String subtitle;
    private String heroImageUrl;
    private Map<String, String> settings;
    private List<HomeSection> sections;
}
