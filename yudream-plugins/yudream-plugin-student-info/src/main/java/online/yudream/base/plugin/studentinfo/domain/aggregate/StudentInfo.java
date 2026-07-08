package online.yudream.base.plugin.studentinfo.domain.aggregate;

import java.util.Locale;

public record StudentInfo(
        String userId,
        String studentNo,
        String className,
        String college,
        long createdAt,
        long updatedAt
) {

    private static final int USER_ID_MAX_LENGTH = 64;
    private static final int STUDENT_NO_MAX_LENGTH = 64;
    private static final int CLASS_NAME_MAX_LENGTH = 80;
    private static final int COLLEGE_MAX_LENGTH = 80;

    public StudentInfo {
        userId = requireText(userId, "用户不能为空");
        studentNo = requireText(studentNo, "学号不能为空");
        className = requireText(className, "班级不能为空");
        college = requireText(college, "学院不能为空");
        requireMax(userId, USER_ID_MAX_LENGTH, "用户 ID");
        requireMax(studentNo, STUDENT_NO_MAX_LENGTH, "学号");
        requireMax(className, CLASS_NAME_MAX_LENGTH, "班级");
        requireMax(college, COLLEGE_MAX_LENGTH, "学院");
        long now = System.currentTimeMillis();
        if (createdAt <= 0) {
            createdAt = now;
        }
        if (updatedAt <= 0) {
            updatedAt = createdAt;
        }
    }

    public static StudentInfo create(String userId, String studentNo, String className, String college) {
        long now = System.currentTimeMillis();
        return new StudentInfo(userId, studentNo, className, college, now, now);
    }

    public StudentInfo update(String studentNo, String className, String college) {
        return new StudentInfo(userId, studentNo, className, college, createdAt, System.currentTimeMillis());
    }

    public boolean matches(String keyword, String collegeFilter, String classNameFilter) {
        return containsAny(keyword)
                && contains(college, collegeFilter)
                && contains(className, classNameFilter);
    }

    private boolean containsAny(String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return userId.toLowerCase(Locale.ROOT).contains(value)
                || studentNo.toLowerCase(Locale.ROOT).contains(value)
                || className.toLowerCase(Locale.ROOT).contains(value)
                || college.toLowerCase(Locale.ROOT).contains(value);
    }

    private boolean contains(String source, String expected) {
        if (!hasText(expected)) {
            return true;
        }
        return source.toLowerCase(Locale.ROOT).contains(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireMax(String value, int maxLength, String name) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + "不能超过 " + maxLength + " 个字符");
        }
    }
}
