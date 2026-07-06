package online.yudream.base.application.system.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCardDTO {
    private String code;
    private String title;
    private String description;
    private String icon;
    private String category;
    private String source;
    private String pluginCode;
    private String permission;
    private String component;
    private String actionPath;
    private String dragPayloadTemplate;
    private String tone;
    private int defaultW;
    private int defaultH;
    private int minW;
    private int minH;
    private int sort;
}
