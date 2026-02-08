package lexicon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LexiconApplication {
    public static void main(String[] args) {
        SpringApplication.run(LexiconApplication.class, args);
    }
}