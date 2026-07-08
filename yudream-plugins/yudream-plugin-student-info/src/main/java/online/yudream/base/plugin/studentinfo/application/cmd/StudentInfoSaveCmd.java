package online.yudream.base.plugin.studentinfo.application.cmd;

public record StudentInfoSaveCmd(
        String userId,
        String studentNo,
        String className,
        String college
) {
}
