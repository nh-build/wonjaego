package com.wonjaego.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.HttpSession;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static MockHttpSession signUpAndLogin(MockMvc mockMvc, String username, String password, String businessName)
            throws Exception {
        mockMvc.perform(post("/signup")
                .with(csrf())
                .param("username", username)
                .param("password", password)
                .param("businessName", businessName));

        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        return (MockHttpSession) session;
    }
}
