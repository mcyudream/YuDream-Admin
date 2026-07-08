package online.yudream.base.plugin.studentinfo.application.cmd;

public record StudentInfoSaveCmd(
        String userId,
        String studentName,
        String studentNo,
        String className,
        String college
) {
}
