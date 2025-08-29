package online.yudream.spring.entity.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import online.yudream.spring.entity.entity.route.RouteMeta;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouteVo {
    private String name;
    private String path;
    private String component; // 字符串，前端懒加载
    private String redirect;
    private RouteMeta meta;
    private List<RouteVo> children;
}