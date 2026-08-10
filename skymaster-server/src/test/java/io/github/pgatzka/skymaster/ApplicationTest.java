package io.github.pgatzka.skymaster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class ApplicationTest {

    @Test
    void contextLoads() {
        assertThat(true).isTrue();
    }
}
