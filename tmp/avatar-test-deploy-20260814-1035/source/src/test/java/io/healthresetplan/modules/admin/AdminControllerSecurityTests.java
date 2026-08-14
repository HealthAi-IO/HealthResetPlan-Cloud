package io.healthresetplan.modules.admin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerSecurityTests {

    @Test
    void releaseUrlMustUseAnApprovedHttpsHost() {
        assertThat(AdminController.isAllowedReleaseUrl(
                "https://jkcqplan.com/downloads/android/app.apk")).isTrue();
        assertThat(AdminController.isAllowedReleaseUrl(
                "https://download.jkcqplan.com/android/app.apk")).isTrue();
        assertThat(AdminController.isAllowedReleaseUrl(
                "http://jkcqplan.com/downloads/android/app.apk")).isFalse();
        assertThat(AdminController.isAllowedReleaseUrl(
                "https://jkcqplan.com.evil.example/app.apk")).isFalse();
    }
}
