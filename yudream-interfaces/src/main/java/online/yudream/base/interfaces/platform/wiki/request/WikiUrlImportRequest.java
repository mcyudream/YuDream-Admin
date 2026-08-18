package online.yudream.base.interfaces.platform.wiki.request;

import lombok.Data;

import java.util.List;

@Data
public class WikiUrlImportRequest {
    private String folderPath;
    private List<String> urls;
}
