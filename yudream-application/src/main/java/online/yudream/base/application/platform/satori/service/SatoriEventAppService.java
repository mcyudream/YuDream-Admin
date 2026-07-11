package online.yudream.base.application.platform.satori.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.satori.dto.SatoriEventDetailDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.satori.aggregate.SatoriConnection;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventCursor;
import online.yudream.base.domain.platform.satori.aggregate.SatoriEventRecord;
import online.yudream.base.domain.platform.satori.aggregate.SatoriLogin;
import online.yudream.base.domain.platform.satori.enumerate.SatoriOpcode;
import online.yudream.base.domain.platform.satori.model.SatoriModels;
import online.yudream.base.domain.platform.satori.repo.SatoriConnectionRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriEventCursorRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriEventRepo;
import online.yudream.base.domain.platform.satori.repo.SatoriLoginRepo;
import online.yudream.base.domain.platform.satori.service.SatoriEventCodec;
import online.yudream.base.domain.platform.satori.service.SatoriEventIngress;
import online.yudream.base.domain.platform.satori.service.SatoriEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Normalizes both active and passive Satori transports into one durable pipeline. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "yudream.platform.capabilities.satori", name = "enabled", havingValue = "true")
public class SatoriEventAppService implements SatoriEventIngress {
    private static final int EVENT_RETENTION_DAYS = 30;
    private static final String CAPABILITY_CODE = SatoriConnectionAppService.CAPABILITY_CODE;
    private static final String CAPABILITY_NAME = "Satori 平台";

    private final SatoriConnectionRepo connectionRepo;
    private final SatoriLoginRepo loginRepo;
    private final SatoriEventRepo eventRepo;
    private final SatoriEventCursorRepo cursorRepo;
    private final SatoriEventPublisher eventPublisher;
    private final SatoriEventCodec eventCodec;
    private final CapabilityAppService capabilityAppService;

    @Override
    @Transactional
    public void acceptEvent(Long connectionId, SatoriModels.SatoriEvent event, String rawData) {
        ensureEnabled();
        enabledConnection(connectionId);
        if (event == null || event.sn() == null || event.sn().isBlank()) {
            throw new BizException("Satori EVENT 缺少 sn");
        }
        SatoriEventRecord record = eventRepo.findByIdempotencyKey(connectionId, event.sn())
                .orElseGet(() -> eventRepo.save(SatoriEventRecord.create(connectionId, event.sn(), event.type(), rawData, EVENT_RETENTION_DAYS)));
        if (!record.published()) {
            eventPublisher.publish(connectionId, event);
            record.markPublished();
            eventRepo.save(record);
        }
        advanceCursor(connectionId, event.sn());
    }

    @Override
    @Transactional
    public void synchronizeLogins(Long connectionId, List<SatoriModels.SatoriLogin> logins) {
        ensureEnabled();
        enabledConnection(connectionId);
        if (logins == null) return;
        for (SatoriModels.SatoriLogin source : logins) {
            if (source == null || blank(source.platform()) || blank(source.selfId())) continue;
            SatoriLogin login = loginRepo.findByNaturalKey(connectionId, source.platform(), source.selfId())
                    .orElseGet(() -> SatoriLogin.create(connectionId, source.platform(), source.selfId(), source.status(), source.adapter(), source.features()));
            login.refresh(source.status(), source.adapter(), source.features());
            loginRepo.save(login);
        }
    }

    @Override
    public void acceptMeta(Long connectionId, SatoriModels.SatoriMeta meta) {
        ensureEnabled();
        enabledConnection(connectionId);
    }

    @Transactional(readOnly = true)
    public SatoriEventDetailDTO detail(Long connectionId, String sequence) {
        ensureEnabled();
        enabledConnection(connectionId);
        SatoriEventRecord event = eventRepo.findByConnectionIdAndSequence(connectionId, sequence)
                .orElseThrow(() -> new BizException("Satori 事件不存在或已过期"));
        return SatoriEventDetailDTO.builder().sequence(event.getSequence()).type(event.getType())
                .rawData(event.getRawData()).receivedAt(event.getReceivedAt()).build();
    }

    @Transactional
    public void acceptWebhook(Long connectionId, String authorization, String opcodeHeader, String rawData) {
        ensureEnabled();
        SatoriConnection connection = enabledConnection(connectionId);
        verifyBearer(connection, authorization);
        SatoriOpcode opcode = parseWebhookOpcode(opcodeHeader);
        if (opcode == SatoriOpcode.EVENT) {
            acceptEvent(connectionId, eventCodec.decodeEvent(rawData), rawData);
            return;
        }
        if (opcode == SatoriOpcode.META) {
            acceptMeta(connectionId, eventCodec.decodeMeta(rawData));
            return;
        }
        throw new BizException("WebHook 仅支持 Satori EVENT 或 META");
    }

    private void advanceCursor(Long connectionId, String sequence) {
        SatoriEventCursor cursor = cursorRepo.findByConnectionId(connectionId)
                .orElseGet(() -> SatoriEventCursor.advance(connectionId, sequence));
        if (cursor.getId() != null) cursor.advanceTo(sequence);
        cursorRepo.save(cursor);
    }

    private SatoriConnection enabledConnection(Long connectionId) {
        if (connectionId == null) throw new BizException("Satori 连接 ID 不能为空");
        SatoriConnection connection = connectionRepo.findById(connectionId)
                .orElseThrow(() -> new BizException("Satori 连接不存在"));
        if (!connection.enabled()) throw new BizException("Satori 连接未启用");
        return connection;
    }

    private void verifyBearer(SatoriConnection connection, String authorization) {
        String prefix = "Bearer ";
        if (authorization == null || !authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            throw new BizException("Satori WebHook 鉴权失败");
        }
        String supplied = authorization.substring(prefix.length()).trim();
        if (supplied.isEmpty() || !java.security.MessageDigest.isEqual(
                connection.getToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new BizException("Satori WebHook 鉴权失败");
        }
    }

    private SatoriOpcode parseWebhookOpcode(String value) {
        try {
            return SatoriOpcode.fromValue(Integer.parseInt(value));
        } catch (RuntimeException exception) {
            throw new BizException("Satori-Opcode 无效");
        }
    }

    private void ensureEnabled() { capabilityAppService.ensureEnabled(CAPABILITY_CODE, CAPABILITY_NAME); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
