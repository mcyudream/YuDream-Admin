package online.yudream.base.interfaces.platform.wiki.request;

import lombok.Data;

import java.util.List;

@Data
public class WikiResearchStartRequest {
    private String topic;
    private List<String> queries;
}
