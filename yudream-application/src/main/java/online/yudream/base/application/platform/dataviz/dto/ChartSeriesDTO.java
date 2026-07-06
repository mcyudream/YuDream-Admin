package online.yudream.base.application.platform.dataviz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartSeriesDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private List<String> categories;
    private List<Number> values;
    private List<ChartNodeDTO> nodes;
    private List<ChartLinkDTO> links;
}
