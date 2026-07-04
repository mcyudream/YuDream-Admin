package online.yudream.base.application.platform.document.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.document.assembler.WordDocumentAssembler;
import online.yudream.base.application.platform.document.cmd.WordGenerateCmd;
import online.yudream.base.application.platform.document.cmd.WordTemplateSaveCmd;
import online.yudream.base.application.platform.document.dto.WordGenerationRecordDTO;
import online.yudream.base.application.platform.document.dto.WordTemplateDTO;
import online.yudream.base.application.platform.document.query.WordDocumentPageQuery;
import online.yudream.base.application.system.file.dto.FileContentDTO;
import online.yudream.base.application.system.file.dto.FileObjectDTO;
import online.yudream.base.application.system.file.service.FileAppService;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.document.aggregate.WordGenerationRecord;
import online.yudream.base.domain.platform.document.aggregate.WordTemplate;
import online.yudream.base.domain.platform.document.enumerate.TemplateStatus;
import online.yudream.base.domain.platform.document.repo.WordGenerationRecordRepo;
import online.yudream.base.domain.platform.document.repo.WordTemplateRepo;
import online.yudream.base.domain.platform.document.service.WordTemplateRenderer;
import online.yudream.base.domain.platform.document.valobj.RenderedDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class WordDocumentAppService {

    private static final String CAPABILITY_CODE = "document-template";
    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final CapabilityModuleRepo capabilityModuleRepo;
    private final WordTemplateRepo wordTemplateRepo;
    private final WordGenerationRecordRepo wordGenerationRecordRepo;
    private final WordTemplateRenderer wordTemplateRenderer;
    private final FileAppService fileAppService;

    @Transactional(readOnly = true)
    public PageResult<WordTemplateDTO> pageTemplates(WordDocumentPageQuery query) {
        ensureEnabled();
        PageResult<WordTemplate> page = wordTemplateRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(
                page.getRecords().stream().map(template -> WordDocumentAssembler.toDTO(template, fileAppService::fileUrl)).toList(),
                page.getTotal(),
                page.getPage(),
                page.getSize()
        );
    }

    @Transactional
    public WordTemplateDTO uploadTemplate(InputStream inputStream, String originalName, String contentType,
                                          long size, WordTemplateSaveCmd cmd, Long operatorId) {
        ensureEnabled();
        validateDocxTemplateFile(originalName, contentType);
        if (wordTemplateRepo.findByCode(cmd.getCode()).isPresent()) {
            throw new BizException("模板编码已存在");
        }
        FileObjectDTO file = fileAppService.upload(inputStream, originalName, contentType, size, "word-template", operatorId, false);
        WordTemplate template = WordTemplate.create(cmd.getName(), cmd.getCode(), file.getId(), originalName);
        template.update(cmd.getName(), cmd.getPlaceholders(), cmd.getDescription(), cmd.getStatus());
        return WordDocumentAssembler.toDTO(wordTemplateRepo.save(template), fileAppService::fileUrl);
    }

    @Transactional
    public WordTemplateDTO updateTemplate(WordTemplateSaveCmd cmd) {
        ensureEnabled();
        WordTemplate template = template(cmd.getId());
        template.update(cmd.getName(), cmd.getPlaceholders(), cmd.getDescription(), cmd.getStatus());
        return WordDocumentAssembler.toDTO(wordTemplateRepo.save(template), fileAppService::fileUrl);
    }

    @Transactional
    public WordTemplateDTO replaceTemplateFile(Long id, InputStream inputStream, String originalName, String contentType,
                                               long size, Long operatorId) {
        ensureEnabled();
        validateDocxTemplateFile(originalName, contentType);
        WordTemplate template = template(id);
        FileObjectDTO file = fileAppService.upload(inputStream, originalName, contentType, size, "word-template", operatorId, false);
        template.replaceFile(file.getId(), originalName);
        return WordDocumentAssembler.toDTO(wordTemplateRepo.save(template), fileAppService::fileUrl);
    }

    @Transactional
    public void disableTemplate(Long id) {
        ensureEnabled();
        WordTemplate template = template(id);
        template.disable();
        wordTemplateRepo.save(template);
    }

    @Transactional
    public WordGenerationRecordDTO generate(WordGenerateCmd cmd) {
        ensureEnabled();
        WordTemplate template = template(cmd.getTemplateId());
        if (template.getStatus() != TemplateStatus.ACTIVE) {
            throw new BizException("Word 模板已停用");
        }
        try {
            FileContentDTO content = fileAppService.content(template.getTemplateFileId());
            RenderedDocument rendered = wordTemplateRenderer.render(content.getInputStream(), cmd.getData());
            String filename = buildOutputFilename(template);
            FileObjectDTO output = fileAppService.upload(
                    new ByteArrayInputStream(rendered.content()),
                    filename,
                    rendered.contentType() == null ? DOCX_CONTENT_TYPE : rendered.contentType(),
                    rendered.content().length,
                    "word-generated",
                    cmd.getOperatorId(),
                    false
            );
            WordGenerationRecord record = WordGenerationRecord.success(
                    template.getId(), template.getCode(), output.getId(), filename, cmd.getData(), cmd.getOperatorId());
            return WordDocumentAssembler.toDTO(wordGenerationRecordRepo.save(record), fileAppService::fileUrl);
        }
        catch (Exception e) {
            WordGenerationRecord record = WordGenerationRecord.failed(
                    template.getId(), template.getCode(), cmd.getData(), e.getMessage(), cmd.getOperatorId());
            wordGenerationRecordRepo.save(record);
            throw new BizException("Word 模板生成失败：" + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResult<WordGenerationRecordDTO> pageRecords(WordDocumentPageQuery query) {
        ensureEnabled();
        PageResult<WordGenerationRecord> page = wordGenerationRecordRepo.page(query.getKeyword(), query.getPage(), query.getSize());
        return new PageResult<>(
                page.getRecords().stream().map(record -> WordDocumentAssembler.toDTO(record, fileAppService::fileUrl)).toList(),
                page.getTotal(),
                page.getPage(),
                page.getSize()
        );
    }

    private WordTemplate template(Long id) {
        return wordTemplateRepo.findById(id).orElseThrow(() -> new BizException("Word 模板不存在"));
    }

    private void ensureEnabled() {
        boolean enabled = capabilityModuleRepo.findByCode(CAPABILITY_CODE)
                .map(module -> Boolean.TRUE.equals(module.getEnabled()))
                .orElse(false);
        if (!enabled) {
            throw new BizException("Word 模板能力未启用");
        }
    }

    private void validateDocxTemplateFile(String originalName, String contentType) {
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new BizException("Word 模板文件必须是 .docx 格式");
        }
        if (contentType == null || contentType.isBlank()) {
            return;
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (!DOCX_CONTENT_TYPE.equals(normalized) && !"application/octet-stream".equals(normalized)) {
            throw new BizException("Word 模板文件类型不正确");
        }
    }

    private String buildOutputFilename(WordTemplate template) {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        return template.getCode() + "-" + timestamp + ".docx";
    }
}
