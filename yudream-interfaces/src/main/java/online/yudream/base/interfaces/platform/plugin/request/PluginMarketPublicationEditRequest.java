package online.yudream.base.interfaces.platform.plugin.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 编辑发布物展示元数据；null 表示保留原值，空集合/空串表示清除。 */
@Data
public class PluginMarketPublicationEditRequest {

    @Size(max = 128, message = "显示名称过长")
    private String displayName;

    @Size(max = 512, message = "描述过长")
    private String description;

    @Size(max = 4096, message = "发布说明过长")
    private String releaseNotes;

    @Size(max = 64, message = "license 过长")
    private String license;

    @Size(max = 32, message = "分类名过长")
    private String category;

    private List<String> tags;

    private Map<String, String> compatibility;
}
