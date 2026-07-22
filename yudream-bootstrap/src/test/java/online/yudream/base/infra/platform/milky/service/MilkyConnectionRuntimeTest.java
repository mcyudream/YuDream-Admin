package online.yudream.base.infra.platform.milky.service;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MilkyConnectionRuntimeTest {

    @Test
    void doesNotRestoreEnabledConnectionsWhenMilkyCapabilityIsDisabled() {
        ReactorMilkyEventGateway gateway = mock(ReactorMilkyEventGateway.class);
        MilkyConnectionRepo connectionRepo = mock(MilkyConnectionRepo.class);
        CapabilityModuleRepo capabilityRepo = mock(CapabilityModuleRepo.class);
        SpringMilkyEventPublisher publisher = mock(SpringMilkyEventPublisher.class);
        CapabilityModule capability = mock(CapabilityModule.class);
        MilkyConnection connection = mock(MilkyConnection.class);
        when(capability.enabled()).thenReturn(false);
        when(capabilityRepo.findByCode("milky")).thenReturn(Optional.of(capability));
        when(connectionRepo.findEnabled()).thenReturn(List.of(connection));

        new MilkyConnectionRuntime(gateway, connectionRepo, capabilityRepo, publisher).restore();

        verifyNoInteractions(connectionRepo, gateway, publisher);
    }

    @Test
    void restoresRemainingConnectionsWhenOneConnectionFails() {
        ReactorMilkyEventGateway gateway = mock(ReactorMilkyEventGateway.class);
        MilkyConnectionRepo connectionRepo = mock(MilkyConnectionRepo.class);
        CapabilityModuleRepo capabilityRepo = mock(CapabilityModuleRepo.class);
        SpringMilkyEventPublisher publisher = mock(SpringMilkyEventPublisher.class);
        CapabilityModule capability = mock(CapabilityModule.class);
        MilkyConnection failed = mock(MilkyConnection.class);
        MilkyConnection healthy = mock(MilkyConnection.class);

        when(capability.enabled()).thenReturn(true);
        when(capabilityRepo.findByCode("milky")).thenReturn(Optional.of(capability));
        when(failed.getId()).thenReturn(1L);
        when(healthy.getId()).thenReturn(2L);
        when(failed.isEnabled()).thenReturn(true);
        when(healthy.isEnabled()).thenReturn(true);
        when(connectionRepo.findEnabled()).thenReturn(List.of(failed, healthy));
        when(connectionRepo.findById(1L)).thenReturn(Optional.of(failed));
        when(connectionRepo.findById(2L)).thenReturn(Optional.of(healthy));
        doThrow(new IllegalStateException("connection failed")).when(gateway).connect(org.mockito.ArgumentMatchers.eq(failed), any());
        when(gateway.connect(org.mockito.ArgumentMatchers.eq(healthy), any())).thenReturn(mock(reactor.core.Disposable.class));

        assertDoesNotThrow(() -> new MilkyConnectionRuntime(gateway, connectionRepo, capabilityRepo, publisher).restore());

        verify(gateway).connect(org.mockito.ArgumentMatchers.eq(failed), any());
        verify(gateway).connect(org.mockito.ArgumentMatchers.eq(healthy), any());
    }
}
