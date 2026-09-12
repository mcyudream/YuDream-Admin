package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 编辑发布物展示元数据；null 表示保留原值，空集合/空串表示清除。 */
@Data
public class PluginMarketPublicationEditCmd {

    private Long id;
    private String displayName;
    private String description;
    private String releaseNotes;
    private String license;
    private String category;
    private List<String> tags;
    private Map<String, String> compatibility;
}
