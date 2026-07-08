package online.yudream.base.plugin.studentinfo.interfaces.res;

public record StudentInfoRes(
        String userId,
        String username,
        String nickname,
        String email,
        String studentNo,
        String className,
        String college,
        Long createdAt,
        Long updatedAt
) {
}
