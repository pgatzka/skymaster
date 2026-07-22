package io.github.pgatzka.skymaster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Application {

    /**
     * Keep public modifier -> spring boot gradle plugin cant find the class otherwise
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
