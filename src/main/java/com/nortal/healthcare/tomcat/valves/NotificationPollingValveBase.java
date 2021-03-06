package com.nortal.healthcare.tomcat.valves;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.valves.ValveBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import repackaged.javax.mail.*;
import repackaged.javax.mail.internet.MimeMessage;

import java.util.Date;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class NotificationPollingValveBase extends ValveBase {

    /**
     * Logger
     */
    private static final Log log = LogFactory.getLog(NotificationPollingValveBase.class);

    public static final long DEFAULT_POLLING_DELAY = 10L;

    public NotificationPollingValveBase(boolean asyncSupported) {
        super(asyncSupported);
        this.pollingDelay = DEFAULT_POLLING_DELAY;
    }

    /**
     * Recipient e-mail address to send a notification of the stuck thread to.
     */
    private String emailRecipient;
    private String emailSender;
    private String emailSubject;
    private String smtpHost;
    private Long pollingDelay;

    public String getEmailRecipient() { return emailRecipient; }

    public void setEmailRecipient(String emailRecipient) {
        this.emailRecipient = emailRecipient;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    public String getEmailSender() {
        return emailSender == null ? emailRecipient : emailSender;
    }

    public void setEmailSender(String emailSender) {
        this.emailSender = emailSender;
    }

    public String getSmtpHost() { return smtpHost; }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    protected Long getPollingDelay() {
        return pollingDelay;
    }

    public void setPollingDelay(Long pollingDelay) {
        this.pollingDelay = pollingDelay;
    }

    private final ScheduledExecutorService emailSendingExecutor = Executors.newSingleThreadScheduledExecutor();
    private final PollingService pollingService = new PollingService();

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        emailSendingExecutor.scheduleWithFixedDelay(pollingService, getPollingDelay(), getPollingDelay(), TimeUnit.SECONDS);
    }

    @Override
    protected synchronized void stopInternal() throws LifecycleException {
        super.stopInternal();
        emailSendingExecutor.shutdownNow();
    }

    protected void addNotification(String message) {
        pollingService.addNotification(message);
    }

    class PollingService implements Runnable {

        private final Queue<String> notifications = new ConcurrentLinkedQueue<>();

        public void addNotification(String message) {
            notifications.add(message);
        }

        @Override
        public void run() {
            StringBuilder content = new StringBuilder();
            boolean isFirstNotification = true;
            for (String notification = notifications.poll();
                 notification != null; notification = notifications.poll()) {
                if (isFirstNotification) {
                    isFirstNotification = false;
                } else {
                    content.append("\n==========\n");
                }
                content.append(notification);
            }
            if (content.length() > 0) {
                if (getEmailRecipient() != null && !"".equals(getEmailRecipient())) {
                    sendEmail(content.toString());
                }
            }
        }

        private void sendEmail(String content) {
            log.info("sending email, recipient: " + getEmailRecipient()
                         + "; sender: "  + getEmailSender());
            Properties props = new Properties();
            Provider provider = new Provider(Provider.Type.TRANSPORT,
                                             "smtp",
                                             "repackaged.com.sun.mail.smtp.SMTPTransport",
                                             "Oracle",
                                             null);
            props.put("mail.smtp.host", getSmtpHost());
            Session session = Session.getInstance(props, null);

            try {
                session.setProvider(provider);
                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(getEmailSender());
                msg.setRecipients(Message.RecipientType.TO,
                                  getEmailRecipient());
                String emailSubject = getEmailSubject() == null
                    ? "Notification from " + getClass().getSimpleName()
                    : getEmailSubject();
                msg.setSubject(emailSubject);
                msg.setSentDate(new Date());
                msg.setText(content);
                Transport.send(msg);
            } catch (MessagingException mex) {
                log.error("send failed, exception: " + mex);
            }
        }

    }

}
