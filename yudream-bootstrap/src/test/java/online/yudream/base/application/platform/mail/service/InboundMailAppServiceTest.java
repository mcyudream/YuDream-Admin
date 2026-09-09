package online.yudream.base.application.platform.mail.service;

import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.mail.dto.InboundMailDetailDTO;
import online.yudream.base.application.platform.mail.dto.InboundMailSummaryDTO;
import online.yudream.base.application.platform.mail.query.InboundMailPageQuery;
import online.yudream.base.domain.common.PageResult;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.mail.service.InboundMailboxGateway;
import online.yudream.base.domain.platform.mail.valobj.InboundMailDetail;
import online.yudream.base.domain.platform.mail.valobj.InboundMailSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboundMailAppServiceTest {

    @Mock
    private CapabilityAppService capabilityAppService;
    @Mock
    private InboundMailboxGateway inboundMailboxGateway;

    private InboundMailAppService service;

    @BeforeEach
    void setUp() {
        service = new InboundMailAppService(capabilityAppService, inboundMailboxGateway);
    }

    @Test
    void pageRequiresEnabledCapability() {
        doThrow(new BizException("入站邮箱能力未启用，请先在平台能力中启用"))
                .when(capabilityAppService).ensureEnabled("inbound-mail", "入站邮箱");

        assertThatThrownBy(() -> service.page(new InboundMailPageQuery()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("入站邮箱");
    }

    @Test
    void pageMapsGatewayRecords() {
        InboundMailPageQuery query = new InboundMailPageQuery();
        query.setPage(1);
        query.setSize(10);
        InboundMailSummary summary = new InboundMailSummary(
                99L, "验证码", "alice@example.com", "verify@example.com",
                LocalDateTime.of(2026, 9, 9, 12, 0), false, false);
        when(inboundMailboxGateway.page(1, 10, null))
                .thenReturn(new PageResult<>(List.of(summary), 1, 1, 10));

        PageResult<InboundMailSummaryDTO> page = service.page(query);

        verify(capabilityAppService).ensureEnabled("inbound-mail", "入站邮箱");
        assertThat(page.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getUid()).isEqualTo(99L);
            assertThat(item.getSubject()).isEqualTo("验证码");
            assertThat(item.getFrom()).isEqualTo("alice@example.com");
        });
    }

    @Test
    void detailRejectsMissingMail() {
        when(inboundMailboxGateway.findByUid(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(8L))
                .isInstanceOf(BizException.class)
                .hasMessage("邮件不存在");
    }

    @Test
    void detailMapsGatewayRecord() {
        InboundMailDetail detail = new InboundMailDetail(
                8L, "主题", "from@example.com", "to@example.com", "",
                LocalDateTime.of(2026, 9, 9, 12, 0), true, "正文", "<p>正文</p>", List.of());
        when(inboundMailboxGateway.findByUid(8L)).thenReturn(Optional.of(detail));

        InboundMailDetailDTO dto = service.detail(8L);

        assertThat(dto.getUid()).isEqualTo(8L);
        assertThat(dto.getTextBody()).isEqualTo("正文");
        assertThat(dto.getHtmlBody()).contains("正文");
    }

    @Test
    void pageClampsSizeToFifty() {
        InboundMailPageQuery query = new InboundMailPageQuery();
        query.setPage(0);
        query.setSize(200);
        query.setKeyword("验证码");
        when(inboundMailboxGateway.page(1, 50, "验证码"))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 50));

        PageResult<InboundMailSummaryDTO> page = service.page(query);

        verify(inboundMailboxGateway).page(1, 50, "验证码");
        assertThat(page.getSize()).isEqualTo(50);
        assertThat(page.getPage()).isEqualTo(1);
    }

    @Test
    void detailRejectsNonPositiveUid() {
        assertThatThrownBy(() -> service.detail(0L))
                .isInstanceOf(BizException.class)
                .hasMessage("邮件不存在");
    }
}
