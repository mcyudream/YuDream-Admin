package online.yudream.base.plugin.studentinfo.application.service;

import online.yudream.base.plugin.spi.system.studentinfo.PluginStudentInfoProfile;
import online.yudream.base.plugin.spi.system.studentinfo.PluginStudentInfoService;
import online.yudream.base.plugin.studentinfo.application.assembler.StudentInfoAppAssembler;
import online.yudream.base.plugin.studentinfo.application.cmd.StudentInfoSaveCmd;
import online.yudream.base.plugin.studentinfo.application.dto.StudentInfoDTO;
import online.yudream.base.plugin.studentinfo.application.query.StudentInfoQuery;
import online.yudream.base.plugin.studentinfo.domain.aggregate.StudentInfo;
import online.yudream.base.plugin.studentinfo.domain.repo.StudentInfoRepository;

import java.util.List;
import java.util.Optional;

public class StudentInfoAppService implements PluginStudentInfoService {

    private final StudentInfoRepository repository;
    private final StudentInfoAppAssembler assembler = new StudentInfoAppAssembler();

    public StudentInfoAppService(StudentInfoRepository repository) {
        this.repository = repository;
    }

    public StudentInfoDTO save(StudentInfoSaveCmd cmd) {
        String userId = requireText(cmd.userId(), "用户不能为空");
        StudentInfo existing = repository.findByUserId(userId).orElse(null);
        StudentInfo candidate = existing == null
                ? StudentInfo.create(userId, cmd.studentName(), cmd.studentNo(), cmd.className(), cmd.college())
                : existing.update(cmd.studentName(), cmd.studentNo(), cmd.className(), cmd.college());
        repository.findByStudentNo(candidate.studentNo())
                .filter(item -> !item.userId().equals(candidate.userId()))
                .ifPresent(item -> {
                    throw new IllegalArgumentException("学号已被其他用户使用：" + candidate.studentNo());
                });
        return assembler.toDTO(repository.save(candidate));
    }

    public Optional<StudentInfoDTO> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByUserId(userId.trim()).map(assembler::toDTO);
    }

    public Optional<StudentInfoDTO> findByStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        return repository.findByStudentNo(studentNo.trim()).map(assembler::toDTO);
    }

    public List<StudentInfoDTO> list(StudentInfoQuery query) {
        StudentInfoQuery safeQuery = query == null
                ? new StudentInfoQuery(null, null, null, 1, 20)
                : query;
        return repository.listAll().stream()
                .filter(item -> item.matches(safeQuery.keyword(), safeQuery.college(), safeQuery.className()))
                .skip((long) (safeQuery.safePage() - 1) * safeQuery.safeSize())
                .limit(safeQuery.safeSize())
                .map(assembler::toDTO)
                .toList();
    }

    public long count() {
        return repository.count();
    }

    public void delete(String userId) {
        repository.delete(requireText(userId, "用户不能为空"));
    }

    @Override
    public Optional<PluginStudentInfoProfile> findStudentInfoByUserId(String userId) {
        return findByUserId(userId).map(this::toPluginProfile);
    }

    @Override
    public Optional<PluginStudentInfoProfile> findStudentInfoByStudentNo(String studentNo) {
        return findByStudentNo(studentNo).map(this::toPluginProfile);
    }

    @Override
    public List<PluginStudentInfoProfile> studentInfos(String keyword, int page, int size) {
        return list(new StudentInfoQuery(keyword, null, null, page, size)).stream()
                .map(this::toPluginProfile)
                .toList();
    }

    private PluginStudentInfoProfile toPluginProfile(StudentInfoDTO dto) {
        return new PluginStudentInfoProfile(
                dto.userId(),
                dto.studentName(),
                dto.studentNo(),
                dto.className(),
                dto.college(),
                dto.createdAt(),
                dto.updatedAt()
        );
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
