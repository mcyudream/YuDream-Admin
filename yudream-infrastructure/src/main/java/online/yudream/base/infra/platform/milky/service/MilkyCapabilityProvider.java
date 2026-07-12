package online.yudream.base.infra.platform.milky.service;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityType;
import online.yudream.base.domain.platform.capability.service.CapabilityProvider;
import online.yudream.base.domain.platform.capability.valobj.CapabilityDescriptor;
import online.yudream.base.domain.platform.capability.valobj.CapabilityHealth;
import online.yudream.base.domain.platform.capability.valobj.CapabilityTestResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix="yudream.platform.capabilities.milky",name="enabled",havingValue="true")
public class MilkyCapabilityProvider implements CapabilityProvider {
 private final AtomicBoolean enabled=new AtomicBoolean(false);
 @Override public CapabilityDescriptor descriptor(){return new CapabilityDescriptor("milky","Milky",CapabilityType.MESSAGING,"Milky QQ 协议与 WebQQ 管理", "i-ri:chat-3-line",75, Map.of(), List.of());}
 @Override public CapabilityHealth health(){return enabled.get()?CapabilityHealth.enabled("Milky 已启用",Map.of()):CapabilityHealth.disabled("Milky 未启用");}
 @Override public void enable(Map<String,String> config){enabled.set(true);}
 @Override public void disable(){enabled.set(false);}
 @Override public CapabilityTestResult test(String message){return enabled.get()?CapabilityTestResult.success("Milky 已就绪"):CapabilityTestResult.failure("Milky 未启用");}
}
