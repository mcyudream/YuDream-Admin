package online.yudream.base.interfaces.platform.cms.res;

import java.time.Instant;
import java.util.List;

public record StudioBlockTemplateRes(String id, String name, String category, String description, List<String> keywords,
                                     String nodesJson, String revision, String createdBy, Instant createdAt, Instant updatedAt) {}
