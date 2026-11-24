package org.spauter.dingtodo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DingTodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DingTodoApplication.class, args);
    }
}
