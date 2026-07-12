package online.yudream.base.infra.platform.milky.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.domain.platform.milky.event.MilkyEventPublished;
import online.yudream.base.domain.platform.milky.model.MilkyModels;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class SpringMilkyEventPublisher {
 private final ApplicationEventPublisher publisher;
 public void publish(Long connectionId, MilkyModels.Event event){publisher.publishEvent(new MilkyEventPublished(connectionId,event));}
}
