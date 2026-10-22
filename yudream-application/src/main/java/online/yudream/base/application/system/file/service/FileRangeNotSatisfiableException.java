package online.yudream.base.application.system.file.service;

import lombok.Getter;
import online.yudream.base.domain.common.exception.BizException;

/** 请求的字节范围超出文件（HTTP 416），携带文件总长度供应答 Content-Range: bytes *&#47;size */
@Getter
public class FileRangeNotSatisfiableException extends BizException {

    private final long size;

    public FileRangeNotSatisfiableException(long size) {
        super("请求的范围超出文件大小");
        this.size = size;
    }
}
