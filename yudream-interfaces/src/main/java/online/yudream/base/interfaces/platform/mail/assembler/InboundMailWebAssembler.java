package online.yudream.base.interfaces.platform.mail.assembler;

import online.yudream.base.application.platform.mail.dto.InboundMailAttachmentDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailDetailDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailSummaryDTO;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.interfaces.platform.mail.res.InboundMailAttachmentRes;
import online.yudream.base.interfaces.platform.mail.res.InboundMailDetailRes;
import online.yudream.base.interfaces.platform.mail.res.InboundMailSummaryRes;

import java.util.List;

public class InboundMailWebAssembler {

    private InboundMailWebAssembler() {
    }

    public static PageResult<InboundMailSummaryRes> toPage(PageResult<InboundMailSummaryDTO> page) {
        return new PageResult<>(
                page.getRecords().stream().map(InboundMailWebAssembler::toRes).toList(),
                page.getTotal(),
                page.getPage(),
                page.getSize()
        );
    }

    public static InboundMailSummaryRes toRes(InboundMailSummaryDTO dto) {
        if (dto == null) {
            return null;
        }
        return InboundMailSummaryRes.builder()
                .uid(dto.getUid())
                .subject(dto.getSubject())
                .from(dto.getFrom())
                .to(dto.getTo())
                .receivedAt(dto.getReceivedAt())
                .seen(dto.getSeen())
                .hasAttachment(dto.getHasAttachment())
                .build();
    }

    public static InboundMailDetailRes toRes(InboundMailDetailDTO dto) {
        if (dto == null) {
            return null;
        }
        return InboundMailDetailRes.builder()
                .uid(dto.getUid())
                .subject(dto.getSubject())
                .from(dto.getFrom())
                .to(dto.getTo())
                .cc(dto.getCc())
                .receivedAt(dto.getReceivedAt())
                .seen(dto.getSeen())
                .textBody(dto.getTextBody())
                .htmlBody(dto.getHtmlBody())
                .attachments(dto.getAttachments() == null
                        ? List.of()
                        : dto.getAttachments().stream().map(InboundMailWebAssembler::toRes).toList())
                .build();
    }

    public static InboundMailAttachmentRes toRes(InboundMailAttachmentDTO dto) {
        if (dto == null) {
            return null;
        }
        return InboundMailAttachmentRes.builder()
                .filename(dto.getFilename())
                .contentType(dto.getContentType())
                .size(dto.getSize())
                .inline(dto.getInline())
                .build();
    }
}
