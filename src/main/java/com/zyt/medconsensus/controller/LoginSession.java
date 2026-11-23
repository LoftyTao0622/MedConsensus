package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.WebUtils;

final class LoginSession {
    static final String USER_ID = "CURRENT_USER_ID";
    static final String ROLE = "CURRENT_USER_ROLE";
    static final String SOCIAL_FLOW = LoginSession.class.getName() + ".socialFlow";

    private LoginSession() {}

    static AuthResponse establish(HttpServletRequest request, HttpSession session, AuthResponse user) {
        synchronized (WebUtils.getSessionMutex(session)) {
            request.changeSessionId();
            session.removeAttribute(SOCIAL_FLOW);
            session.setAttribute(USER_ID, user.id());
            session.setAttribute(ROLE, user.role());
            return user;
        }
    }
}
