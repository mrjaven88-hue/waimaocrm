package com.ftcrm.messaging;

import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import com.ftcrm.time.TimeSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内滑动窗口。重启后清零。TODO: 多实例试点前换成共享存储。
 */
@Component
public class SendRateLimiter {

    private final TimeSource time;
    private final AppProperties properties;
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public SendRateLimiter(TimeSource time, AppProperties properties) {
        this.time = time;
        this.properties = properties;
    }

    public void acquire(long userId, MessageChannel channel) {
        int limit = limitFor(channel);
        Deque<Instant> deque = hits.computeIfAbsent(userId + ":" + channel.name(), key -> new ArrayDeque<>());
        Instant now = time.now();
        Instant cutoff = now.minus(Duration.ofHours(1));
        synchronized (deque) {
            while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
                deque.removeFirst();
            }
            if (deque.size() >= limit) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "发送过于频繁，请稍后再试");
            }
            deque.addLast(now);
        }
    }

    private int limitFor(MessageChannel channel) {
        int raw = channel == MessageChannel.WHATSAPP
                ? properties.getMessaging().getWhatsappPerHour()
                : properties.getMessaging().getEmailPerHour();
        if (raw < 1) {
            return 1;
        }
        return Math.min(raw, 200);
    }
}
