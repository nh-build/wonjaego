package com.wonjaego.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wonjaego.testsupport.AuthTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@Transactional
class ProductPhotoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Value("${wonjaego.storage.local.base-directory}")
    private String baseDirectory;

    private MockMultipartFile jpegFile(String filename) {
        return new MockMultipartFile("photo", filename, "image/jpeg", "fake-jpeg-bytes".getBytes());
    }

    private Long createProductWithPhoto(MockHttpSession session, String name, MockMultipartFile photo) throws Exception {
        mockMvc.perform(multipart("/products")
                        .file(photo)
                        .session(session).with(csrf())
                        .param("name", name)
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());
        return productRepository.findAll().stream()
                .filter(p -> p.getName().equals(name))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 사진_없이_상품을_등록할_수_있다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller1", "password123", "가게1");

        mockMvc.perform(multipart("/products")
                        .session(session).with(csrf())
                        .param("name", "상품A")
                        .param("price", "1000"))
                .andExpect(status().is3xxRedirection());

        Product product = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("상품A"))
                .findFirst()
                .orElseThrow();
        assertThat(product.getPhotoKey()).isNull();
    }

    @Test
    void 사진과_함께_등록하면_목록과_상세에서_썸네일이_보인다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller2", "password123", "가게2");
        Long productId = createProductWithPhoto(session, "상품B", jpegFile("photo.jpg"));

        mockMvc.perform(get("/products").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/products/" + productId + "/photo")));

        mockMvc.perform(get("/products/" + productId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/products/" + productId + "/photo")));

        // The owner can actually fetch the photo bytes, not just see a link to them.
        mockMvc.perform(get("/products/" + productId + "/photo").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getPhotoKey()).isNotNull();
        assertThat(Files.exists(Path.of(baseDirectory, product.getPhotoKey()))).isTrue();
    }

    @Test
    void 지원하지_않는_형식의_사진은_등록이_거부되고_다른_입력값이_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller3", "password123", "가게3");
        MockMultipartFile textFile = new MockMultipartFile("photo", "photo.txt", "text/plain", "not an image".getBytes());

        mockMvc.perform(multipart("/products")
                        .file(textFile)
                        .session(session).with(csrf())
                        .param("name", "상품C")
                        .param("price", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JPEG, PNG, WebP")))
                // The re-rendered registration form keeps what the user already typed —
                // rejection only clears the photo, not the rest of the submission.
                .andExpect(content().string(containsString("value=\"상품C\"")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("상품C"))).isFalse();
    }

    @Test
    void 오MB를_초과하는_사진은_등록이_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller4", "password123", "가게4");
        byte[] tooLarge = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile bigFile = new MockMultipartFile("photo", "big.jpg", "image/jpeg", tooLarge);

        mockMvc.perform(multipart("/products")
                        .file(bigFile)
                        .session(session).with(csrf())
                        .param("name", "상품D")
                        .param("price", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5MB")))
                .andExpect(content().string(containsString("value=\"상품D\"")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("상품D"))).isFalse();
    }

    @Test
    void Content_Type이_없는_파일은_500이_아니라_형식_에러로_거부된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller11", "password123", "가게11");
        MockMultipartFile noContentType = new MockMultipartFile("photo", "photo.jpg", null, "bytes".getBytes());

        mockMvc.perform(multipart("/products")
                        .file(noContentType)
                        .session(session).with(csrf())
                        .param("name", "상품H")
                        .param("price", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("JPEG, PNG, WebP")));

        assertThat(productRepository.findAll().stream().anyMatch(p -> p.getName().equals("상품H"))).isFalse();
    }

    @Test
    void 수정_화면에서_새_사진으로_교체하면_이전_파일이_삭제된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller5", "password123", "가게5");
        Long productId = createProductWithPhoto(session, "상품E", jpegFile("old.jpg"));
        String oldKey = productRepository.findById(productId).orElseThrow().getPhotoKey();
        assertThat(Files.exists(Path.of(baseDirectory, oldKey))).isTrue();

        mockMvc.perform(multipart("/products/" + productId + "/edit")
                        .file(jpegFile("new.jpg"))
                        .session(session).with(csrf())
                        .param("name", "상품E")
                        .param("price", "2000"))
                .andExpect(status().is3xxRedirection());

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getPhotoKey()).isNotEqualTo(oldKey);
        assertThat(Files.exists(Path.of(baseDirectory, oldKey))).isFalse();
        assertThat(Files.exists(Path.of(baseDirectory, updated.getPhotoKey()))).isTrue();
    }

    @Test
    void 사진_없이_수정하면_기존_사진이_유지된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller6", "password123", "가게6");
        Long productId = createProductWithPhoto(session, "상품F", jpegFile("keep.jpg"));
        String originalKey = productRepository.findById(productId).orElseThrow().getPhotoKey();

        mockMvc.perform(multipart("/products/" + productId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "상품F수정")
                        .param("price", "3000"))
                .andExpect(status().is3xxRedirection());

        Product updated = productRepository.findById(productId).orElseThrow();
        assertThat(updated.getPhotoKey()).isEqualTo(originalKey);
        assertThat(Files.exists(Path.of(baseDirectory, originalKey))).isTrue();
    }

    @Test
    void 다른_회원_소유_상품의_사진에_접근하면_404() throws Exception {
        MockHttpSession victimSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller7", "password123", "가게7");
        Long victimProductId = createProductWithPhoto(victimSession, "피해자상품", jpegFile("victim.jpg"));

        MockHttpSession attackerSession = AuthTestSupport.signUpAndLogin(mockMvc, "seller8", "password123", "가게8");

        mockMvc.perform(get("/products/" + victimProductId + "/photo").session(attackerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void 사진이_없는_상품의_사진_URL에_접근하면_404() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller9", "password123", "가게9");
        mockMvc.perform(multipart("/products")
                .session(session).with(csrf())
                .param("name", "무사진상품")
                .param("price", "1000"));
        Long productId = productRepository.findAll().stream()
                .filter(p -> p.getName().equals("무사진상품"))
                .findFirst()
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/products/" + productId + "/photo").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비로그인_상태로_사진_URL에_접근하면_로그인으로_리다이렉트된다() throws Exception {
        mockMvc.perform(get("/products/1/photo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 상품_삭제_시_사진_파일도_함께_삭제된다() throws Exception {
        MockHttpSession session = AuthTestSupport.signUpAndLogin(mockMvc, "seller10", "password123", "가게10");
        Long productId = createProductWithPhoto(session, "상품G", jpegFile("delete-me.jpg"));
        String photoKey = productRepository.findById(productId).orElseThrow().getPhotoKey();
        assertThat(Files.exists(Path.of(baseDirectory, photoKey))).isTrue();

        mockMvc.perform(post("/products/" + productId + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(Files.exists(Path.of(baseDirectory, photoKey))).isFalse();
    }
}
