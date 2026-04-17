package com.ExpenseTracker.infrastructure.email;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Value("${EMAIL_USER}")
    private String username;

    @Value("${EMAIL_PASS}")
    private String password;

    @Value("${EMAIL_PORT}")
    private int port;

    @Async
    @Override
    public void sendVerificationEmail(String to, String token, String frontendUrl) {
        String link = frontendUrl + "/verify?token=" + token;
        log.info("Enviando correo de verificación a: {}", to);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.zoho.com");
        props.put("mail.smtp.port", port);

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject("Activa tu cuenta en FINZ");
            message.setContent(buildHtml(link), "text/html; charset=utf-8");
            Transport.send(message);
            log.info("Correo enviado exitosamente a: {}", to);
        } catch (Exception e) {
            log.error("Error enviando correo a: {} | Error: {}", to, e.getMessage(), e);
            throw new RuntimeException("Error enviando correo: " + e.getMessage(), e);
        }
    }

    private String buildHtml(String link) {
        return """
            <!DOCTYPE html>
            <html>
            <body style="margin:0;padding:0;background-color:#000000;font-family:'Segoe UI',sans-serif">
              <div style="max-width:520px;margin:40px auto;background:#151515;border-radius:16px;overflow:hidden">
                <div style="background:linear-gradient(135deg,#06b6d4,#3b82f6);padding:32px;text-align:center">
                  <span style="font-size:28px;font-weight:700;color:#fff;letter-spacing:-1px">⚡ FINZ</span>
                </div>
                <div style="padding:40px 32px">
                  <h2 style="color:#ffffff;font-size:22px;margin:0 0 12px">Activa tu cuenta</h2>
                  <p style="color:#9ca3af;font-size:15px;line-height:1.6;margin:0 0 32px">
                    Un administrador te ha invitado a unirte a FINZ.<br>
                    Haz clic en el botón para configurar tu perfil y comenzar.
                  </p>
                  <a href="%s"
                     style="display:block;text-align:center;background:linear-gradient(135deg,#06b6d4,#3b82f6);
                            color:#fff;text-decoration:none;padding:16px 32px;border-radius:12px;
                            font-size:15px;font-weight:600">
                    Activar mi cuenta
                  </a>
                  <p style="color:#6b7280;font-size:12px;margin:24px 0 0;text-align:center">
                    El enlace expira en 24 horas. Si no esperabas este correo, ignóralo.
                  </p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(link);
    }
}
