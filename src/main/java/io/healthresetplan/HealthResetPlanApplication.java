package io.healthresetplan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 健康重启计划后端启动类。
 */
@SpringBootApplication
@MapperScan("io.healthresetplan.modules.**.mapper")
public class HealthResetPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthResetPlanApplication.class, args);
    }
}
