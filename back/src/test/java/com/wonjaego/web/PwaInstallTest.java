package com.wonjaego.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
class PwaInstallTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void manifest_json은_로그인_없이_접근할_수_있고_standalone_설정을_포함한다() throws Exception {
        mockMvc.perform(get("/manifest.json"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("standalone")))
                .andExpect(content().string(containsString("원재고")));
    }

    @Test
    void 서비스워커_스크립트는_로그인_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/sw.js"))
                .andExpect(status().isOk());
    }

    @Test
    void 아이콘_파일은_로그인_없이_접근할_수_있다() throws Exception {
        mockMvc.perform(get("/icons/icon-192.png"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/icons/icon-512.png"))
                .andExpect(status().isOk());
    }

    @Test
    void 홈_화면에는_manifest_링크와_서비스워커_등록_스크립트가_포함된다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/manifest.json")))
                .andExpect(content().string(containsString("serviceWorker")));
    }

    @Test
    void 로그인이_필요한_화면은_비로그인_시_여전히_로그인으로_리다이렉트된다() throws Exception {
        mockMvc.perform(get("/products"))
                .andExpect(status().is3xxRedirection());
    }
}
