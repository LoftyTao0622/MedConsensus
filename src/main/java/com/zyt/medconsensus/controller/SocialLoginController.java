package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.auth.social.SocialProviderClient;
import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.service.ExternalIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

@RestController
@RequestMapping("/api/auth/social")
public class SocialLoginController {
    private static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final URI LOGIN_ERROR = URI.create("/?social=error");
    private final SocialProviderClient providers;
    private final ExternalIdentityService identities;

    public SocialLoginController(SocialProviderClient providers, ExternalIdentityService identities) {
        this.providers = providers;
        this.identities = identities;
    }

    @GetMapping("/providers")
    public Map<String, List<String>> available() {
        return Map.of("providers", Stream.of("wechat", "qq").filter(providers::enabled).toList());
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable String provider, @RequestParam(required = false) String role,
                                      HttpSession session) {
        if (!"DOCTOR".equals(role) && !"PATIENT".equals(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择医生或患者身份");
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(LoginSession.USER_ID) != null) return redirect(URI.create("/"));
            var attempt = new Authorization(provider, role, nonce(), expiry());
            URI target = providers.authorizationUri(provider, attempt.state());
            session.setAttribute(LoginSession.SOCIAL_FLOW, attempt);
            return redirect(target);
        }
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String code, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return redirect(LOGIN_ERROR);
        Authorization attempt;
        // 验证和消费 state 在同一临界区完成，平台换码在锁外执行。
        Object mutex = WebUtils.getSessionMutex(session);
        synchronized (mutex) {
            if (!(session.getAttribute(LoginSession.SOCIAL_FLOW) instanceof Authorization active)
                    || !active.provider().equals(provider) || !active.state().equals(state)) return redirect(LOGIN_ERROR);
            session.removeAttribute(LoginSession.SOCIAL_FLOW);
            if (!active.expiresAt().isAfter(Instant.now()) || code == null || code.isBlank()) return redirect(LOGIN_ERROR);
            attempt = active;
            session.setAttribute(LoginSession.SOCIAL_FLOW, new Exchanging(attempt.state()));
        }

        try {
            var identity = providers.exchange(provider, code);
            var linked = identities.lookup(provider, identity.appId(), identity.openId(), attempt.role());
            synchronized (mutex) {
                // 用户在另一标签页重新登录或开始新授权后，旧回调不得再写入身份。
                if (!new Exchanging(attempt.state()).equals(session.getAttribute(LoginSession.SOCIAL_FLOW))
                        || !attempt.expiresAt().isAfter(Instant.now())) return redirect(LOGIN_ERROR);
                if (linked.isPresent()) {
                    LoginSession.establish(request, session, linked.get());
                    return redirect(URI.create("/"));
                }
                var pending = new Pending(provider, identity.appId(), identity.openId(), attempt.role(), nonce(), expiry());
                session.setAttribute(LoginSession.SOCIAL_FLOW, pending);
                return redirect(URI.create("/?social=bind#bindingId=" + pending.bindingId()));
            }
        } catch (ResponseStatusException | IllegalStateException exception) {
            // 回调页仅返回站内通用错误，避免把平台凭据及响应带回浏览器。
            return redirect(LOGIN_ERROR);
        }
    }

    @GetMapping("/pending")
    public PendingView pending(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            Pending pending = requirePending(session);
            return new PendingView(pending.provider(), pending.role(), pending.bindingId());
        }
    }

    @PostMapping("/bind")
    public AuthResponse bind(@Valid @RequestBody BindRequest input, HttpServletRequest request, HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            Pending pending = requireMatching(session, input.bindingId());
            AuthResponse user = identities.bind(pending.provider(), pending.appId(), pending.openId(),
                    pending.role(), input.phone(), input.password());
            return LoginSession.establish(request, session, user);
        }
    }

    @PostMapping("/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Valid @RequestBody CancelRequest input, HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            requireMatching(session, input.bindingId());
            session.removeAttribute(LoginSession.SOCIAL_FLOW);
        }
    }

    private Pending requirePending(HttpSession session) {
        if (session.getAttribute(LoginSession.SOCIAL_FLOW) instanceof Pending pending) {
            if (pending.expiresAt().isAfter(Instant.now()) && providers.enabled(pending.provider())) return pending;
            session.removeAttribute(LoginSession.SOCIAL_FLOW);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "第三方授权已失效，请重新登录");
    }

    private Pending requireMatching(HttpSession session, String bindingId) {
        Pending pending = requirePending(session);
        if (!pending.bindingId().equals(bindingId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "登录请求已变更，请重新发起授权");
        }
        return pending;
    }

    private static String nonce() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Instant expiry() { return Instant.now().plus(LIFETIME); }

    private static ResponseEntity<Void> redirect(URI target) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(target).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }

    record Authorization(String provider, String role, String state, Instant expiresAt) implements Serializable {}
    private record Exchanging(String state) implements Serializable {}
    record Pending(String provider, String appId, String openId, String role, String bindingId, Instant expiresAt) implements Serializable {}
    public record PendingView(String provider, String role, String bindingId) {}
    public record BindRequest(@NotBlank @Size(max = 20) String phone, @NotBlank String password, @NotBlank String bindingId) {}
    public record CancelRequest(@NotBlank String bindingId) {}
}
