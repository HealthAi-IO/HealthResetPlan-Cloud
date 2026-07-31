package io.healthresetplan.modules.files;

import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FileStorageServiceTests {

    private final FileStorageService service =
            new FileStorageService(new OssProperties(), mock(DataEncryptionService.class));

    @Test
    void rejectsNonImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "medicine.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThrows(
                BusinessException.class,
                () -> service.storeImage(file, "user-1", "client-1"));
    }

    @Test
    void rejectsUnsupportedImageType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "medicine.gif", "image/gif", new byte[]{'G', 'I', 'F'});

        assertThrows(
                BusinessException.class,
                () -> service.storeImage(file, "user-1", "client-1"));
    }
}
