package lexicon.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple IP-based rate limiter to protect against abuse.
 * Limits each IP to 200 requests per minute.
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final int MAX_REQUESTS_PER_MINUTE = 200;
    private final Map<String, RateBucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        String ip = getClientIp(httpReq);

        // Periodic cleanup of stale entries (every 5 minutes)
        long now = System.currentTimeMillis();
        if (now - lastCleanup > 300_000) {
            lastCleanup = now;
            buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > 120_000);
        }

        RateBucket bucket = buckets.computeIfAbsent(ip, k -> new RateBucket());

        if (bucket.isAllowed(now)) {
            chain.doFilter(request, response);
        } else {
            HttpServletResponse httpResp = (HttpServletResponse) response;
            httpResp.setStatus(429);
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("{\"error\":\"Too many requests. Try again later.\"}");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        // Cloudflare sets CF-Connecting-IP for the real client IP
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isEmpty()) return cfIp;

        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // Take the first IP (original client)
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class RateBucket {
        volatile long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);

        boolean isAllowed(long now) {
            // Reset window every 60 seconds
            if (now - windowStart > 60_000) {
                synchronized (this) {
                    if (now - windowStart > 60_000) {
                        windowStart = now;
                        count.set(0);
                    }
                }
            }
            return count.incrementAndGet() <= MAX_REQUESTS_PER_MINUTE;
        }
    }
}
