package online.yudream.base.interfaces.platform.docs.res;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiDocAccessTicketRes {

    private String ticket;
    private long expiresIn;
}
