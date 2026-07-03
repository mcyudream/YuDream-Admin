package online.yudream.base.interfaces.platform.document.assembler;

import online.yudream.base.application.platform.document.cmd.WordGenerateCmd;
import online.yudream.base.application.platform.document.cmd.WordTemplateSaveCmd;
import online.yudream.base.application.platform.document.dto.WordGenerationRecordDTO;
import online.yudream.base.application.platform.document.dto.WordTemplateDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.document.request.WordGenerateRequest;
import online.yudream.base.interfaces.platform.document.request.WordTemplateSaveRequest;
import online.yudream.base.interfaces.platform.document.res.WordGenerationRecordRes;
import online.yudream.base.interfaces.platform.document.res.WordTemplateRes;

public class WordDocumentWebAssembler {

    private WordDocumentWebAssembler() {
    }

    public static WordTemplateSaveCmd toCmd(WordTemplateSaveRequest request) {
        WordTemplateSaveCmd cmd = new WordTemplateSaveCmd();
        cmd.setName(request.getName());
        cmd.setCode(request.getCode());
        cmd.setPlaceholders(request.getPlaceholders());
        cmd.setDescription(request.getDescription());
        cmd.setStatus(request.getStatus());
        return cmd;
    }

    public static WordTemplateSaveCmd toCmd(Long id, WordTemplateSaveRequest request) {
        WordTemplateSaveCmd cmd = toCmd(request);
        cmd.setId(id);
        return cmd;
    }

    public static WordGenerateCmd toCmd(Long templateId, WordGenerateRequest request, Long operatorId) {
        WordGenerateCmd cmd = new WordGenerateCmd();
        cmd.setTemplateId(templateId);
        cmd.setData(request.getData());
        cmd.setOperatorId(operatorId);
        return cmd;
    }

    public static PageResult<WordTemplateRes> toTemplatePage(PageResult<WordTemplateDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(WordDocumentWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static PageResult<WordGenerationRecordRes> toRecordPage(PageResult<WordGenerationRecordDTO> page) {
        return new PageResult<>(page.getRecords().stream().map(WordDocumentWebAssembler::toRes).toList(),
                page.getTotal(), page.getPage(), page.getSize());
    }

    public static WordTemplateRes toRes(WordTemplateDTO dto) {
        return WordTemplateRes.builder()
                .id(dto.getId())
                .name(dto.getName())
                .code(dto.getCode())
                .templateFileId(dto.getTemplateFileId())
                .templateFileUrl(dto.getTemplateFileUrl())
                .originalFilename(dto.getOriginalFilename())
                .placeholders(dto.getPlaceholders())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .createTime(dto.getCreateTime())
                .updateTime(dto.getUpdateTime())
                .build();
    }

    public static WordGenerationRecordRes toRes(WordGenerationRecordDTO dto) {
        return WordGenerationRecordRes.builder()
                .id(dto.getId())
                .templateId(dto.getTemplateId())
                .templateCode(dto.getTemplateCode())
                .outputFileId(dto.getOutputFileId())
                .outputFileUrl(dto.getOutputFileUrl())
                .outputFilename(dto.getOutputFilename())
                .data(dto.getData())
                .status(dto.getStatus())
                .errorMessage(dto.getErrorMessage())
                .operatorId(dto.getOperatorId())
                .generatedAt(dto.getGeneratedAt())
                .build();
    }
}
