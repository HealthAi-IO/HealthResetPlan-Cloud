package io.healthresetplan.modules.files;

import io.healthresetplan.common.crypto.DataEncryptionService;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.config.OssProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void rejectsNonAvatarPathForSharedAvatarRead() {
        assertThrows(
                BusinessException.class,
                () -> service.readAvatar("files/user-1/report.enc"));
    }

    @Test
    void rejectsAvatarWithForgedImageContent() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThrows(BusinessException.class, () -> service.storeAvatar(file, "user-1"));
    }

    @Test
    void rejectsAvatarOwnedByAnotherUser() {
        String url = "/api/v1/files/avatar?objectKey="
                + "avatars%2Fuser-2%2F00000000-0000-0000-0000-000000000000.jpg.enc";

        assertThrows(BusinessException.class, () -> service.canonicalAvatarUrl(url, "user-1"));
    }

    @Test
    void canonicalizesLegacyOwnedAvatarUrl() {
        String url = "/api/v1/files/content?objectKey="
                + "avatars%2Fuser-1%2F00000000-0000-0000-0000-000000000000.png.enc";

        assertEquals(
                "/api/v1/files/avatar?objectKey=avatars%2Fuser-1%2F00000000-0000-0000-0000-000000000000.png.enc",
                service.canonicalAvatarUrl(url, "user-1"));
    }
}
