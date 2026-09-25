package online.yudream.base.infra.system.backup.service;

import online.yudream.base.domain.common.exception.BizException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 归档路径工具：对象键的编码/校验与插件范围相对路径校验。
 * 归档内 ZIP 条目必须可在各文件系统安全落盘，因此对象键逐段百分号编码。
 */
public final class ArchivePaths {

    private static final int MAX_PATH_LENGTH = 512;

    private ArchivePaths() {
    }

    /** 校验对象存储键：禁止空键、反斜杠、穿越段、控制字符与超长。 */
    public static void validateObjectKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BizException("对象键不能为空");
        }
        if (key.length() > MAX_PATH_LENGTH) {
            throw new BizException("对象键过长：" + key.length() + " 字符");
        }
        if (key.startsWith("/") || key.contains("\\") || key.endsWith("/")) {
            throw new BizException("非法对象键：" + key);
        }
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new BizException("非法对象键段：" + key);
            }
        }
        if (key.chars().anyMatch(c -> c < 0x20)) {
            throw new BizException("对象键含控制字符");
        }
    }

    /** 对象键逐段百分号编码为归档内安全路径。 */
    public static String encodeObjectKey(String key) {
        validateObjectKey(key);
        StringBuilder builder = new StringBuilder();
        for (String segment : key.split("/")) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return builder.toString();
    }

    /** 归档内编码路径还原为对象键。 */
    public static String decodeObjectKey(String encoded) {
        StringBuilder builder = new StringBuilder();
        for (String segment : encoded.split("/")) {
            if (!builder.isEmpty()) {
                builder.append('/');
            }
            builder.append(URLDecoder.decode(segment, StandardCharsets.UTF_8));
        }
        return builder.toString();
    }

    /** 校验插件范围相对路径：仅字母数字与 {@code . _ / -}，禁止穿越与反斜杠。 */
    public static void validateRelative(String path) {
        if (path == null || path.isBlank()) {
            throw new BizException("备份文件路径不能为空");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new BizException("备份文件路径过长");
        }
        if (path.startsWith("/") || path.contains("\\") || path.endsWith("/")) {
            throw new BizException("非法备份文件路径：" + path);
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new BizException("非法备份文件路径段：" + path);
            }
            if (!segment.matches("[A-Za-z0-9._-]+")) {
                throw new BizException("备份文件路径含非法字符：" + path);
            }
        }
    }

    /** 校验 Mongo 集合名（本系统集合命名均为字母数字）。 */
    public static void validateCollection(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,64}")) {
            throw new BizException("非法的备份集合名：" + name);
        }
    }
}
