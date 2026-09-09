package online.yudream.base.application.platform.mail.assembler;

import online.yudream.base.application.platform.mail.dto.InboundMailAttachmentDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailDetailDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailSummaryDTO;
import online.yudream.base.domain.platform.mail.valobj.InboundMailAttachment;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;

public class InboundMailAssembler {

    private InboundMailAssembler() {
    }

    public static InboundMailSummaryDTO toDTO(InboundMailSummary summary) {
        if (summary == null) {
            return null;
        }
        return InboundMailSummaryDTO.builder()
                .uid(summary.uid())
                .subject(summary.subject())
                .from(summary.from())
                .to(summary.to())
                .receivedAt(summary.receivedAt())
                .seen(summary.seen())
                .hasAttachment(summary.hasAttachment())
                .build();
    }

    public static InboundMailDetailDTO toDTO(InboundMailDetail detail) {
        if (detail == null) {
            return null;
        }
        return InboundMailDetailDTO.builder()
                .uid(detail.uid())
                .subject(detail.subject())
                .from(detail.from())
                .to(detail.to())
                .cc(detail.cc())
                .receivedAt(detail.receivedAt())
                .seen(detail.seen())
                .textBody(detail.textBody())
                .htmlBody(detail.htmlBody())
                .attachments(detail.attachments().stream().map(InboundMailAssembler::toDTO).toList())
                .build();
    }

    public static InboundMailAttachmentDTO toDTO(InboundMailAttachment attachment) {
        if (attachment == null) {
            return null;
        }
        return InboundMailAttachmentDTO.builder()
                .filename(attachment.filename())
                .contentType(attachment.contentType())
                .size(attachment.size())
                .inline(attachment.inline())
                .build();
    }
}
