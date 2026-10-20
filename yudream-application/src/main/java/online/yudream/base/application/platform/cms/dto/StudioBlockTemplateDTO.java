package online.yudream.base.application.platform.cms.dto;

import java.time.Instant;
import java.util.List;

public record StudioBlockTemplateDTO(String id, String name, String category, String description, List<String> keywords,
                                     String nodesJson, String revision, String createdBy, Instant createdAt, Instant updatedAt) {}
