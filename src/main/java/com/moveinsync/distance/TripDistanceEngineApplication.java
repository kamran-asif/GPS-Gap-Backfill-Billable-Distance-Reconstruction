package com.moveinsync.distance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TripDistanceEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripDistanceEngineApplication.class, args);
    }
}
