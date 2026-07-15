package online.yudream.base.infra.platform.milky.service;

import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.milky.aggregate.MilkyConnection;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
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
}
