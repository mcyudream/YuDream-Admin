package online.yudream.base.application.platform.mail.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mail.assembler.InboundMailAssembler;
import online.yudream.base.application.platform.mail.dto.InboundMailDetailDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailSummaryDTO;
import online.yudream.base.application.platform.mail.query.InboundMailPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mail.service.InboundMailboxGateway;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InboundMailAppService {

    static final String CAPABILITY_CODE = "inbound-mail";
    static final String CAPABILITY_NAME = "入站邮箱";
    private static final int MAX_PAGE_SIZE = 50;

    private final CapabilityAppService capabilityAppService;
    private final InboundMailboxGateway inboundMailboxGateway;

    @Transactional(readOnly = true)
    public PageResult<InboundMailSummaryDTO> page(InboundMailPageQuery query) {
        ensureEnabled();
        int page = query == null ? 1 : Math.max(query.getPage(), 1);
        int size = query == null ? 10 : Math.min(Math.max(query.getSize(), 1), MAX_PAGE_SIZE);
        String keyword = query == null ? null : query.getKeyword();
        PageResult<InboundMailSummary> result = inboundMailboxGateway.page(page, size, keyword);
        return new PageResult<>(
                result.getRecords().stream().map(InboundMailAssembler::toDTO).toList(),
                result.getTotal(),
                result.getPage(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public InboundMailDetailDTO detail(long uid) {
        ensureEnabled();
        if (uid <= 0) {
            throw new BizException("邮件不存在");
        }
        return inboundMailboxGateway.findByUid(uid)
                .map(InboundMailAssembler::toDTO)
                .orElseThrow(() -> new BizException("邮件不存在"));
    }

    private void ensureEnabled() {
        capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME);
    }
}
