package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.cloudrive.mapper")
@SpringBootApplication
public class CloudDriveApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudDriveApplication.class, args);
    }
} 