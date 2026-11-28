package edu.usc.marketplace.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends email with Jakarta Mail (section 3: verification codes).
 *
 * Settings come from WEB-INF/config.properties:
 *   mail.host=smtp.gmail.com
 *   mail.port=587
 *   mail.user=youraccount@gmail.com
 *   mail.password=your app password
 *   mail.from=USC Marketplace <youraccount@gmail.com>     (optional; default: mail.user)
 *
 * If mail.host is missing, nothing is sent. The email is written to the Tomcat console instead, so the
 * app can still be run and tested on a laptop without an email account.
 */
public final class Mailer {
    private static final Logger LOG = Logger.getLogger(Mailer.class.getName());
    private static final String TIMEOUT_MS = "10000";

    private static volatile Mailer instance = new Mailer(new Properties());

    private final Session session;   // null when email is not set up
    private final String from;

    public Mailer(Properties config) {
        String host = config.getProperty("mail.host", "").trim();
        String user = config.getProperty("mail.user", "").trim();
        String password = config.getProperty("mail.password", "");
        from = config.getProperty("mail.from", user).trim();

        if (host.isEmpty()) {
            session = null;
            return;
        }
        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", config.getProperty("mail.port", "587").trim());
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        p.put("mail.smtp.timeout", TIMEOUT_MS);
        p.put("mail.smtp.writetimeout", TIMEOUT_MS);
        if (user.isEmpty()) {
            session = Session.getInstance(p);
        } else {
            p.put("mail.smtp.auth", "true");
            session = Session.getInstance(p, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });
        }
    }

    /** Called once by AppStartup with the settings from config.properties. */
    public static void init(Mailer mailer) {
        instance = mailer;
    }

    public static Mailer get() {
        return instance;
    }

    /** False when mail.host is missing, so emails are only written to the console. */
    public boolean isConfigured() {
        return session != null;
    }

    /**
     * Sends a plain-text email. Returns false if it could not be sent; the reason is written to the
     * Tomcat log. A failed email never stops the request, because the user can ask for a new code.
     */
    public boolean send(String to, String subject, String text) {
        return send(to, subject, text, null);
    }

    public boolean send(String to, String subject, String text, String deliveryKey) {
        if (session == null) {
            LOG.info("Email is not set up (mail.host is missing from config.properties), so this email was not sent."
                    + "\n  To: " + to + "\n  Subject: " + subject + "\n  " + text.replace("\n", "\n  "));
            return false;
        }
        try {
            MimeMessage message = new MimeMessage(session) {
                @Override protected void updateMessageID() throws MessagingException {
                    if (deliveryKey == null) super.updateMessageID();
                    else setHeader("Message-ID", "<" + deliveryKey + "@usc-marketplace>");
                }
            };
            message.setFrom(new InternetAddress(from));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setText(text, "UTF-8");
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            LOG.log(Level.WARNING, "Could not send email to " + to + ". Check the mail settings in config.properties.", e);
            return false;
        }
    }
}
