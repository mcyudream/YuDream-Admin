package online.yudream.base.interfaces.system.setting.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrontendFeatureRes {

    private boolean apiKeyEnabled;
    private boolean passkeyEnabled;
    private boolean oauthServerEnabled;
    private boolean oauthClientEnabled;
    private Map<String, Boolean> capabilities;
}
