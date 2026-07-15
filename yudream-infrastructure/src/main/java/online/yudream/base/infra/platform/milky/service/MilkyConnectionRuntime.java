package online.yudream.base.infra.platform.milky.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.milky.repo.MilkyConnectionRepo;
import online.yudream.base.domain.platform.milky.service.MilkyEventGateway;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MilkyConnectionRuntime implements MilkyEventGateway {
 private final ReactorMilkyEventGateway gateway;
 private final MilkyConnectionRepo repo;
 private final CapabilityModuleRepo capabilityModuleRepo;
 private final SpringMilkyEventPublisher publisher;
 private final Map<Long,Disposable> sessions=new ConcurrentHashMap<>();
 @Override public void connect(Long id){close(id);if(!capabilityEnabled())return;var c=repo.findById(id).orElseThrow();if(!c.isEnabled())return;sessions.put(id,gateway.connect(c,(event,raw)->publisher.publish(id,event)));}
 @Override public void close(Long id){Disposable session=sessions.remove(id);if(session!=null)session.dispose();}
 @Override public void closeAll(){sessions.keySet().forEach(this::close);}
 @EventListener(ApplicationReadyEvent.class) public void restore(){if(capabilityEnabled())repo.findEnabled().forEach(c->connect(c.getId()));}
 @EventListener public void shutdown(MilkyRuntimeShutdownRequested ignored){closeAll();}
 @PreDestroy void stop(){closeAll();}
 private boolean capabilityEnabled(){return capabilityModuleRepo.findByCode("milky").map(CapabilityModule::enabled).orElse(false);}
}
