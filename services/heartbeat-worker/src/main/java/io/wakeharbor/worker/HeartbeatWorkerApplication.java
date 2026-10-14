package io.wakeharbor.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HeartbeatWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeartbeatWorkerApplication.class, args);
    }
}
