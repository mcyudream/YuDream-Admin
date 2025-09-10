package online.yudream.spring.base.common;

import java.util.Map;

public record SearchPageDto(
        Map<String, String> keywords,
        int page,
        int size,
        String extraId
) {
}
