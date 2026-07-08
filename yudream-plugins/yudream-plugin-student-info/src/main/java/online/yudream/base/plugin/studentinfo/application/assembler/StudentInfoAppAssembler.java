package online.yudream.base.plugin.studentinfo.application.assembler;

import online.yudream.base.plugin.studentinfo.application.dto.StudentInfoDTO;
import online.yudream.base.plugin.studentinfo.domain.aggregate.StudentInfo;

public class StudentInfoAppAssembler {

    public StudentInfoDTO toDTO(StudentInfo info) {
        return new StudentInfoDTO(
                info.userId(),
                info.studentName(),
                info.studentNo(),
                info.className(),
                info.college(),
                info.createdAt(),
                info.updatedAt()
        );
    }
}
