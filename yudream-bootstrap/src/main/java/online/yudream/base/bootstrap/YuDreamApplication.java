package online.yudream.base.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "online.yudream.base")
public class YuDreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuDreamApplication.class, args);
    }
}
