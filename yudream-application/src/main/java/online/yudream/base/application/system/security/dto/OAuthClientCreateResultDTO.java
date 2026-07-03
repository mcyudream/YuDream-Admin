package online.yudream.base.application.system.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthClientCreateResultDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private OAuthClientDTO client;
    private String clientSecret;
}
