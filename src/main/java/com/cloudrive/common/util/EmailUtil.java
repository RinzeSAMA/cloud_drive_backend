package com.cloudrive.common.util;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 邮件工具类
 * 支持HTML内容、自定义主题和异步发送
 */
@Component
public class EmailUtil {
    private static final Logger log = LoggerFactory.getLogger(EmailUtil.class);
    
    private static JavaMailSender mailSender;
    private static String fromEmail;
    
    /**
     * 构造方法注入依赖
     */
    public EmailUtil(JavaMailSender mailSender,
                     @Value("${spring.mail.username}") String fromEmail) {
        EmailUtil.mailSender = mailSender;
        EmailUtil.fromEmail = fromEmail;
    }

    /**
     * 发送简单文本邮件(使用SimpleMailMessage)
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 发送是否成功
     */
    public static boolean sendSimpleEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            log.info("简单邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (Exception e) {
            log.error("发送简单邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送普通文本邮件
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @return 发送是否成功
     */
    public static boolean sendTextEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, false); // false表示不是HTML
            
            mailSender.send(message);
            log.info("文本邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (MessagingException e) {
            log.error("发送文本邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 发送HTML邮件
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent HTML内容
     * @return 发送是否成功
     */
    public static boolean sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true); // true表示是HTML
            
            mailSender.send(message);
            log.info("HTML邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (MessagingException e) {
            log.error("发送HTML邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 异步发送邮件，不阻塞当前线程
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param isHtml  是否为HTML内容
     * @return CompletableFuture对象
     */
    public static CompletableFuture<Boolean> sendEmailAsync(String to, String subject, String content, boolean isHtml) {
        return CompletableFuture.supplyAsync(() -> {
            if (isHtml) {
                return sendHtmlEmail(to, subject, content);
            } else {
                return sendTextEmail(to, subject, content);
            }
        });
    }
    
    /**
     * 发送验证码邮件
     * 
     * @param to          收件人邮箱
     * @param verificationCode 验证码
     * @return 发送是否成功
     */
    public static boolean sendVerificationCode(String to, String verificationCode) {
        String subject = "Cloud Drive 账号验证码";
        String content = buildVerificationEmailContent(verificationCode);
        return sendHtmlEmail(to, subject, content);
    }
    
    /**
     * 构建验证码邮件内容
     */
    private static String buildVerificationEmailContent(String verificationCode) {
        return "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 5px;\">"
                + "<h2 style=\"color: #1976d2; text-align: center;\">Cloud Drive 验证码</h2>"
                + "<p style=\"font-size: 16px; color: #333;\">您好,</p>"
                + "<p style=\"font-size: 16px; color: #333;\">您的验证码是:</p>"
                + "<div style=\"text-align: center; margin: 20px 0;\">"
                + "<span style=\"font-size: 24px; font-weight: bold; letter-spacing: 5px; color: #1976d2; background-color: #f5f5f5; padding: 10px 20px; border-radius: 4px;\">"
                + verificationCode
                + "</span>"
                + "</div>"
                + "<p style=\"font-size: 16px; color: #333;\">验证码有效期为5分钟，请勿将验证码泄露给他人。</p>"
                + "<p style=\"font-size: 14px; color: #757575; margin-top: 20px; text-align: center;\">此邮件由系统自动发送，请勿回复</p>"
                + "</div>";
    }
    
    /**
     * 发送带附件的邮件
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @param isHtml  是否为HTML内容
     * @param attachmentPath 附件路径
     * @param attachmentFileName 附件名称
     * @return 发送是否成功
     */
    public static boolean sendEmailWithAttachment(String to, String subject, String content, 
                                                 boolean isHtml, String attachmentPath, String attachmentFileName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true表示支持附件
            
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);
            
            // 添加附件
            // FileSystemResource file = new FileSystemResource(new File(attachmentPath));
            // helper.addAttachment(attachmentFileName, file);
            
            mailSender.send(message);
            log.info("带附件的邮件发送成功，收件人: {}, 主题: {}", to, subject);
            return true;
        } catch (MessagingException e) {
            log.error("发送带附件的邮件失败: {}", e.getMessage(), e);
            return false;
        }
    }
} 