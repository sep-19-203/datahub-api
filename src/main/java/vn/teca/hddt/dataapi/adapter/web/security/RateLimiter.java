package vn.teca.hddt.dataapi.adapter.web.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Token bucket theo client trong từng pod (APIC giới hạn tổng; lớp này chặn bùng nổ tới backend). */
@Component
public class RateLimiter {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientId, double ratePerSecond, int burst) {
        return buckets.computeIfAbsent(clientId, k -> new Bucket(ratePerSecond, burst)).tryAcquire();
    }

    static final class Bucket {
        private final double rate;
        private final double capacity;
        private double tokens;
        private long last = System.nanoTime();

        Bucket(double rate, int burst) {
            this.rate = rate;
            this.capacity = Math.max(1, burst);
            this.tokens = capacity;
        }

        synchronized boolean tryAcquire() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - last) / 1e9 * rate);
            last = now;
            if (tokens >= 1) {
                tokens -= 1;
                return true;
            }
            return false;
        }
    }
}
