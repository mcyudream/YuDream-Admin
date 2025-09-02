package online.yudream.spring.entity.dto;

public record ParamsDepartmentDto(
        String id,
        String name,
        String parentId,
        String description
) {
}
