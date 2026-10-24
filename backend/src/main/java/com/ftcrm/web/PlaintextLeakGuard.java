package com.ftcrm.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.Role;
import com.ftcrm.security.AuthUser;
import com.ftcrm.web.dto.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class PlaintextLeakGuard implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(PlaintextLeakGuard.class);

    private final ObjectMapper mapper;

    public PlaintextLeakGuard(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser user)
                || user.role() != Role.SALESPERSON) {
            return body;
        }
        try {
            SalespersonResponsePolicy.assertSafe(mapper, body);
            return body;
        } catch (RuntimeException ex) {
            log.error("plaintext leak guard tripped");
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                    && attrs.getResponse() != null) {
                attrs.getResponse().setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
            return new ErrorBody("PLAINTEXT_BLOCKED", "响应被安全策略拦截");
        }
    }
}
