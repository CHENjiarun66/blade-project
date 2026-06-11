package com.blade;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.blade.**.mapper")
@EnableScheduling
public class BladeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BladeApplication.class, args);
    }
}
