import { useEffect, useState } from "react";
import { bindSocialAccount, cancelSocialBinding, fetchSocialPending, fetchSocialProviders } from "../api/auth";
import { callbackBindingId, enabledSocialProviders, readPendingBinding, samePendingBinding, socialIdentity } from "../socialLogin";

export function SocialLoginButtons({ role, disabled }) {
  const [providers, setProviders] = useState([]);
  useEffect(() => {
    let active = true;
    fetchSocialProviders()
      .then((data) => { if (active) setProviders(enabledSocialProviders(data)); })
      .catch(() => { if (active) setProviders([]); });
    return () => { active = false; };
  }, []);

  if (!providers.length) return null;
  return (
    <div className="social-login-actions" role="group" aria-label="第三方登录">
      {providers.map((provider) => {
        const identity = socialIdentity(provider, role);
        if (!identity) return null;
        return (
          <button key={provider} type="button" className="secondary-button" disabled={disabled}
            onClick={() => window.location.assign(identity.startPath)}>
            使用{identity.providerLabel}登录
          </button>
        );
      })}
    </div>
  );
}

export function SocialCallbackPanel({ action, onBack, onBound }) {
  const [expectedId] = useState(() => callbackBindingId(window.location.hash));
  const [pending, setPending] = useState(null);
  const [loading, setLoading] = useState(action === "bind");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(action === "error" ? "第三方登录未完成，请返回登录页重试。" : "");

  useEffect(() => {
    if (action !== "bind") return;
    if (!expectedId) {
      setError("绑定信息已失效，请返回登录页重新授权。");
      setLoading(false);
      return;
    }
    let active = true;
    fetchSocialPending().then((data) => {
      if (!active) return;
      const binding = readPendingBinding(data);
      if (binding?.bindingId === expectedId) setPending(binding);
      else setError("绑定信息已失效，请返回登录页重新授权。");
    }).catch(() => {
      if (active) setError("暂时无法读取待绑定信息，请返回登录页重新授权。");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [action, expectedId]);

  async function bind(event) {
    event.preventDefault();
    if (!pending || busy) return;
    setBusy(true);
    setError("");
    try {
      const user = await bindSocialAccount(phone.trim(), password, pending.bindingId);
      if (user?.role !== pending.role) {
        setPending(null);
        setPassword("");
        setError("登录身份未获确认，请返回登录页重新授权。");
        return;
      }
      onBound(user);
    } catch (failure) {
      setPassword("");
      if (failure.status === 401) {
        const current = await fetchSocialPending().catch(() => null);
        if (samePendingBinding(pending, current)) {
          setError("手机号或密码不正确，请核对当前身份的账号后重试。");
          return;
        }
      }
      if (failure.status === 401 || failure.status === 409) {
        setPending(null);
        setError("当前绑定未获确认，请返回登录页重新授权。");
      } else {
        setError("绑定未完成，请检查当前身份的手机号和密码，或返回登录页重新授权。");
      }
    } finally {
      setBusy(false);
    }
  }

  async function cancel() {
    if (busy) return;
    if (!pending) {
      onBack();
      return;
    }
    setBusy(true);
    setError("");
    try {
      await cancelSocialBinding(pending.bindingId);
      onBack();
    } catch (failure) {
      setPending(null);
      setPassword("");
      setError(failure.status === 401 || failure.status === 409
        ? "此绑定已失效，请返回登录页重新授权。"
        : "暂时无法确认取消结果。请返回登录页，需要绑定时重新授权。");
    } finally {
      setBusy(false);
    }
  }

  const identity = socialIdentity(pending?.provider, pending?.role);
  return (
    <main className="auth-shell">
      <section className="auth-card glass-card">
        <div className="auth-hero">
          <h1>{identity ? "绑定" + identity.providerLabel + "登录" : "第三方登录"}</h1>
          {identity && <p>请输入已有{identity.roleLabel}账号的手机号和密码，完成绑定后即可登录。</p>}
        </div>
        {loading && <p role="status">正在确认待绑定的账号身份…</p>}
        {identity && (
          <form className="auth-form" onSubmit={bind}>
            <label>手机号
              <input type="tel" autoComplete="username" required value={phone} disabled={busy}
                onChange={(event) => setPhone(event.target.value)} />
            </label>
            <label>密码
              <input type="password" autoComplete="current-password" required value={password} disabled={busy}
                onChange={(event) => setPassword(event.target.value)} />
            </label>
            <button className="primary-button full" type="submit" disabled={busy || !phone.trim() || !password}>
              绑定并登录
            </button>
          </form>
        )}
        {error && <p className="feedback-box" role="alert">{error}</p>}
        <div className="social-login-actions">
          <button type="button" className="secondary-button" disabled={busy || loading} onClick={cancel}>返回登录页</button>
        </div>
      </section>
    </main>
  );
}