package com.ftcrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.AuditEvent;
import com.ftcrm.domain.Lead;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.AuditEventRepository;
import com.ftcrm.repo.LeadRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LeadStageWireTest.TestTime.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ftcrmstagewire;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@Transactional
class LeadStageWireTest {

    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    LeadRepository leadRows;
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
    void createPersistsDefaultGradeC() throws Exception {
        String sales = tokenOfNewSales("sales.grade.create");
        long id = createLead(sales, "等级建档厂");
        String detail = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("LEAD"))
                .andExpect(jsonPath("$.grade").value("C"))
                .andExpect(jsonPath("$.gradeLabel").value("弱意向"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(detail).path("wakePlan").isMissingNode()
                || json.readTree(detail).path("wakePlan").isNull()).isTrue();
        Lead stored = leadRows.findById(id).orElseThrow();
        assertThat(stored.getCustomerGrade().name()).isEqualTo("C");
    }

    @Test
    void promoteAdvancesOneStageWithCommercialEvidence() throws Exception {
        String sales = tokenOfNewSales("sales.promote.one");
        long id = createLead(sales, "升迁阀门");
        String body = promote(sales, id, Map.of(
                "product", "不锈钢球阀 DN50",
                "quantity", "2 柜",
                "destinationPort", "Hamburg",
                "daysSinceValidInteraction", 0,
                "consecutiveUnanswered", 0));
        JsonNode node = json.readTree(body);
        assertThat(node.get("stage").asText()).isEqualTo("INQUIRY");
        assertThat(node.get("grade").asText()).isIn("B", "C");
        assertThat(node.hasNonNull("wakePlan")).isFalse();

        List<AuditEvent> events = audits.findAll().stream()
                .filter(e -> id == (e.getTargetId() == null ? -1L : e.getTargetId()))
                .filter(e -> "LEAD_PROMOTE".equals(e.getAction()) || "LEAD_GRADE".equals(e.getAction()))
                .toList();
        assertThat(events).isNotEmpty();
        assertThat(events.stream().anyMatch(e -> "LEAD_PROMOTE".equals(e.getAction()))).isTrue();
    }

    @Test
    void lossSetsLostWakePlanWithoutOutboundDispatch() throws Exception {
        String sales = tokenOfNewSales("sales.promote.lost");
        long id = createLead(sales, "流失阀门");
        String body = promote(sales, id, Map.of(
                "customerReply", "Thanks, we decided not to proceed this quarter",
                "daysSinceValidInteraction", 5,
                "consecutiveUnanswered", 1));
        JsonNode node = json.readTree(body);
        assertThat(node.get("stage").asText()).isEqualTo("LOST");
        assertThat(node.get("wakePlan")).isNotNull();
        assertThat(node.get("wakePlan").get("sendEnabled").asBoolean()).isFalse();
        assertThat(node.get("wakePlan").get("steps")).hasSize(3);
        assertThat(node.get("wakePlan").get("steps").get(0).get("dayOffset").asInt()).isEqualTo(0);
        assertThat(node.get("wakePlan").get("steps").get(1).get("dayOffset").asInt()).isEqualTo(3);
        assertThat(node.get("wakePlan").get("steps").get(2).get("dayOffset").asInt()).isEqualTo(7);

        Lead stored = leadRows.findById(id).orElseThrow();
        assertThat(stored.getStage().name()).isEqualTo("LOST");
        assertThat(stored.getWakePlanJson()).contains("\"sendEnabled\":false");

        assertThat(audits.findAll().stream()
                .filter(e -> "LEAD_LOST".equals(e.getAction()))
                .anyMatch(e -> id == e.getTargetId())).isTrue();
    }

    @Test
    void threeConsecutiveUnansweredMarksGradeDWithoutForcedLoss() throws Exception {
        String sales = tokenOfNewSales("sales.grade.d");
        long id = createLead(sales, "沉默等级厂");
        String body = promote(sales, id, Map.of(
                "daysSinceValidInteraction", 10,
                "consecutiveUnanswered", 3));
        JsonNode node = json.readTree(body);
        assertThat(node.get("stage").asText()).isEqualTo("LEAD");
        assertThat(node.get("grade").asText()).isEqualTo("D");
        assertThat(node.hasNonNull("wakePlan")).isFalse();
    }

    @Test
    void gradePriorityABeatsD() throws Exception {
        String sales = tokenOfNewSales("sales.grade.a");
        long id = createLead(sales, "高价值厂");
        String body = promote(sales, id, Map.of(
                "budget", "USD 120000",
                "deliveryDate", "2026-12-01",
                "daysSinceValidInteraction", 40,
                "consecutiveUnanswered", 5));
        JsonNode node = json.readTree(body);
        assertThat(node.get("grade").asText()).isEqualTo("A");
        // 沉默天数触发流失优先于升迁；预算/交货期只影响等级
        assertThat(node.get("stage").asText()).isEqualTo("LOST");
        assertThat(node.get("wakePlan").get("sendEnabled").asBoolean()).isFalse();
    }

    @Test
    void promoteRejectsPhoneSmugglingInEvidenceNotes() throws Exception {
        String sales = tokenOfNewSales("sales.promote.side");
        long id = createLead(sales, "侧信道升迁");
        mvc.perform(post("/api/leads/" + id + "/promote")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "notes", "回拨 +4900001112299",
                                "product", "球阀"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PLAINTEXT_SIDE_CHANNEL"));
    }

    @Test
    void contactedOnlyDoesNotPromote() throws Exception {
        String sales = tokenOfNewSales("sales.promote.contacted");
        long id = createLead(sales, "仅联系厂");
        mvc.perform(post("/api/leads/" + id + "/promote")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("notes", "已联系"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("STAGE_BLOCKED"));
    }

    private String promote(String token, long id, Map<String, Object> fields) throws Exception {
        return mvc.perform(post("/api/leads/" + id + "/promote")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new LinkedHashMap<>(fields))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private long createLead(String token, String company) throws Exception {
        int n = SEQ.incrementAndGet();
        String body = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", company,
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", "+49000022" + n,
                                "email", "stage.wire." + n + "@acme-export.test",
                                "whatsapp", "+49000033" + n,
                                "productInterest", "球阀",
                                "source", "展会"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).get("id").asLong();
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

    @TestConfiguration
    static class TestTime {
        @Bean
        AdjustableTimeSource timeSource() {
            return new AdjustableTimeSource();
        }
    }
}
