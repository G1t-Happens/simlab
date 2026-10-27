package dev.simlab.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SimServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimServerApplication.class, args);
    }
}
