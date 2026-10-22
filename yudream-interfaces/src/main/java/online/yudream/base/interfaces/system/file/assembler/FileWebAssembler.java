package online.yudream.base.interfaces.system.file.assembler;

import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.system.file.res.FileObjectRes;

public class FileWebAssembler {

    private FileWebAssembler() {
    }

    public static FileObjectRes toRes(FileObjectDTO dto) {
        return FileObjectRes.builder()
                .id(dto.getId())
                .originalName(dto.getOriginalName())
                .contentType(dto.getContentType())
                .size(dto.getSize())
                .module(dto.getModule())
                .url(dto.getUrl())
                .createTime(dto.getCreateTime())
                .build();
    }

    public static PageResult<FileObjectRes> toPage(PageResult<FileObjectDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(FileWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    /**
     * 解析 HTTP Range 头（只支持单段 bytes=a-b / a- / -n）；多段或无法解析时返回 null（按整文件应答）。
     */
    public static online.yudream.base.application.system.file.dto.FileByteRange byteRange(String header) {
        if (header == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*bytes=(\\d*)-(\\d*)\\s*$").matcher(header);
        if (!matcher.matches() || (matcher.group(1).isEmpty() && matcher.group(2).isEmpty())) {
            return null;
        }
        try {
            Long first = matcher.group(1).isEmpty() ? null : Long.parseLong(matcher.group(1));
            Long last = matcher.group(2).isEmpty() ? null : Long.parseLong(matcher.group(2));
            return new online.yudream.base.application.system.file.dto.FileByteRange(first, last);
        } catch (NumberFormatException overflow) {
            return null;
        }
    }
}
