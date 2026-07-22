package io.healthresetplan.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.modules.auth.dto.PhoneRegisterRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDtoTests {

    @Test
    void registrationAllowsPasswordToBeOmitted() {
        PhoneRegisterRequest request = new PhoneRegisterRequest();
        request.setPhone("13800138000");
        request.setRegistrationTicket("ticket");
        request.setAgreementVersion("2026-07-17");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertTrue(validator.validate(request).isEmpty());
        }
    }

    @Test
    void tokenResponseIncludesPasswordState() throws Exception {
        TokenResponse response = new TokenResponse("access", "refresh", 900, "100000000001", true);
        String json = new ObjectMapper().writeValueAsString(response);

        assertTrue(json.contains("\"hasPassword\":true"));
    }
}
