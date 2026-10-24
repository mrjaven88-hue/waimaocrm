package com.ftcrm.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalespersonResponsePolicyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsRevealedAndRawContactKeys() {
        assertThatThrownBy(() -> SalespersonResponsePolicy.assertSafe(mapper, Map.of("revealed", Map.of("phone", "1"))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SalespersonResponsePolicy.assertSafe(mapper, Map.of("email", "a@b.c")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsMaskedKeys() {
        assertThatCode(() -> SalespersonResponsePolicy.assertSafe(mapper,
                Map.of("contact", Map.of("phoneMasked", "+490****2299", "emailMasked", "s***@acme-export.test"))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHashKeysAndEchoedFragments() {
        assertThatThrownBy(() -> SalespersonResponsePolicy.assertSafe(mapper,
                Map.of("phone_hash", "abcdefabcdefabcd")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SalespersonResponsePolicy.assertSafe(mapper,
                Map.of("notes", "哈希截断 abcdefabcdefabcd")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> SalespersonResponsePolicy.assertSafe(mapper,
                Map.of("revealed_contact", Map.of("phone", "1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsDatesAndMaskedProductCodes() {
        assertThatCode(() -> SalespersonResponsePolicy.assertSafe(mapper, Map.of(
                "notes", "2024-10-23 广交会认识",
                "productInterest", "不锈钢球阀 DN50-1234-5678",
                "message", "联系方式已存在，未重复建档")))
                .doesNotThrowAnyException();
    }
}
