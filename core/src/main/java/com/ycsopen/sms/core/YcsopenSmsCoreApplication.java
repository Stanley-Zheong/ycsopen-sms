package com.ycsopen.sms.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ycsopen-sms core 后端入口。
 * <p>实现范围与依据：Documents/优创硕安/系统开发/ycsansms.md（PRD v1.3.0）。
 * 各包按 PRD 第 5 章功能模块划分，见 core/docs/ARCHITECTURE.md。</p>
 */
@SpringBootApplication
@EnableScheduling
public class YcsopenSmsCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(YcsopenSmsCoreApplication.class, args);
    }
}
