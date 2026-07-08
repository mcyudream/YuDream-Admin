package online.yudream.base.plugin.studentinfo.infrastructure.repository;

import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import online.yudream.base.plugin.studentinfo.domain.aggregate.StudentInfo;
import online.yudream.base.plugin.studentinfo.domain.repo.StudentInfoRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StudentInfoDocumentRepository implements StudentInfoRepository {

    private static final int SCAN_PAGE_SIZE = 200;
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
        List<StudentInfo> result = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            List<StudentInfo> batch = documents.findAll(PROFILES, page, SCAN_PAGE_SIZE).stream()
                    .map(this::toProfile)
                    .toList();
            result.addAll(batch);
            if (batch.size() < SCAN_PAGE_SIZE) {
                return result;
            }
            page++;
        }
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
        document.put("studentName", info.studentName());
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
                string(document, "studentName", "name"),
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
