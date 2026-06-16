package io.healthresetplan.modules.sms;

public interface SmsSender {

    SendSmsResult sendVerificationCode(String phone, String code);
}
