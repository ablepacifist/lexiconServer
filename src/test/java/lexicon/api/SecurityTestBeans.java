package lexicon.api;

import lexicon.logic.PlayerManagerService;
import lexicon.logic.RememberMeManagerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.mockito.Mockito.mock;

/** Stand-ins for the services the security chain's remember-me filter needs, so the chain can be tested without a database. */
@Configuration
class SecurityTestBeans {
    @Bean
    RememberMeManagerService rememberMeManagerService() {
        return mock(RememberMeManagerService.class);
    }

    @Bean
    PlayerManagerService playerManagerService() {
        return mock(PlayerManagerService.class);
    }
}
