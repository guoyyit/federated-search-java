package com.github.guoyyit.federatedsearchjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.github.guoyyit.federatedsearchjava.config.FederatedSearchProperties;

@SpringBootApplication
@EnableConfigurationProperties(FederatedSearchProperties.class)
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
