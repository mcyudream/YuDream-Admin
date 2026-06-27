package online.yudream.base.infra.common.impl;

import online.yudream.base.domain.common.service.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordEncoderImpl implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}
