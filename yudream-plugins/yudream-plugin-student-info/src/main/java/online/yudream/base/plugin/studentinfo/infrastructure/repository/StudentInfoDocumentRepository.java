package online.yudream.base.plugin.studentinfo.infrastructure.repository;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.studentinfo.domain.aggregate.StudentInfo;
import online.yudream.base.plugin.studentinfo.domain.repo.StudentInfoRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StudentInfoDocumentRepository implements StudentInfoRepository {

    private static final String PROFILES = "profiles";

    private final PluginDocumentStore documents;

    public StudentInfoDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public StudentInfo save(StudentInfo info) {
        return toProfile(documents.save(PROFILES, info.userId(), profileDocument(info)));
    }

    @Override
    public Optional<StudentInfo> findByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return documents.findById(PROFILES, userId.trim()).map(this::toProfile);
    }

    @Override
    public Optional<StudentInfo> findByStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isBlank()) {
            return Optional.empty();
        }
        return documents.findByField(PROFILES, "studentNo", studentNo.trim(), 1, 1).stream()
                .findFirst()
                .map(this::toProfile);
    }

    @Override
    public List<StudentInfo> listAll() {
        long total = count();
        if (total <= 0) {
            return List.of();
        }
        return documents.findAll(PROFILES, 1, (int) Math.min(total, Integer.MAX_VALUE)).stream()
                .map(this::toProfile)
                .toList();
    }

    @Override
    public long count() {
        return documents.count(PROFILES);
    }

    @Override
    public void delete(String userId) {
        documents.delete(PROFILES, userId);
    }

    private Map<String, Object> profileDocument(StudentInfo info) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("userId", info.userId());
        document.put("studentNo", info.studentNo());
        document.put("className", info.className());
        document.put("college", info.college());
        document.put("createdAt", info.createdAt());
        document.put("updatedAt", info.updatedAt());
        return document;
    }

    private StudentInfo toProfile(Map<String, Object> document) {
        return new StudentInfo(
                string(document, "userId", "id"),
                string(document, "studentNo"),
                string(document, "className"),
                string(document, "college"),
                number(document, "createdAt", 0L),
                number(document, "updatedAt", 0L)
        );
    }

    private String string(Map<String, Object> document, String key) {
        Object value = document.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private String string(Map<String, Object> document, String key, String fallbackKey) {
        String value = string(document, key);
        return value == null ? string(document, fallbackKey) : value;
    }

    private Long number(Map<String, Object> document, String key, Long defaultValue) {
        Object value = document.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null || String.valueOf(value).isBlank() ? defaultValue : Long.parseLong(String.valueOf(value));
    }
}
