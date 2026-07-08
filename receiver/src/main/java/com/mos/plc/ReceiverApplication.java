package com.mos.plc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReceiverApplication {
    public static void main(String[] args) {
        prepareRuntimeDirectories();
        SpringApplication.run(ReceiverApplication.class, args);
    }

    private static void prepareRuntimeDirectories() {
        try {
            Files.createDirectories(Path.of("data"));
            Files.createDirectories(Path.of("logs"));
            Files.createDirectories(Path.of("uploads"));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to prepare runtime directories", ex);
        }
    }
}
