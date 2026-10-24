package com.ftcrm;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.domain.AppUser;
import com.ftcrm.domain.AuditEvent;
import com.ftcrm.domain.ContactVaultEntry;
import com.ftcrm.domain.Lead;
import com.ftcrm.domain.Role;
import com.ftcrm.repo.AppUserRepository;
import com.ftcrm.repo.AuditEventRepository;
import com.ftcrm.repo.ContactVaultRepository;
import com.ftcrm.repo.LeadRepository;
import com.ftcrm.time.AdjustableTimeSource;
import com.ftcrm.web.LeakProbeController;
import jakarta.persistence.EntityManager;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({VaultAccessTest.TestTime.class, LeakProbeController.class})
@Transactional
class VaultAccessTest {

    private static final String PHONE = "+4900001112299";
    private static final String EMAIL = "sentinel.hans@acme-export.test";
    private static final String LOCAL = "sentinel.hans";
    private static final String WHATSAPP = "+4900009998877";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    ContactVaultRepository vaults;
    @Autowired
    LeadRepository leadRows;
    @Autowired
    AuditEventRepository audits;
    @Autowired
    AdjustableTimeSource clock;
    @Autowired
    EntityManager entityManager;

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
    void registerForcesSalespersonEvenIfClientSendsManager() throws Exception {
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", "liwei",
                                "password", "Sales@123",
                                "displayName", "李伟",
                                "role", "MANAGER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("SALESPERSON"))
                .andExpect(jsonPath("$.user.roleLabel").value("业务员"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(body).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("SALESPERSON"))
                .andExpect(jsonPath("$.roleLabel").value("业务员"));
        assertThat(users.findByUsername("liwei").orElseThrow().getRole()).isEqualTo(Role.SALESPERSON);
    }

    @Test
    void salespersonNeverReceivesPlaintextAndVaultStoresCiphertext() throws Exception {
        String sales = tokenOfNewSales("sales.one");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String created;
        try {
            created = mvc.perform(post("/api/leads")
                            .header("Authorization", "Bearer " + sales)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(leadJson("哨兵阀门厂")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.companyName").value("哨兵阀门厂"))
                    .andExpect(jsonPath("$.contact.emailMasked").value("s***@acme-export.test"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            root.detachAppender(logs);
        }
        assertNoPlaintext(created);
        List<String> logged = new ArrayList<>();
        for (ILoggingEvent event : logs.list) {
            logged.add(event.getFormattedMessage());
            assertThat(event.getFormattedMessage()).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(WHATSAPP);
        }

        long id = json.readTree(created).get("id").asLong();
        ContactVaultEntry vault = vaults.findByLeadId(id).orElseThrow();
        assertThat(vault.getEmailCipher()).doesNotContain(EMAIL).doesNotContain(LOCAL);
        assertThat(vault.getPhoneCipher()).doesNotContain(PHONE);
        assertThat(vault.getWhatsappCipher()).doesNotContain(WHATSAPP);
        assertThat(new String(Base64.getDecoder().decode(vault.getEmailCipher()), StandardCharsets.ISO_8859_1))
                .doesNotContain(EMAIL).doesNotContain(LOCAL);
        assertThat(new String(Base64.getDecoder().decode(vault.getPhoneCipher()), StandardCharsets.ISO_8859_1))
                .doesNotContain(PHONE);
        assertThat(vault.getEmailMasked()).doesNotContain(LOCAL);
        assertThat(vault.getPhoneHash()).hasSize(64).doesNotContain(PHONE);
        assertHashHidden(created, vault);
        assertHashHidden(String.join("\n", logged), vault);

        String listed = mvc.perform(get("/api/leads").header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(listed);
        assertHashHidden(listed, vault);
        String detail = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(detail);
        assertHashHidden(detail, vault);

        mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"客户要求核对合同条款\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", "Bearer " + sales))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/leads/export").header("Authorization", "Bearer " + sales))
                .andExpect(status().isNotFound());
        String exported = mvc.perform(post("/api/leads/export")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("导出不应存在")))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(exported);
        mvc.perform(post("/api/leads/" + id + "/unmask/end")
                        .header("Authorization", "Bearer " + sales))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerUnmaskIsTimeBoxedAuditedAndHiddenFromLists() throws Exception {
        String sales = tokenOfNewSales("sales.two");
        String manager = login("manager", "Manager@123");
        String created = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("哨兵泵业")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = json.readTree(created).get("id").asLong();

        String before = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(before);

        String revealed = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"客户要求核对合同条款\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.revealed.email").value(EMAIL))
                .andExpect(jsonPath("$.revealed.phone").value(PHONE))
                .andExpect(jsonPath("$.unmask.active").value(true))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(revealed).contains(WHATSAPP);
        ContactVaultEntry vault = vaults.findByLeadId(id).orElseThrow();
        assertHashHidden(revealed, vault);

        String listed = mvc.perform(get("/api/leads").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(listed);
        assertHashHidden(listed, vault);

        String salesDuring = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(salesDuring);
        assertHashHidden(salesDuring, vault);

        String audit = mvc.perform(get("/api/audit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(audit).contains("UNMASK_START").contains("客户要求核对合同条款");
        assertNoPlaintext(audit);
        assertHashHidden(audit, vault);
        for (AuditEvent event : audits.findAll()) {
            String blob = String.valueOf(event.getReason()) + String.valueOf(event.getMetadataJson());
            assertThat(blob).doesNotContain(EMAIL).doesNotContain(PHONE).doesNotContain(WHATSAPP);
        }

        String ended = mvc.perform(post("/api/leads/" + id + "/unmask/end")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(ended);

        mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"再次核对目的港条款\"}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(16));
        String expired = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(expired);
        String auditAfter = mvc.perform(get("/api/audit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(auditAfter).contains("UNMASK_EXPIRE");
        assertNoPlaintext(auditAfter);
        assertHashHidden(auditAfter, vault);
    }

    @Test
    void repeatUnmaskDoesNotExtendTtl() throws Exception {
        String sales = tokenOfNewSales("sales.ttl");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "期限阀门");
        String first = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第一次核对合同条款\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed.phone").value(PHONE))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String expiresAt = json.readTree(first).get("unmask").get("expiresAt").asText();
        clock.advance(Duration.ofMinutes(5));
        String second = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"第二次仍在原窗口内\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed.email").value(EMAIL))
                .andExpect(jsonPath("$.unmask.expiresAt").value(expiresAt))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json.readTree(second).get("unmask").get("expiresAt").asText()).isEqualTo(expiresAt);
        clock.advance(Duration.ofMinutes(10));
        String expired = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(expired);
        String audit = mvc.perform(get("/api/audit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(audit).contains("UNMASK_EXPIRE");
        assertNoPlaintext(audit);
    }

    @Test
    void duplicateAndImportErrorsDoNotEchoPlaintext() throws Exception {
        String sales = tokenOfNewSales("sales.dup");
        String first = createLeadBody(sales, Map.of(
                "companyName", "跨字段甲",
                "country", "德国",
                "contactName", "汉斯",
                "whatsapp", PHONE,
                "email", "alpha.unique@acme-export.test"));
        assertThat(first).doesNotContain("alpha.unique").doesNotContain(PHONE);
        String cross = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "跨字段乙",
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", PHONE,
                                "email", "beta.unique@acme-export.test"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_CONTACT"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cross).doesNotContain(PHONE).doesNotContain("alpha.unique").doesNotContain("beta.unique");

        mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "格式甲",
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", "+49 1111 2222 333",
                                "email", "format.one@acme-export.test"))))
                .andExpect(status().isOk());
        String formatted = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "格式乙",
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", "+4911112222333",
                                "email", "format.two@acme-export.test"))))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(formatted).doesNotContain("+49 1111 2222 333").doesNotContain("+4911112222333")
                .doesNotContain("format.one").doesNotContain("format.two");

        mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "大小写甲",
                                "country", "德国",
                                "contactName", "汉斯",
                                "email", "Case.Sensitive@Acme-Export.Test"))))
                .andExpect(status().isOk());
        String cased = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "大小写乙",
                                "country", "德国",
                                "contactName", "汉斯",
                                "email", "case.sensitive@acme-export.test"))))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(cased).doesNotContain("Case.Sensitive").doesNotContain("case.sensitive");

        String invalid = mvc.perform(post("/api/leads/import")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("leads", List.of(Map.of(
                                "companyName", "坏邮箱",
                                "country", "德国",
                                "contactName", "汉斯",
                                "email", "leak-sentinel-mailbox"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.imported").value(0))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(invalid).doesNotContain("leak-sentinel-mailbox");
    }

    @Test
    void sideChannelVariantsAreRejectedAndNotEchoed() throws Exception {
        String sales = tokenOfNewSales("sales.side");
        String manager = login("manager", "Manager@123");
        assertRejectedCreate(sales, "全角数字", "请联系１２３４５６７８９０１", "１２３４５６７８９０１", "12345678901");
        assertRejectedCreate(sales, "零宽数字", "编号1234\u200b56789012", "1234\u200b56789012", "123456789012");
        assertRejectedCreate(sales, "分隔电话", "电话 +49 000 111 2299", "+49 000 111 2299", "490001112299");
        assertRejectedCreate(sales, "哈希截断", "索引 abcdefabcdefabcd", "abcdefabcdefabcd");

        String kept = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "日期阀门",
                                "country", "德国",
                                "contactName", "汉斯",
                                "email", "date.keep@acme-export.test",
                                "productInterest", "不锈钢球阀 DN50-1234-5678",
                                "notes", "2024-10-23 广交会认识"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("2024-10-23 广交会认识"))
                .andExpect(jsonPath("$.productInterest").value("不锈钢球阀 DN50-1234-5678"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(kept).doesNotContain("date.keep");

        String mixed = mvc.perform(post("/api/leads/import")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leads\":["
                                + leadJson("正常导入") + ","
                                + json.writeValueAsString(Map.of(
                                        "companyName", "夹带导入",
                                        "country", "德国",
                                        "contactName", "汉斯",
                                        "email", "import.bad@acme-export.test",
                                        "notes", "索引 abcdefabcdefabcd"))
                                + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(mixed).contains("正常导入").doesNotContain("abcdefabcdefabcd").doesNotContain("import.bad");

        String reason = mvc.perform(post("/api/leads/1/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"客户电话13800138000请核对\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(reason).doesNotContain("13800138000");
        mvc.perform(post("/api/leads/1/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"太短\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void poisonedEchoFieldsStayHiddenAfterRead() throws Exception {
        String sales = tokenOfNewSales("sales.poison");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "投毒前公司");
        Lead lead = leadRows.findById(id).orElseThrow();
        lead.setNotes("哈希截断 abcdefabcdefabcd");
        lead.setSource("+49 000 111 2299");
        leadRows.saveAndFlush(lead);

        String salesDetail = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("已拦截"))
                .andExpect(jsonPath("$.source").value("已拦截"))
                .andExpect(jsonPath("$.companyName").value("投毒前公司"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(salesDetail);
        assertThat(salesDetail).doesNotContain("abcdefabcdefabcd").doesNotContain("+49 000 111 2299")
                .doesNotContain("490001112299");

        String revealed = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"核对投毒字段是否仍被拦住\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed.phone").value(PHONE))
                .andExpect(jsonPath("$.notes").value("已拦截"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(revealed).contains(EMAIL).doesNotContain("abcdefabcdefabcd").doesNotContain("+49 000 111 2299");
        String salesDuring = mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(salesDuring);
        assertThat(salesDuring).doesNotContain("abcdefabcdefabcd");
    }

    @Test
    void queryAndPathDoNotEchoContactsOrHashFragments() throws Exception {
        String sales = tokenOfNewSales("sales.query");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            assertQueryRejected("/api/health?q=%31%32%33%34%35%36%37%38%0Ahidden", "12345678", "%31%32%33%34%35%36%37%38");
            assertQueryRejected("/api/health?q=%2531%2532%2533%2534%2535%2536%2537%2538", "12345678", "%2531");
            assertQueryRejected("/api/health?q=sentinel.hans%40acme-export.test", EMAIL, "sentinel.hans", "%40");
            assertQueryRejected("/api/health?q=abcdefabcdefabcd", "abcdefabcdefabcd");
            String fullwidth = mvc.perform(get("/api/health").param("q", "１２３４５６７８９０"))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(fullwidth).doesNotContain("１２３４５６７８９０").doesNotContain("1234567890");
        } finally {
            root.detachAppender(logs);
        }
        for (ILoggingEvent event : logs.list) {
            assertThat(event.getFormattedMessage())
                    .doesNotContain("12345678")
                    .doesNotContain(EMAIL)
                    .doesNotContain("abcdefabcdefabcd")
                    .doesNotContain("sentinel.hans");
        }

        String missing = mvc.perform(get("/api/missing/abcdefabcdefabcd").header("Authorization", "Bearer " + sales))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(missing).doesNotContain("abcdefabcdefabcd");
        String phoneId = mvc.perform(get("/api/leads/13800138000").header("Authorization", "Bearer " + sales))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(phoneId).doesNotContain("13800138000");
        String overflow = mvc.perform(get("/api/leads/123456789012345678901234").header("Authorization", "Bearer " + sales))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(overflow).doesNotContain("123456789012345678901234");
    }

    @Test
    void salespersonProbeDropsHashKeyAndHashFragment() throws Exception {
        String sales = tokenOfNewSales("sales.hash");
        String hashBody = mvc.perform(get("/api/test/leak-hash").header("Authorization", "Bearer " + sales))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(hashBody).doesNotContain("abcdefabcdefabcd").contains("PLAINTEXT_BLOCKED");
        String notesBody = mvc.perform(get("/api/test/leak-notes").header("Authorization", "Bearer " + sales))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(notesBody).doesNotContain("abcdefabcdefabcd").contains("PLAINTEXT_BLOCKED");
    }

    @Test
    void importDoesNotEchoPlaintextAndDuplicateIsCounted() throws Exception {
        String sales = tokenOfNewSales("sales.three");
        String body = mvc.perform(post("/api/leads/import")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leads\":[" + leadJson("导入阀门") + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(body);

        mvc.perform(post("/api/leads/import")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leads\":[" + leadJson("导入阀门重复") + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicated").value(1))
                .andExpect(jsonPath("$.imported").value(0));
    }

    @Test
    void notesCannotSmuggleAPhoneNumber() throws Exception {
        String sales = tokenOfNewSales("sales.four");
        String body = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "companyName", "备注侧信道",
                                "country", "德国",
                                "contactName", "汉斯",
                                "phone", PHONE,
                                "notes", "请打 " + PHONE))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(PHONE);
    }

    @Test
    void salespersonResponseGuardDropsProbePayload() throws Exception {
        String sales = tokenOfNewSales("sales.five");
        String body = mvc.perform(get("/api/test/leak").header("Authorization", "Bearer " + sales))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("should-not-pass").contains("PLAINTEXT_BLOCKED");
    }

    @Test
    void queryStringCannotCarryAPhone() throws Exception {
        String body = mvc.perform(get("/api/health").param("q", "12345678"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("12345678");
    }

    @Test
    void salespersonCannotReadAnotherOwnersLead() throws Exception {
        String sales = tokenOfNewSales("sales.six");
        String other = tokenOfNewSales("sales.seven");
        MvcResult created = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + sales)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(leadJson("仅自己可见")))
                .andExpect(status().isOk())
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asLong();
        mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        String listed = mvc.perform(get("/api/leads").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(listed).doesNotContain("仅自己可见");
    }

@Test
    void shortOrSmuggledUnmaskReasonIsRejectedWithoutEcho() throws Exception {
        String sales = tokenOfNewSales("sales.reason");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "原因探针");

        String tooShort = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"一二三四五六七\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(tooShort).doesNotContain("一二三四五六七").doesNotContain(PHONE).doesNotContain(EMAIL);

        String smuggled = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "核对" + PHONE))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PLAINTEXT_SIDE_CHANNEL"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(smuggled).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain("4900001112299");

        String emailReason = mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", "核对" + EMAIL))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PLAINTEXT_SIDE_CHANNEL"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(emailReason).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain("@");

        assertMaskedLeadShape(detail(manager, id));
        assertThat(json.readTree(detail(manager, id)).get("unmask").get("active").asBoolean()).isFalse();
        assertThat(count("select count(*) from unmask_session")).isZero();
        assertThat(count("select count(*) from audit_event where action = 'UNMASK_START'")).isZero();
        assertAuditStoreClean();
    }

@Test
    void validReasonRevealsDetailForFifteenMinutesOnly() throws Exception {
        String sales = tokenOfNewSales("sales.window");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "十五分钟探针");

        String opened = unmask(manager, id, "客户争议到港数量，需核对原话");
        JsonNode openedJson = json.readTree(opened);
        assertThat(openedJson.get("revealed").get("phone").asText()).isEqualTo(PHONE);
        assertThat(openedJson.get("revealed").get("email").asText()).isEqualTo(EMAIL);
        assertThat(openedJson.get("revealed").get("whatsapp").asText()).isEqualTo(WHATSAPP);
        assertThat(openedJson.get("unmask").get("active").asBoolean()).isTrue();
        assertThat(Instant.parse(openedJson.get("unmask").get("expiresAt").asText()))
                .isEqualTo(Instant.parse("2026-10-23T08:15:00Z"));
        assertThat(openedJson.has("phone")).isFalse();
        assertThat(openedJson.has("email")).isFalse();
        assertThat(openedJson.has("whatsapp")).isFalse();
        assertThat(openedJson.get("contact").get("emailMasked").asText()).doesNotContain(LOCAL);
        long sessionId = openedJson.get("unmask").get("sessionId").asLong();

        String managerDetail = detail(manager, id);
        assertThat(json.readTree(managerDetail).get("revealed").get("phone").asText()).isEqualTo(PHONE);
        assertThat(json.readTree(managerDetail).get("revealed").get("email").asText()).isEqualTo(EMAIL);
        assertThat(json.readTree(managerDetail).get("revealed").get("whatsapp").asText()).isEqualTo(WHATSAPP);
        assertMaskedLeadShape(list(manager));
        String salesDetail = detail(sales, id);
        assertMaskedLeadShape(salesDetail);
        assertThat(json.readTree(salesDetail).get("unmask").get("active").asBoolean()).isFalse();
        assertMaskedLeadShape(list(sales));

        clock.advance(Duration.ofMinutes(5));
        JsonNode again = json.readTree(unmask(manager, id, "再次核对但窗口不应延长"));
        assertThat(again.get("unmask").get("sessionId").asLong()).isEqualTo(sessionId);
        assertThat(Instant.parse(again.get("unmask").get("expiresAt").asText()))
                .isEqualTo(Instant.parse("2026-10-23T08:15:00Z"));

        clock.advance(Duration.ofMinutes(9));
        String still = detail(manager, id);
        assertThat(json.readTree(still).get("revealed").get("phone").asText()).isEqualTo(PHONE);
        assertThat(json.readTree(still).get("revealed").get("email").asText()).isEqualTo(EMAIL);
        assertThat(Instant.parse(json.readTree(still).get("unmask").get("expiresAt").asText()))
                .isEqualTo(Instant.parse("2026-10-23T08:15:00Z"));
        assertMaskedLeadShape(list(manager));
        assertMaskedLeadShape(detail(sales, id));

        clock.advance(Duration.ofMinutes(2));
        String expired = detail(manager, id);
        assertMaskedLeadShape(expired);
        assertThat(json.readTree(expired).get("unmask").get("active").asBoolean()).isFalse();
        String audit = assertAuditApi(manager, "UNMASK_START", "UNMASK_EXPIRE");
        assertThat(audit).contains("已到期限").doesNotContain("UNMASK_END");
        assertAuditStoreClean();
        assertThat(count("select count(*) from unmask_session where status = 'EXPIRED'")).isEqualTo(1);
    }

@Test
    void endingUnmaskRemovesPlaintextAndAuditsStartAndEnd() throws Exception {
        String sales = tokenOfNewSales("sales.endgate");
        String manager = login("manager", "Manager@123");
        long id = createLead(sales, "结束揭盲探针");
        unmask(manager, id, "客户要求核对合同条款");
        assertMaskedLeadShape(list(manager));
        assertMaskedLeadShape(detail(sales, id));
        assertMaskedLeadShape(list(sales));

        String ended = mvc.perform(post("/api/leads/" + id + "/unmask/end")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unmask.active").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertMaskedLeadShape(ended);
        String after = detail(manager, id);
        assertMaskedLeadShape(after);
        assertThat(json.readTree(after).get("unmask").get("active").asBoolean()).isFalse();
        assertMaskedLeadShape(detail(sales, id));

        String audit = assertAuditApi(manager, "UNMASK_START", "UNMASK_END");
        assertThat(audit).contains("主动结束").contains("客户要求核对合同条款");
        assertAuditStoreClean();
        assertThat(count("select count(*) from unmask_session where status = 'ENDED'")).isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'UNMASK_EXPIRE'")).isZero();
    }

@Test
    void exportEndpointDoesNotReturnLeadData() throws Exception {
        String sales = tokenOfNewSales("sales.export");
        String manager = login("manager", "Manager@123");
        createLead(sales, "不可导出客户");
        assertExportBlocked(sales, 404);
        assertExportBlocked(manager, 404);
        assertExportBlocked(null, 401);
    }


    private long createLead(String token, String company) throws Exception {
        return json.readTree(createLeadBody(token, leadJson(company))).get("id").asLong();
    }

    private String createLeadBody(String token, Map<String, String> fields) throws Exception {
        return createLeadBody(token, json.writeValueAsString(fields));
    }

    private String createLeadBody(String token, String body) throws Exception {
        String created = mvc.perform(post("/api/leads")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(created);
        return created;
    }

    private void assertRejectedCreate(String token, String company, String notes, String... secrets) throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        String body;
        try {
            body = mvc.perform(post("/api/leads")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of(
                                    "companyName", company,
                                    "country", "德国",
                                    "contactName", "汉斯",
                                    "email", "side.channel@acme-export.test",
                                    "notes", notes))))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        } finally {
            root.detachAppender(logs);
        }
        assertThat(body).doesNotContain("side.channel");
        for (String secret : secrets) {
            assertThat(body).doesNotContain(secret);
        }
        for (ILoggingEvent event : logs.list) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain("side.channel@acme-export.test");
            for (String secret : secrets) {
                assertThat(message).doesNotContain(secret);
            }
        }
    }

    private void assertQueryRejected(String url, String... secrets) throws Exception {
        MvcResult result = mvc.perform(get(url))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (String secret : secrets) {
            assertThat(body).doesNotContain(secret);
            for (String name : result.getResponse().getHeaderNames()) {
                String value = result.getResponse().getHeader(name);
                if (value != null) {
                    assertThat(value).doesNotContain(secret);
                }
            }
        }
    }

    private static void assertHashHidden(String body, ContactVaultEntry vault) {
        for (String hash : List.of(vault.getPhoneHash(), vault.getEmailHash(), vault.getWhatsappHash())) {
            assertThat(hash).hasSize(64);
            assertThat(body).doesNotContain(hash).doesNotContain(hash.substring(0, 16));
        }
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

    private String leadJson(String company) throws Exception {
        return json.writeValueAsString(Map.of(
                "companyName", company,
                "country", "德国",
                "contactName", "汉斯",
                "phone", PHONE,
                "email", EMAIL,
                "whatsapp", WHATSAPP,
                "productInterest", "球阀",
                "source", "展会"));
    }

    private void assertNoPlaintext(String body) throws Exception {
        assertThat(body).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
        assertThat(body).doesNotContain("\"revealed\"").doesNotContain("\"phone\"").doesNotContain("\"email\"").doesNotContain("\"whatsapp\"");
        JsonNode node = json.readTree(body);
        assertThat(node.toString()).doesNotContain(PHONE);
        assertForbiddenContactKeysAbsent(node);
    }

    private String detail(String token, long id) throws Exception {
        return mvc.perform(get("/api/leads/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String list(String token) throws Exception {
        return mvc.perform(get("/api/leads").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String unmask(String manager, long id, String reason) throws Exception {
        return mvc.perform(post("/api/leads/" + id + "/unmask")
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("reason", reason))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertForbidden(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mvc.perform(request)
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
    }

    private void assertExportBlocked(String token, int expected) throws Exception {
        var request = get("/api/leads/export");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain(PHONE)
                .doesNotContain(EMAIL)
                .doesNotContain(WHATSAPP)
                .doesNotContain(LOCAL)
                .doesNotContain("不可导出客户");
    }

    private String assertAuditApi(String manager, String... actions) throws Exception {
        String body = mvc.perform(get("/api/audit").header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertNoPlaintext(body);
        assertThat(body).doesNotContain("metadata");
        JsonNode root = json.readTree(body);
        Set<String> found = new HashSet<>();
        root.forEach(event -> {
            found.add(event.get("action").asText());
            Set<String> keys = new HashSet<>();
            event.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).contains("action").doesNotContain(
                    "metadata", "metadataJson", "phone", "email", "whatsapp", "revealed");
            if (event.hasNonNull("reason")) {
                assertThat(event.get("reason").asText())
                        .doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(WHATSAPP).doesNotContain(LOCAL);
            }
        });
        assertThat(found).contains(actions);
        return body;
    }

    private void assertForbiddenContactKeysAbsent(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(this::assertForbiddenContactKeysAbsent);
            return;
        }
        node.fieldNames().forEachRemaining(name -> {
            assertThat(name.toLowerCase(Locale.ROOT)).isNotIn("phone", "email", "whatsapp", "revealed");
            assertForbiddenContactKeysAbsent(node.get(name));
        });
    }

    private void assertMaskedLeadShape(String body) throws Exception {
        assertNoPlaintext(body);
        JsonNode root = json.readTree(body);
        if (root.isArray()) {
            assertThat(root).isNotEmpty();
            root.forEach(this::assertOneMaskedLead);
            return;
        }
        if (root.has("leads")) {
            root.get("leads").forEach(this::assertOneMaskedLead);
            return;
        }
        assertOneMaskedLead(root);
    }

    private void assertOneMaskedLead(JsonNode lead) {
        assertThat(lead.has("revealed")).isFalse();
        assertThat(lead.has("phone")).isFalse();
        assertThat(lead.has("email")).isFalse();
        assertThat(lead.has("whatsapp")).isFalse();
        JsonNode contact = lead.get("contact");
        assertThat(contact).isNotNull();
        Set<String> keys = new HashSet<>();
        contact.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("phoneMasked", "emailMasked", "whatsappMasked");
        assertThat(contact.get("phoneMasked").asText()).isEqualTo("+490****2299").doesNotContain(PHONE);
        assertThat(contact.get("emailMasked").asText()).isEqualTo("s***@acme-export.test").doesNotContain(LOCAL);
        assertThat(contact.get("whatsappMasked").asText()).isEqualTo("+490****8877").doesNotContain(WHATSAPP);
    }

    private void assertVaultAndLeadStored(long leadId) {
        entityManager.flush();
        assertLeadTableHasNoContactColumns();
        Object[] vault = singleRow("""
                select phone_cipher, email_cipher, whatsapp_cipher,
                       phone_masked, email_masked, whatsapp_masked,
                       phone_hash, email_hash, whatsapp_hash,
                       phone_iv, email_iv, whatsapp_iv
                from contact_vault where lead_id = :id
                """, leadId);
        assertCipher(text(vault[0]));
        assertCipher(text(vault[1]));
        assertThat(text(vault[1])).doesNotContain("@");
        assertCipher(text(vault[2]));
        assertPhoneMaskedColumn(text(vault[3]));
        assertEmailMaskedColumn(text(vault[4]));
        assertPhoneMaskedColumn(text(vault[5]));
        assertHash(text(vault[6]));
        assertHash(text(vault[7]));
        assertHash(text(vault[8]));
        assertCipher(text(vault[9]));
        assertCipher(text(vault[10]));
        assertCipher(text(vault[11]));

        Object[] lead = singleRow("""
                select company_name, country, contact_name, product_interest, source, notes
                from lead_record where id = :id
                """, leadId);
        StringBuilder blob = new StringBuilder();
        for (Object value : lead) {
            if (value != null) {
                blob.append(value);
            }
        }
        assertThat(blob.toString()).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
    }

    private void assertLeadTableHasNoContactColumns() {
        @SuppressWarnings("unchecked")
        List<Object> raw = entityManager.createNativeQuery("""
                select column_name from information_schema.columns
                where lower(table_name) = 'lead_record'
                """).getResultList();
        List<String> columns = raw.stream().map(value -> value.toString().toLowerCase(Locale.ROOT)).toList();
        assertThat(columns).contains("company_name", "notes", "contact_name");
        assertThat(columns).doesNotContain(
                "phone", "email", "whatsapp", "revealed",
                "phone_cipher", "email_cipher", "whatsapp_cipher",
                "phone_masked", "email_masked", "whatsapp_masked");
    }

    private void assertAuditStoreClean() {
        entityManager.flush();
        @SuppressWarnings("unchecked")
        List<Object[]> events = entityManager.createNativeQuery(
                "select reason, metadata_json from audit_event").getResultList();
        assertThat(events).isNotEmpty();
        for (Object[] row : events) {
            String blob = textOrEmpty(row[0]) + textOrEmpty(row[1]);
            assertThat(blob).doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
        }
        @SuppressWarnings("unchecked")
        List<Object> reasons = entityManager.createNativeQuery("select reason from unmask_session").getResultList();
        for (Object reason : reasons) {
            assertThat(String.valueOf(reason))
                    .doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
        }
    }

    @SuppressWarnings("unchecked")
    private Object[] singleRow(String sql, long leadId) {
        List<Object[]> rows = entityManager.createNativeQuery(sql).setParameter("id", leadId).getResultList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private int count(String sql) {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery(sql).getSingleResult()).intValue();
    }

    private static void assertCipher(String value) {
        assertThat(value).isNotBlank()
                .doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP)
                .doesNotContain("@")
                .isNotEqualTo(PHONE).isNotEqualTo(EMAIL).isNotEqualTo(WHATSAPP);
    }

    private static void assertPhoneMaskedColumn(String value) {
        assertThat(value).isNotBlank()
                .doesNotContain(PHONE).doesNotContain(WHATSAPP).doesNotContain(EMAIL).doesNotContain(LOCAL)
                .doesNotMatch(".*\\d{8,}.*");
    }

    private static void assertEmailMaskedColumn(String value) {
        assertThat(value).isNotBlank()
                .contains("@")
                .doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(PHONE).doesNotContain(WHATSAPP);
    }

    private static void assertHash(String value) {
        assertThat(value).matches("[0-9a-f]{64}")
                .doesNotContain(PHONE).doesNotContain(EMAIL).doesNotContain(LOCAL).doesNotContain(WHATSAPP);
    }

    private static String text(Object value) {
        assertThat(value).isNotNull();
        return value.toString();
    }

    private static String textOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    @TestConfiguration
    static class TestTime {
        @Bean
        AdjustableTimeSource timeSource() {
            return new AdjustableTimeSource();
        }
    }
}
