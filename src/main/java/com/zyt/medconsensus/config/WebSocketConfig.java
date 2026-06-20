package com.zyt.medconsensus.config;

import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String SESSION_USER_ID = "CURRENT_USER_ID";
    private static final String SESSION_USER_ROLE = "CURRENT_USER_ROLE";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/diagnosis")
                .addInterceptors(new AuthenticatedSessionHandshakeInterceptor())
                .setHandshakeHandler(new SessionUserHandshakeHandler())
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    private static final class AuthenticatedSessionHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session == null
                    || !(session.getAttribute(SESSION_USER_ID) instanceof Long userId)
                    || !(session.getAttribute(SESSION_USER_ROLE) instanceof String role)) {
                return false;
            }
            attributes.put(SESSION_USER_ID, userId);
            attributes.put(SESSION_USER_ROLE, role);
            return true;
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
        }
    }

    private static final class SessionUserHandshakeHandler extends DefaultHandshakeHandler {

        @Override
        protected Principal determineUser(
                ServerHttpRequest request,
                WebSocketHandler wsHandler,
            Map<String, Object> attributes
        ) {
            Long userId = (Long) attributes.get(SESSION_USER_ID);
            String role = (String) attributes.get(SESSION_USER_ROLE);
            return () -> WebSocketUserNames.forRole(role, userId);
        }
    }
}
