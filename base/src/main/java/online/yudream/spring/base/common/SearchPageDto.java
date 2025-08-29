package online.yudream.spring.base.common;

public record SearchPageDto(
        String keyword,
        int page,
        int size
) {
}
