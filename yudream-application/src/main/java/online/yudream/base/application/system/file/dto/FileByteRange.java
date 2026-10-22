package online.yudream.base.application.system.file.dto;

/**
 * 请求的字节范围（HTTP Range 的单段形式）：first 为空表示「最后 last 个字节」；last 为空表示到文件末尾。
 */
public record FileByteRange(Long first, Long last) {

    /** 按文件大小解析为闭区间 [start, end]；超出文件或非法时返回 null（应答 416） */
    public long[] resolve(long size) {
        if (size <= 0) {
            return null;
        }
        long start;
        long end;
        if (first == null) {
            if (last == null || last <= 0) {
                return null;
            }
            start = Math.max(0, size - last);
            end = size - 1;
        } else {
            start = first;
            end = last == null ? size - 1 : Math.min(last, size - 1);
        }
        return start < 0 || start >= size || end < start ? null : new long[] {start, end};
    }
}
