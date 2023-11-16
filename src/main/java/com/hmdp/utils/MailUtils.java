package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import com.sun.mail.util.MailSSLSocketFactory;
import org.springframework.mail.javamail.MimeMailMessage;

import javax.mail.*;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.security.GeneralSecurityException;
import java.util.Properties;

public class MailUtils {

    public static void main(String[] args) throws MessagingException, GeneralSecurityException {
        sendTestMail("949804680@qq.com", new MailUtils().achieveCode());
    }

    public static void sendTestMail(String mail, String code) throws MessagingException, GeneralSecurityException {
        Properties props = new Properties();
        props.setProperty("mail.debug", "true");
        // 设置邮件服务器主机名
        props.setProperty("mail.host", "smtp.qq.com");
        // 发送服务器需要身份验证
        props.setProperty("mail.smtp.auth", "true");
        // 发送邮件协议名称
        props.setProperty("mail.transport.protocol", "smtp");
        // 开启SSL加密，否则会失败
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        sf.setTrustAllHosts(true);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.socketFactory", sf);
        //设置信息
        props.put("mail.user", "949804680@qq.com");
        props.put("mail.password", "puvmgwexqpqfbche");
        //构建授权信息
        Authenticator authenticator = new Authenticator(){
            protected PasswordAuthentication getPasswordAuthentication() {
                String userName = props.getProperty("mail.user");
                String password = props.getProperty("mail.password");
                return new PasswordAuthentication(userName, password);
            }
        };
        //使用环境属性和授权信息
        Session mailSession = Session.getInstance(props, authenticator);
        MimeMessage mailMessage = new MimeMessage(mailSession);
        InternetAddress from = new InternetAddress(props.getProperty("mail.user"));
        mailMessage.setFrom(from);
        InternetAddress to = new InternetAddress(mail);
        mailMessage.setRecipient(MimeMessage.RecipientType.TO, to);
        mailMessage.setSubject("测试1");
        mailMessage.setContent("验证码为："+code+"(有效期为一分钟,请勿告知他人)", "text/html;charset=UTF-8");
        Transport.send(mailMessage);
    }

    public static String achieveCode() {
        return RandomUtil.randomString(6);
    }
}
