package lexicon.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lexicon.logic.ActivityTrackerService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Tells the activity tracker who made each signed-in API request. Runs after Spring Security,
 * so it sees whichever way the player signed in. The bridge's service login is not a player
 * and is skipped. Deciding what to record is the tracker's job; this only says who called.
 */
public class ActivityInterceptor implements HandlerInterceptor {

    private final ActivityTrackerService tracker;

    public ActivityInterceptor(ActivityTrackerService tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LexiconPrincipal p = LexiconPrincipal.current();
        if (p != null && !p.isService() && p.getUserId() != null) {
            tracker.recordActivity(p.getUserId());
        }
        return true;
    }

    @Configuration
    public static class Config implements WebMvcConfigurer {
        private final ActivityTrackerService tracker;

        public Config(ActivityTrackerService tracker) {
            this.tracker = tracker;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new ActivityInterceptor(tracker)).addPathPatterns("/api/**");
        }
    }
}
