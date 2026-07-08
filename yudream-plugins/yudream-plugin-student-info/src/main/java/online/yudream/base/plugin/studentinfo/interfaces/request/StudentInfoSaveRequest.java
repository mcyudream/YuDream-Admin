package online.yudream.base.plugin.studentinfo.interfaces.request;

public record StudentInfoSaveRequest(
        String userId,
        String studentNo,
        String className,
        String college
) {
}
