package online.yudream.base.domain.platform.milky.event;

import online.yudream.base.domain.platform.milky.model.MilkyModels;
public record MilkyEventPublished(Long connectionId, MilkyModels.Event event) { }
