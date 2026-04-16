package com.qrpublic.apartment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
//@EnableRabbit
public class PublicLinkApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(PublicLinkApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(PublicLinkApplication.class);
    }

}
