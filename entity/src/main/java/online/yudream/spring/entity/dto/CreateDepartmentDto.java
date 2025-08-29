package online.yudream.spring.entity.dto;

public record CreateDepartmentDto(
        String name,
        String parentId,
        String description
) {
}
