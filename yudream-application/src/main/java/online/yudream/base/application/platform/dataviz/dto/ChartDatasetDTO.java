package online.yudream.base.application.platform.dataviz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.dataviz.enumerate.ChartType;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartDatasetDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private ChartType chartType;
    private String title;
    private String subTitle;
    private List<ChartSeriesDTO> series;
}
