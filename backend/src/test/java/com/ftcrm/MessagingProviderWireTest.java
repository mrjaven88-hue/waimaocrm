package com.ftcrm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.AuditEventRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MessagingProviderWireTest.TestTime.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ftcrmmsgprovider;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ftcrm.messaging.whatsapp-provider=mock",
        "ftcrm.messaging.email-provider=mock",
        "ftcrm.messaging.whatsapp-api-key=super-secret-wa-api-key",
        "ftcrm.messaging.resend-api-key=super-secret-resend-key",
        "ftcrm.messaging.wati-api-token=super-secret-wati"
})
@Transactional
class MessagingProviderWireTest {

    private static final String PHONE = "+4900001112299";
    private static final String EMAIL = "sentinel.hans@acme-export.test";
    private static final String LOCAL = "sentinel.hans";
    private static final String WHATSAPP = "+4900009998877";
    private static final String SECRETS = "super-secret";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditEventRepository audits;
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
    void mockProviderDeliversWithoutPlaintext() throws Exception {
        String sales = tokenOfNewSales("mock.sales");
        long id = createLead(sales, "Mock投递厂");

        String whatsapp = mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody(id, "inquiry_followup_v1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.delivered").value(true))
                .andExpect(jsonPath("$.leadId").value(id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String email = mvc.perform(post("/api/messages/email")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"quote_update_v1","subject":"报价跟进"}
                                """.formatted(id)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.delivered").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertNoDestination(whatsapp);
        assertNoDestination(email);
        assertThat(email).doesNotContain("报价跟进");
        assertThat(audits.findAll())
                .filteredOn(event -> "MESSAGE_SEND".equals(event.getAction()))
                .isNotEmpty()
                .allSatisfy(event -> {
                    String blob = String.valueOf(event.getMetadataJson()) + event.getReason();
                    assertNoDestination(blob);
                    assertThat(blob).contains("mock");
                });
    }

    @Test
    void mockStillRefusesDestinationFields() throws Exception {
        String sales = tokenOfNewSales("mock.refuse");
        long id = createLead(sales, "Mock拒绝明文");
        String rejected = mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"inquiry_followup_v1","to":"%s"}
                                """.formatted(id, WHATSAPP)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DESTINATION_NOT_ACCEPTED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(rejected);
    }

    private String sendBody(long id, String template) throws Exception {
        return json.writeValueAsString(Map.of("leadId", id, "templateCode", template));
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
                                "whatsapp", WHATSAPP,
                                "productInterest", "球阀",
                                "source", "展会"))))
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
        assertThat(body).doesNotContain(SECRETS);
        assertThat(body).doesNotContain("\"phone\"").doesNotContain("\"email\"").doesNotContain("\"whatsapp\"");
        assertThat(body).doesNotContain("\"revealed\"").doesNotContain("\"to\"").doesNotContain("\"recipient\"");
        assertThat(body).doesNotContain("\"destination\"");
    }

    @TestConfiguration
    static class TestTime {
        @Bean
        AdjustableTimeSource timeSource() {
            return new AdjustableTimeSource();
        }
    }
}
