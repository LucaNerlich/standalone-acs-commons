package com.adobe.acs.email;

import com.day.cq.commons.mail.MailTemplate;
import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.HtmlEmail;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    private static final String TEMPLATE_PATH = "/apps/example/emailTemplates/test.html";

    @Mock
    private MessageGatewayService messageGatewayService;

    @Mock
    private ResourceResolverFactory resourceResolverFactory;

    @InjectMocks
    private EmailServiceImpl underTest;

    @Test
    void sendEmail_appliesReservedParamsAndSendsToRecipient() throws Exception {
        final MailTemplate mailTemplateMock = mock(MailTemplate.class);
        final MessageGateway<Email> messageGateway = mock(MessageGateway.class);
        doReturn(new HtmlEmail()).when(mailTemplateMock).getEmail(any(Map.class), any());
        doReturn(messageGateway).when(messageGatewayService).getGateway(any());
        doReturn(true).when(messageGateway).handles(any());
        stubResourceResolver();

        final Map<String, String> params = new HashMap<>();
        params.put(EmailConstants.SENDER_EMAIL_ADDRESS, "sender@example.com");
        params.put(EmailConstants.SENDER_NAME, "Sender Name");
        params.put(EmailConstants.SUBJECT, "Overridden subject");
        params.put(EmailConstants.BOUNCE_ADDRESS, "bounce@example.com");

        try (MockedStatic<MailTemplate> mailTemplateStatic = mockStatic(MailTemplate.class)) {
            mailTemplateStatic.when(() -> MailTemplate.create(anyString(), any())).thenReturn(mailTemplateMock);

            final List<String> failures = underTest.sendEmail(TEMPLATE_PATH, params, "recipient@example.com");

            assertTrue(failures.isEmpty());
        }

        final ArgumentCaptor<Email> emailCaptor = ArgumentCaptor.forClass(Email.class);
        verify(messageGateway).send(emailCaptor.capture());
        final Email sentEmail = emailCaptor.getValue();
        assertEquals("Overridden subject", sentEmail.getSubject());
        assertEquals("sender@example.com", sentEmail.getFromAddress().getAddress());
        assertEquals("Sender Name", sentEmail.getFromAddress().getPersonal());
        assertEquals("bounce@example.com", sentEmail.getBounceAddress());
    }

    @Test
    void sendEmail_skipsInvalidAddressWithoutCountingItAsFailure() throws Exception {
        final MailTemplate mailTemplateMock = mock(MailTemplate.class);
        final MessageGateway<Email> messageGateway = mock(MessageGateway.class);
        doReturn(new HtmlEmail()).when(mailTemplateMock).getEmail(any(Map.class), any());
        doReturn(messageGateway).when(messageGatewayService).getGateway(any());
        doReturn(true).when(messageGateway).handles(any());
        stubResourceResolver();

        try (MockedStatic<MailTemplate> mailTemplateStatic = mockStatic(MailTemplate.class)) {
            mailTemplateStatic.when(() -> MailTemplate.create(anyString(), any())).thenReturn(mailTemplateMock);

            final List<String> failures = underTest.sendEmail(TEMPLATE_PATH, new HashMap<>(), "Invalid <bad", "valid@example.com");
            assertTrue(failures.isEmpty());
        }

        verify(messageGateway).send(any());
    }

    @Test
    void sendEmail_throwsOnEmptyRecipients() {
        assertThrows(IllegalArgumentException.class, () -> underTest.sendEmail(TEMPLATE_PATH, new HashMap<>()));
    }

    @Test
    void sendEmail_returnsAllAsFailedWhenTemplateResolutionFails() throws Exception {
        when(resourceResolverFactory.getServiceResourceResolver(any())).thenThrow(new LoginException("no service user configured in test"));

        final List<String> failures = underTest.sendEmail(TEMPLATE_PATH, new HashMap<>(), "recipient@example.com");

        assertEquals(List.of("recipient@example.com"), failures);
        // no need to verify messageGatewayService if we cannot resolve a template
        verifyNoInteractions(messageGatewayService);
    }

    @Test
    void sendEmail_returnsAllAsFailedWhenGatewayDoesNotHandleMailType() throws Exception {
        final MailTemplate mailTemplateMock = mock(MailTemplate.class);
        final MessageGateway<Email> messageGateway = mock(MessageGateway.class);
        doReturn(messageGateway).when(messageGatewayService).getGateway(any());
        doReturn(false).when(messageGateway).handles(any());
        stubResourceResolver();

        try (MockedStatic<MailTemplate> mailTemplateStatic = mockStatic(MailTemplate.class)) {
            mailTemplateStatic.when(() -> MailTemplate.create(anyString(), any())).thenReturn(mailTemplateMock);

            final List<String> failures = underTest.sendEmail(TEMPLATE_PATH, new HashMap<>(), "recipient@example.com");

            assertEquals(List.of("recipient@example.com"), failures);
        }

        verify(messageGateway, never()).send(any());
    }

    @Test
    void sendEmail_attachesSuppliedAttachmentsAndForcesHtmlEmail() throws Exception {
        // deliberately not ending in ".html", to prove a non-empty attachment list forces HtmlEmail anyway
        final String plainTextTemplatePath = "/apps/example/emailTemplates/test.txt";
        final MailTemplate mailTemplateMock = mock(MailTemplate.class);
        final HtmlEmail spiedEmail = spy(new HtmlEmail());
        final MessageGateway<Email> messageGateway = mock(MessageGateway.class);
        doReturn(spiedEmail).when(mailTemplateMock).getEmail(any(Map.class), eq(HtmlEmail.class));
        doReturn(messageGateway).when(messageGatewayService).getGateway(HtmlEmail.class);
        doReturn(true).when(messageGateway).handles(HtmlEmail.class);
        stubResourceResolver();

        final DataSource dataSource = new ByteArrayDataSource("attachment content".getBytes(), "text/plain");
        final MailAttachment attachment = new MailAttachment("report.txt", dataSource);

        try (MockedStatic<MailTemplate> mailTemplateStatic = mockStatic(MailTemplate.class)) {
            mailTemplateStatic.when(() -> MailTemplate.create(anyString(), any())).thenReturn(mailTemplateMock);

            final List<String> failures = underTest.sendEmail(plainTextTemplatePath, new HashMap<>(),
                    List.of(attachment), "recipient@example.com");

            assertTrue(failures.isEmpty());
        }

        verify(spiedEmail).attach(dataSource, "report.txt", null);
        verify(messageGatewayService).getGateway(HtmlEmail.class);
    }

    @Test
    void sendEmail_withoutAttachments_neverCallsAttach() throws Exception {
        final MailTemplate mailTemplateMock = mock(MailTemplate.class);
        final HtmlEmail spiedEmail = spy(new HtmlEmail());
        final MessageGateway<Email> messageGateway = mock(MessageGateway.class);
        doReturn(spiedEmail).when(mailTemplateMock).getEmail(any(Map.class), any());
        doReturn(messageGateway).when(messageGatewayService).getGateway(any());
        doReturn(true).when(messageGateway).handles(any());
        stubResourceResolver();

        try (MockedStatic<MailTemplate> mailTemplateStatic = mockStatic(MailTemplate.class)) {
            mailTemplateStatic.when(() -> MailTemplate.create(anyString(), any())).thenReturn(mailTemplateMock);

            underTest.sendEmail(TEMPLATE_PATH, new HashMap<>(), "recipient@example.com");
        }

        verify(spiedEmail, never()).attach(any(DataSource.class), any(), any());
    }

    private void stubResourceResolver() throws LoginException {
        final ResourceResolver resourceResolver = mock(ResourceResolver.class);
        when(resourceResolverFactory.getServiceResourceResolver(any())).thenReturn(resourceResolver);
    }
}
