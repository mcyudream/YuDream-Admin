package online.yudream.base.plugin.studentinfo.domain.repo;

import online.yudream.base.plugin.studentinfo.domain.aggregate.StudentInfo;

import java.util.List;
import java.util.Optional;

public interface StudentInfoRepository {

    StudentInfo save(StudentInfo info);

    Optional<StudentInfo> findByUserId(String userId);

    Optional<StudentInfo> findByStudentNo(String studentNo);

    List<StudentInfo> listAll();

    long count();

    void delete(String userId);
}
