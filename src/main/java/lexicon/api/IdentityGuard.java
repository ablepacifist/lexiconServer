package lexicon.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

/**
 * Makes "who you are" come from the login, not from a {@code userId} the client sends.
 *
 * A signed-in player who names a different player id gets 403:
 *   - in the query string, form fields or path of any write (and of the private reads below), via {@link Interceptor};
 *   - in any JSON object body, via {@link BodyAdvice}.
 * The trusted bridge (service principal) is exempt: it acts for the players in its chat.
 * Whether the caller is signed in at all is decided by {@link LexiconSecurityConfig}.
 */
public final class IdentityGuard {

    private IdentityGuard() {
    }

    /** Reads that return one player's private data; other GETs (browsing public media and playlists) are not restricted. */
    private static boolean isPrivateRead(String path) {
        return path.startsWith("/api/playback/") || path.startsWith("/api/download-queue/active/")
                || path.startsWith("/api/notifications");
    }

    public static class Interceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
            String method = request.getMethod();
            boolean write = !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method));
            if (!write && !isPrivateRead(request.getRequestURI())) {
                return true;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> vars = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (LexiconPrincipal.impersonates(request.getParameter("userId"))
                    || (vars != null && LexiconPrincipal.impersonates(vars.get("userId")))) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"You can only act as yourself\"}");
                return false;
            }
            return true;
        }
    }

    /** The live stream has exactly two channels; anything else is a bad request instead of a new junk channel. */
    public static class ChannelInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws IOException {
            String channel = request.getParameter("channel");
            if (channel != null && !"music".equals(channel) && !"video".equals(channel)) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Unknown channel\"}");
                return false;
            }
            return true;
        }
    }

    @ControllerAdvice
    public static class BodyAdvice extends RequestBodyAdviceAdapter {
        @Override
        public boolean supports(MethodParameter methodParameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
            return true;
        }

        @Override
        public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                    Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
            if (body instanceof Map<?, ?> map && LexiconPrincipal.impersonates(map.get("userId"))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only act as yourself");
            }
            return body;
        }
    }

    @Configuration
    public static class Config implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new Interceptor()).addPathPatterns("/api/**");
            registry.addInterceptor(new ChannelInterceptor()).addPathPatterns("/api/livestream/**");
        }
    }
}
