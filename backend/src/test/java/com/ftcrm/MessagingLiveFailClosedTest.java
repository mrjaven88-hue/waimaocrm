package com.ftcrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.time.AdjustableTimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 实发供应商名已选但密钥缺失 → 失败关闭，不落 stub 501、不泄露目的地。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MessagingLiveFailClosedTest.TestTime.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ftcrmmsgfail;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ftcrm.messaging.whatsapp-provider=wati",
        "ftcrm.messaging.email-provider=resend",
        "ftcrm.messaging.wati-api-base=",
        "ftcrm.messaging.wati-api-token=",
        "ftcrm.messaging.resend-api-key=",
        "ftcrm.messaging.email-from="
})
@Transactional
class MessagingLiveFailClosedTest {

    private static final String PHONE = "+4900001112299";
    private static final String EMAIL = "sentinel.hans@acme-export.test";
    private static final String LOCAL = "sentinel.hans";
    private static final String WHATSAPP = "+4900009998877";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired AdjustableTimeSource clock;

    @BeforeEach
    void resetClockAndManager() {
        clock.set(Instant.parse("2026-10-23T08:00:00Z"));
        if (users.findByUsername("manager").isEmpty()) {
            AppUser manager = new AppUser();
            manager.setUsername("manager");
            manager.setPasswordHash(encoder.encode("Manager@123"));
            manager.setDisplayName("陈经理");
            manager.setRole(Role.MANAGER);
            manager.setCreatedAt(clock.now());
            users.save(manager);
        }
    }

    @Test
    void missingLiveKeysFailClosedWithoutDestination() throws Exception {
        String sales = tokenOfNewSales("fail.sales");
        long id = createLead(sales, "缺密钥厂");

        String wa = mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("leadId", id, "templateCode", "inquiry_followup_v1"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_NOT_CONFIGURED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String em = mvc.perform(post("/api/messages/email")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"quote_update_v1","subject":"跟进"}
                                """.formatted(id)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("PROVIDER_NOT_CONFIGURED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertNoDestination(wa);
        assertNoDestination(em);
    }

    private long createLead(String token, String company) throws Exception {
        String created = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", company,
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", PHONE,
                                "email", EMAIL,
                                "whatsapp", WHATSAPP))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(created);
        return json.readTree(created).get("id").asLong();
    }

    private String tokenOfNewSales(String username) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", username,
                                "password", "Sales@123",
                                "displayName", "业务员"))))
                .andExpect(status().isOk());
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "Sales@123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("token").asText();
    }

    private static void assertNoDestination(String body) {
        assertThat(body).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
        assertThat(body).doesNotContain("\"phone\"").doesNotContain("\"email\"").doesNotContain("\"whatsapp\"");
        assertThat(body).doesNotContain("\"to\"").doesNotContain("\"destination\"");
    }

    @TestConfiguration
    static class TestTime {
        @Bean
        AdjustableTimeSource timeSource() {
            return new AdjustableTimeSource();
        }
    }
}
