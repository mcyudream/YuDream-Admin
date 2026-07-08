package online.yudream.base.plugin.studentinfo.application.dto;

public record StudentInfoDTO(
        String userId,
        String studentName,
        String studentNo,
        String className,
        String college,
        long createdAt,
        long updatedAt
) {
}
