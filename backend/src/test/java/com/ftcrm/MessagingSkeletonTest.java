package com.ftcrm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.AuditEvent;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.AuditEventRepository;
import com.ftcrm.time.AdjustableTimeSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MessagingSkeletonTest.TestTime.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ftcrmmessaging;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "ftcrm.messaging.whatsapp-provider=super-secret-wa-key",
        "ftcrm.messaging.whatsapp-api-key=super-secret-wa-api-key",
        "ftcrm.messaging.email-provider=resend",
        "ftcrm.messaging.resend-api-key=super-secret-resend-key",
        "ftcrm.messaging.gmail-client-secret=super-secret-gmail",
        "ftcrm.messaging.wati-api-token=super-secret-wati"
})
@Transactional
class MessagingSkeletonTest {

    private static final String PHONE = "+4900001112299";
    private static final String EMAIL = "sentinel.hans@acme-export.test";
    private static final String LOCAL = "sentinel.hans";
    private static final String WHATSAPP = "+4900009998877";
    private static final String SECRETS = "super-secret";
    private static final String CIPHER = "CIPHERMARKER123456";
    private static final String IV = "IVMARKER12345678";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    AuditEventRepository audits;
    @Autowired
    AdjustableTimeSource clock;

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
    void stubsDoNotReturnPlaintextDestinations() throws Exception {
        String sales = tokenOfNewSales("msg.sales");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "消息哨兵厂");

        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String whatsapp;
        String email;
        try {
            whatsapp = mvc.perform(post("/api/messages/whatsapp")
                            .header("Authorization", "Bearer " + sales)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(sendBody(id, "inquiry_followup_v1", null)))
                    .andExpect(status().isNotImplemented())
                    .andExpect(jsonPath("$.error").value("NOT_IMPLEMENTED"))
                    .andExpect(jsonPath("$.channel").value("WHATSAPP"))
                    .andExpect(jsonPath("$.provider").value("unconfigured"))
                    .andExpect(jsonPath("$.delivered").value(false))
                    .andExpect(jsonPath("$.leadId").value(id))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            email = mvc.perform(post("/api/messages/email")
                            .header("Authorization", "Bearer " + manager)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"leadId":%d,"templateCode":"quote_update_v1","subject":"报价跟进","body":"请看更新"}
                                    """.formatted(id)))
                    .andExpect(status().isNotImplemented())
                    .andExpect(jsonPath("$.channel").value("EMAIL"))
                    .andExpect(jsonPath("$.provider").value("resend"))
                    .andExpect(jsonPath("$.delivered").value(false))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            root.detachAppender(logs);
        }
        assertNoDestination(whatsapp);
        assertNoDestination(email);
        assertThat(email).doesNotContain("报价跟进").doesNotContain("请看更新");
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(WHATSAPP).doesNotContain(SECRETS);
        }
        String audit = mvc.perform(get("/api/audit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(audit).contains("MESSAGE_SEND").contains("发送消息");
        assertNoDestination(audit);
        assertThat(audits.findAll())
                .filteredOn(event -> "MESSAGE_SEND".equals(event.getAction()))
                .isNotEmpty()
                .allSatisfy(event -> assertNoDestination(String.valueOf(event.getMetadataJson()) + event.getReason()));
    }

    @Test
    void destinationFieldsAndSmuggledBodyAreRefused() throws Exception {
        String sales = tokenOfNewSales("msg.refuse");
        long id = createLead(sales, "拒绝明文厂");
        String rejected = mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"inquiry_followup_v1","to":"%s","email":"%s","phone":"%s"}
                                """.formatted(id, WHATSAPP, EMAIL, PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DESTINATION_NOT_ACCEPTED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(rejected);

        String smuggled = mvc.perform(post("/api/messages/email")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"inquiry_followup_v1","subject":"跟进","body":"write %s"}
                                """.formatted(id, EMAIL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PLAINTEXT_SIDE_CHANNEL"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(smuggled);

        mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"templateCode":"free_text_v9"}
                                """.formatted(id)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("TEMPLATE_NOT_APPROVED"));
    }

    @Test
    void salesSendOnlyOwnLeadsAndListsStayEmpty() throws Exception {
        String owner = tokenOfNewSales("msg.owner");
        String other = tokenOfNewSales("msg.other");
        String manager = login("manager", "Manager@123");
        long id = createLead(owner, "仅负责人可发");

        mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody(id, "inquiry_followup_v1", null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/leads/" + id + "/conversations").header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        String threads = mvc.perform(get("/api/leads/" + id + "/conversations").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archiveStatus").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.threads").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String messages = mvc.perform(get("/api/leads/" + id + "/conversations/messages").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages").isEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(threads);
        assertNoDestination(messages);

        mvc.perform(post("/api/messages/whatsapp").contentType(MediaType.APPLICATION_JSON).content(sendBody(id, "inquiry_followup_v1", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void archiveRefusesPlaintextAndDoesNotEchoCipher() throws Exception {
        String sales = tokenOfNewSales("msg.archive");
        long id = createLead(sales, "存档哨兵厂");
        String leaked = mvc.perform(post("/api/messages/archive")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"channel":"WHATSAPP","direction":"OUTBOUND","recipient":"%s","bodyCipher":"%s","bodyIv":"%s"}
                                """.formatted(id, EMAIL, CIPHER, IV)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(leaked);
        assertThat(leaked).doesNotContain(CIPHER);

        String archived = mvc.perform(post("/api/messages/archive")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leadId":%d,"channel":"WHATSAPP","direction":"OUTBOUND","bodyCipher":"%s","bodyIv":"%s"}
                                """.formatted(id, CIPHER, IV)))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.stored").value(false))
                .andExpect(jsonPath("$.error").value("NOT_IMPLEMENTED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(archived);
        assertThat(archived).doesNotContain(CIPHER).doesNotContain(IV);
        assertThat(audits.findAll())
                .filteredOn(event -> "MESSAGE_ARCHIVE".equals(event.getAction()))
                .isNotEmpty()
                .allSatisfy(this::assertAuditHasNoPayload);
    }

    @Test
    void missingChannelContactDoesNotEchoTheOtherAddress() throws Exception {
        String sales = tokenOfNewSales("msg.missing");
        String created = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "只有邮箱",
                                "country", "德国",
                                "contactName", "汉斯",
                                "email", EMAIL))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = json.readTree(created).get("id").asLong();
        String body = mvc.perform(post("/api/messages/whatsapp")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sendBody(id, "quote_update_v1", "zh")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("NO_CONTACT"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoDestination(body);
    }

    private void assertAuditHasNoPayload(AuditEvent event) {
        String blob = String.valueOf(event.getMetadataJson()) + String.valueOf(event.getReason());
        assertNoDestination(blob);
        assertThat(blob).doesNotContain(CIPHER).doesNotContain(IV);
    }

    private String sendBody(long id, String template, String language) throws Exception {
        if (language == null) {
            return json.writeValueAsString(Map.of("leadId", id, "templateCode", template));
        }
        return json.writeValueAsString(Map.of("leadId", id, "templateCode", template, "language", language));
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
        return login(username, "Sales@123");
    }

    private String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
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
