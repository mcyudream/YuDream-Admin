package online.yudream.spring.entity.vo;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class TreeVo {
    private String title;
    private String description;
    private String key;
    private List<TreeVo> children;
}
