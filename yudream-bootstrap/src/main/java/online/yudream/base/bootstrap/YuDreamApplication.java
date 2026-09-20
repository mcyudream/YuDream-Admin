package online.yudream.base.bootstrap;

import online.yudream.base.bootstrap.installer.BootstrapInstallerMode;
import online.yudream.base.bootstrap.installer.InstallerApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "online.yudream.base")
@EnableScheduling
public class YuDreamApplication {

    public static void main(String[] args) {
        if (BootstrapInstallerMode.active(args)) {
            // 无引导配置且未预置数据库配置：进入安装向导切片
            InstallerApplication.run(args);
            return;
        }
        SpringApplication.run(YuDreamApplication.class, args);
    }
}
