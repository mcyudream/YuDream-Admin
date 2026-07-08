package online.yudream.base.plugin.studentinfo.interfaces.assembler;

import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.studentinfo.application.cmd.StudentInfoSaveCmd;
import online.yudream.base.plugin.studentinfo.application.dto.StudentInfoDTO;
import online.yudream.base.plugin.studentinfo.interfaces.request.StudentInfoSaveRequest;
import online.yudream.base.plugin.studentinfo.interfaces.res.StudentInfoRes;

public class StudentInfoWebAssembler {

    public StudentInfoSaveCmd toCmd(StudentInfoSaveRequest request, String userId) {
        StudentInfoSaveRequest safeRequest = request == null
                ? new StudentInfoSaveRequest(null, null, null, null, null)
                : request;
        return new StudentInfoSaveCmd(
                userId,
                safeRequest.studentName(),
                safeRequest.studentNo(),
                safeRequest.className(),
                safeRequest.college()
        );
    }

    public StudentInfoSaveCmd toAdminCmd(StudentInfoSaveRequest request) {
        StudentInfoSaveRequest safeRequest = request == null
                ? new StudentInfoSaveRequest(null, null, null, null, null)
                : request;
        return new StudentInfoSaveCmd(
                safeRequest.userId(),
                safeRequest.studentName(),
                safeRequest.studentNo(),
                safeRequest.className(),
                safeRequest.college()
        );
    }

    public StudentInfoRes toRes(StudentInfoDTO dto, PluginUserProfile user) {
        return new StudentInfoRes(
                dto.userId(),
                username(user),
                nickname(user),
                email(user),
                dto.studentName(),
                dto.studentNo(),
                dto.className(),
                dto.college(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    public StudentInfoRes empty(String userId, PluginUserProfile user) {
        return new StudentInfoRes(
                userId,
                username(user),
                nickname(user),
                email(user),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String username(PluginUserProfile user) {
        return user == null ? null : user.username();
    }

    private String nickname(PluginUserProfile user) {
        return user == null ? null : user.nickname();
    }

    private String email(PluginUserProfile user) {
        return user == null ? null : user.email();
    }
}
