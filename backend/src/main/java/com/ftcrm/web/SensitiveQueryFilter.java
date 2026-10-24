package com.ftcrm.web;

import com.ftcrm.common.SideChannelGuard;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SensitiveQueryFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveQueryFilter.class);
    private static final int MAX_SCAN = 4000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (sensitive(request)) {
            log.warn("rejected sensitive query");
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean sensitive(HttpServletRequest request) {
        String raw = request.getQueryString();
        if (raw != null && (raw.length() > MAX_SCAN || contains(raw) || contains(decodeRepeated(raw)))) {
            return true;
        }
        for (var entry : request.getParameterMap().entrySet()) {
            if (contains(entry.getKey()) || contains(decodeRepeated(entry.getKey()))) {
                return true;
            }
            String[] values = entry.getValue();
            if (values == null) {
                continue;
            }
            for (String value : values) {
                if (value != null && (value.length() > MAX_SCAN || contains(value) || contains(decodeRepeated(value)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(String value) {
        return value != null && SideChannelGuard.containsContact(value);
    }

    private static String decodeRepeated(String raw) {
        if (raw == null) {
            return "";
        }
        String current = raw;
        for (int i = 0; i < 2; i++) {
            try {
                String next = URLDecoder.decode(current, StandardCharsets.UTF_8);
                if (next.equals(current)) {
                    break;
                }
                current = next;
            } catch (IllegalArgumentException ex) {
                break;
            }
        }
        return current;
    }

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.getWriter().write("{\"error\":\"BAD_QUERY\",\"message\":\"查询参数不能包含联系方式\"}");
    }
}
