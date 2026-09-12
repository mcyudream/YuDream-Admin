package online.yudream.base.application.platform.plugin.cmd;

import lombok.Data;

/** 发布物审核/下架指令；note 为可选备注。 */
@Data
public class PluginMarketPublicationReviewCmd {

    private Long id;
    private String note;
}
