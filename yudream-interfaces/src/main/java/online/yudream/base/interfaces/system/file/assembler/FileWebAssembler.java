package online.yudream.base.interfaces.system.file.assembler;

import online.yudream.base.application.system.file.dto.FileObjectDTO;
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
}
