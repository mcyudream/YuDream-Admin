package online.yudream.base.application.platform.workspace.service;

import online.yudream.base.domain.common.exception.BizException;

import java.util.Arrays;

/**
 * 工作区路径规则（界面端点与 AI 工具共用）：相对路径、正斜杠分隔、无 ..、无控制字符，限 240 字。
 * 目录以去掉结尾斜杠的形式表示，根目录为空串；逻辑地址为 workspace://&lt;工作区 id&gt;/&lt;相对路径&gt;。
 */
public final class WorkspacePaths {

    private static final int MAX_LENGTH = 240;

    private WorkspacePaths() {
    }

    /** 文件路径：必须非空且不以 / 结尾 */
    public static String file(Object raw) {
        String path = clean(raw);
        if (path.isEmpty() || path.endsWith("/")) {
            throw invalid();
        }
        return path;
    }

    /** 目录路径：允许空串（根目录），去掉结尾斜杠 */
    public static String directory(Object raw) {
        String path = clean(raw);
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** 文件名：不含斜杠 */
    public static String name(Object raw) {
        String name = raw == null ? "" : String.valueOf(raw).trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || ".".equals(name) || "..".equals(name)
                || name.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new BizException("文件名无效：" + name);
        }
        return name;
    }

    public static String join(String directory, String name) {
        return directory == null || directory.isEmpty() ? name : directory + "/" + name;
    }

    /** 父目录路径，根下条目返回空串 */
    public static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    public static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** path 等于 directory 或位于其下；directory 为空串表示根 */
    public static boolean within(String path, String directory) {
        return directory.isEmpty() || path.equals(directory) || path.startsWith(directory + "/");
    }

    private static String clean(Object raw) {
        String path = raw == null ? "" : String.valueOf(raw).trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() > MAX_LENGTH || path.contains("\\") || path.contains("//")
                || Arrays.asList(path.split("/")).contains("..") || Arrays.asList(path.split("/")).contains(".")
                || path.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw invalid();
        }
        return path;
    }

    private static BizException invalid() {
        return new BizException("工作区路径无效：使用不含 .. 的相对路径，如 notes/plan.md");
    }
}
