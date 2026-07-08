package online.yudream.base.plugin.spi.system.studentinfo;

import java.util.List;
import java.util.Optional;

public interface PluginStudentInfoService {

    Optional<PluginStudentInfoProfile> findStudentInfoByUserId(String userId);

    Optional<PluginStudentInfoProfile> findStudentInfoByStudentNo(String studentNo);

    List<PluginStudentInfoProfile> studentInfos(String keyword, int page, int size);
}
