package com.ftcrm.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftcrm.common.ApiException;
import com.ftcrm.config.AppProperties;
import com.ftcrm.time.AdjustableTimeSource;
import com.ftcrm.vault.ContactCryptoService;
import com.ftcrm.web.dto.MessageDtos.ArchiveCommand;
import com.ftcrm.web.dto.MessageDtos.ArchiveStubResponse;
import com.ftcrm.web.dto.MessageDtos.ConversationListResponse;
import com.ftcrm.web.dto.MessageDtos.MessageListResponse;
import com.ftcrm.web.dto.MessageDtos.SendStubResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingRulesTest {

    private static final Set<String> DESTINATION_NAMES = Set.of(
            "phone", "email", "whatsapp", "to", "cc", "bcc", "recipient", "destination", "revealed", "plaintext");

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void rejectedDestinationIsNotCopiedIntoTheError() throws Exception {
        var node = json.readTree("""
                {"leadId":1,"templateCode":"inquiry_followup_v1","to":"+4900001112299","email":"sentinel.hans@acme-export.test"}
                """);
        assertThatThrownBy(() -> new MessageRequestParser().whatsapp(node))
                .isInstanceOf(ApiException.class)
                .hasMessage("不能提交联系方式，服务器只按客户编号发送")
                .hasMessageNotContaining("+4900001112299")
                .hasMessageNotContaining("sentinel.hans");
    }

    @Test
    void archiveCommandDropsCiphertext() throws Exception {
        var node = json.readTree("""
                {"leadId":4,"channel":"EMAIL","direction":"INBOUND","bodyCipher":"CIPHERMARKER123456","bodyIv":"IVMARKER12345678"}
                """);
        ArchiveCommand command = new MessageRequestParser().archive(node);
        assertThat(command.leadId()).isEqualTo(4L);
        assertThat(command.channel()).isEqualTo(MessageChannel.EMAIL);
        assertThat(command.toString()).doesNotContain("CIPHERMARKER123456").doesNotContain("IVMARKER12345678");
        assertThat(names(ArchiveCommand.class)).containsExactly("leadId", "channel", "direction");
    }

    @Test
    void rateLimitDoesNotNeedADestination() {
        AppProperties properties = new AppProperties();
        properties.getMessaging().setWhatsappPerHour(2);
        AdjustableTimeSource clock = new AdjustableTimeSource();
        SendRateLimiter limiter = new SendRateLimiter(clock, properties);
        limiter.acquire(7, MessageChannel.WHATSAPP);
        limiter.acquire(7, MessageChannel.WHATSAPP);
        assertThatThrownBy(() -> limiter.acquire(7, MessageChannel.WHATSAPP))
                .isInstanceOf(ApiException.class)
                .hasMessage("发送过于频繁，请稍后再试")
                .hasMessageNotContaining("+4900001112299");
        limiter.acquire(7, MessageChannel.EMAIL);
        clock.advance(Duration.ofHours(1));
        limiter.acquire(7, MessageChannel.WHATSAPP);
    }

    @Test
    void unknownProviderNameCannotSurfaceASecret() {
        AppProperties.Messaging messaging = new AppProperties().getMessaging();
        messaging.setWhatsappProvider("super-secret-wa-key");
        messaging.setEmailProvider("Resend");
        messaging.setWhatsappApiKey("super-secret-wa-api-key");
        assertThat(messaging.resolvedWhatsappProvider()).isEqualTo("unconfigured");
        assertThat(messaging.resolvedEmailProvider()).isEqualTo("resend");
        assertThat(messaging.resolvedWhatsappProvider()).doesNotContain("super-secret");
        messaging.setEmailProvider("mock");
        messaging.setWhatsappProvider("Mock");
        assertThat(messaging.resolvedEmailProvider()).isEqualTo("mock");
        assertThat(messaging.resolvedWhatsappProvider()).isEqualTo("mock");
        messaging.setEmailProvider("smtp");
        assertThat(messaging.resolvedEmailProvider()).isEqualTo("smtp");
    }

    @Test
    void responseShapesHaveNoDestinationFields() {
        for (Class<?> type : List.of(
                ProviderAck.class,
                WhatsAppSend.class,
                EmailSend.class,
                SendStubResponse.class,
                ArchiveStubResponse.class,
                ConversationListResponse.class,
                MessageListResponse.class)) {
            assertThat(names(type)).allSatisfy(name ->
                    assertThat(DESTINATION_NAMES).doesNotContain(name.toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    void stubsDoNotDependOnDecrypt() throws Exception {
        assertThat(VaultDestinationGate.class.getMethod("requireSealed", long.class, MessageChannel.class).getReturnType())
                .isEqualTo(void.class);
        for (Class<?> type : List.of(StubWhatsAppClient.class, StubEmailClient.class, MessagingService.class, VaultDestinationGate.class)) {
            assertThat(Arrays.stream(type.getDeclaredFields()).map(field -> field.getType().getName()))
                    .noneMatch(name -> name.contains("ContactCrypto"));
            assertThat(Arrays.stream(type.getDeclaredConstructors()).flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                    .noneMatch(param -> param.equals(ContactCryptoService.class));
        }
    }

    private static List<String> names(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
