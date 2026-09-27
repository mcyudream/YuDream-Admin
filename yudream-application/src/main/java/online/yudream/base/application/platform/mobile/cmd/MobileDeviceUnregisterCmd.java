package online.yudream.base.application.platform.mobile.cmd;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileDeviceUnregisterCmd implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String pushToken;
}
