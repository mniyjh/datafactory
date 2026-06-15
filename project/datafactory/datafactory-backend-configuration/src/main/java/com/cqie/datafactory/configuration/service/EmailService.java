package com.cqie.datafactory.configuration.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("1106935659@qq.com");
        msg.setTo(to);
        msg.setSubject("DataFactory 密码重置验证码");
        msg.setText("您的验证码是: " + code + "，有效期 5 分钟。如非本人操作请忽略。");
        mailSender.send(msg);
        log.info("Verification code sent to {}", to);
    }
}
