package com.scriptum.backend.infrastructure.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailService")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private static final String RECIPIENT = "user@example.invalid";

    @Test
    @DisplayName("sendVerificationEmail addresses the recipient and carries the code in the body")
    void sendVerificationEmailBuildsMessage() {
        emailService.sendVerificationEmail(RECIPIENT, "the-verification-code");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();

        assertThat(message.getTo()).containsExactly(RECIPIENT);
        assertThat(message.getSubject()).isEqualTo("Email Verification");
        assertThat(message.getText()).contains("the-verification-code");
    }

    @Test
    @DisplayName("sendVerificationEmail propagates a transport failure rather than swallowing it")
    void sendVerificationEmailPropagatesTransportFailure() {
        doThrow(new MailSendException("smtp unreachable")).when(mailSender).send(any(SimpleMailMessage.class));

        // Callers need to know the mail never left: VerificationTokenService runs inside
        // a transaction, and a silent failure would leave a token nobody can act on.
        assertThatThrownBy(() -> emailService.sendVerificationEmail(RECIPIENT, "the-verification-code"))
                .isInstanceOf(MailSendException.class);
    }
}
