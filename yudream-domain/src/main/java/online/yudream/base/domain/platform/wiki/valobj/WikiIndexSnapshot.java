package online.yudream.base.domain.platform.wiki.valobj;

import java.util.List;

public record WikiIndexSnapshot(List<WikiChunk> chunks, List<WikiGraphRelation> relations) {
}
