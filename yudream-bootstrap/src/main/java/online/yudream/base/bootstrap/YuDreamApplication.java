package online.yudream.base.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "online.yudream.base")
@EnableScheduling
public class YuDreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuDreamApplication.class, args);
    }
}
