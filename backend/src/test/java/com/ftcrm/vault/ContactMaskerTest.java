package com.ftcrm.vault;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactMaskerTest {

    @Test
    void masksEmailAndPhoneWithoutKeepingTheLocalPart() {
        assertThat(ContactMasker.email("sentinel.hans@acme-export.test")).isEqualTo("s***@acme-export.test");
        assertThat(ContactMasker.phone("+4900001112299")).isEqualTo("+490****2299");
        assertThat(ContactMasker.phone("+4900001112299")).doesNotContain("0001112299");
    }
}
