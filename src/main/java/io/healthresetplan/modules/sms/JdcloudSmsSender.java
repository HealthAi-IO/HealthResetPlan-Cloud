package io.healthresetplan.modules.sms;

import com.jdcloud.sdk.auth.StaticCredentialsProvider;
import com.jdcloud.sdk.client.Environment;
import com.jdcloud.sdk.http.HttpRequestConfig;
import com.jdcloud.sdk.model.ServiceError;
import com.jdcloud.sdk.service.sms.client.SmsClient;
import com.jdcloud.sdk.service.sms.model.BatchSendRequest;
import com.jdcloud.sdk.service.sms.model.BatchSendResponse;
import com.jdcloud.sdk.service.sms.model.BatchSendResult;
import com.jdcloud.sdk.service.sms.model.BatchSendResp;
import io.healthresetplan.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JdcloudSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(JdcloudSmsSender.class);

    private final SmsProperties properties;

    public JdcloudSmsSender(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SendSmsResult sendVerificationCode(String phone, String code) {
        if (!properties.isEnabled()) {
            if (!properties.isDebugCodeEnabled()) {
                throw new BusinessException(50012, "短信服务暂未开通");
            }
            log.info("京东云短信未启用或配置不完整，跳过真实发送 phoneTail={}", phoneTail(phone));
            return new SendSmsResult(false, "jdcloud", null);
        }
        if (!"jdcloud".equalsIgnoreCase(properties.getProvider()) || !properties.isJdcloudReady()) {
            throw new BusinessException(50012, "短信服务配置不完整，请检查京东云短信配置");
        }

        SmsProperties.Jdcloud jdcloud = properties.getJdcloud();
        try {
            SmsClient client = SmsClient.builder()
                    .credentialsProvider(new StaticCredentialsProvider(
                            jdcloud.getAccessKeyId(),
                            jdcloud.getSecretAccessKey()
                    ))
                    .environment(new Environment.Builder().endpoint(jdcloud.getEndpoint()).build())
                    .httpRequestConfig(new HttpRequestConfig.Builder()
                            .connectionTimeout(jdcloud.getConnectTimeoutMillis())
                            .socketTimeout(jdcloud.getReadTimeoutMillis())
                            .build())
                    .build();

            BatchSendRequest request = new BatchSendRequest()
                    .regionId(properties.getRegionId())
                    .signId(jdcloud.getSignId())
                    .templateId(jdcloud.getTemplateId())
                    .phoneList(List.of(normalizePhone(phone)))
                    .params(List.of(code));

            BatchSendResponse response = client.batchSend(request);
            ServiceError error = response.getError();
            if (error != null) {
                log.warn("京东云短信发送失败 requestId={} code={} message={} endpoint={} regionId={} signId={} templateId={} phoneTail={}",
                        response.getRequestId(), error.getCode(), error.getMessage(),
                        jdcloud.getEndpoint(), properties.getRegionId(), jdcloud.getSignId(),
                        jdcloud.getTemplateId(), phoneTail(phone));
                throw new BusinessException(50011, "短信发送失败，请稍后再试");
            }

            BatchSendResult result = response.getResult();
            if (result != null && Boolean.FALSE.equals(result.getStatus())) {
                log.warn("京东云短信发送失败 requestId={} code={} message={} endpoint={} regionId={} signId={} templateId={} phoneTail={}",
                        response.getRequestId(), result.getCode(), result.getMessage(),
                        jdcloud.getEndpoint(), properties.getRegionId(), jdcloud.getSignId(),
                        jdcloud.getTemplateId(), phoneTail(phone));
                throw new BusinessException(50011, "短信发送失败，请稍后再试");
            }

            BatchSendResp data = result == null ? null : result.getData();
            String sequenceNumber = data == null ? null : data.getSequenceNumber();
            log.info("京东云短信发送成功 requestId={} sequenceNumber={} phoneTail={}",
                    response.getRequestId(), sequenceNumber, phoneTail(phone));
            return new SendSmsResult(true, "jdcloud", sequenceNumber);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("京东云短信调用异常 exceptionType={} message={} endpoint={} regionId={} signId={} templateId={} phoneTail={}",
                    ex.getClass().getSimpleName(), ex.getMessage(), jdcloud.getEndpoint(),
                    properties.getRegionId(), jdcloud.getSignId(), jdcloud.getTemplateId(), phoneTail(phone), ex);
            throw new BusinessException(50011, "短信发送失败，请稍后再试");
        }
    }

    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    private static String phoneTail(String phone) {
        String digits = normalizePhone(phone);
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : "";
    }
}
