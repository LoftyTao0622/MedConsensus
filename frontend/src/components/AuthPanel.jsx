import { useState } from "react";
import { HeartPulse, LogIn, UserPlus } from "lucide-react";
import { loginUser, registerUser } from "../api/auth";

const loginInitialState = {
  phone: "",
  password: ""
};

const registerInitialState = {
  username: "",
  password: "",
  age: "",
  weight: "",
  phone: "",
  gender: ""
};

export function AuthPanel({ onAuthenticated }) {
  const [mode, setMode] = useState("login");
  const [loginForm, setLoginForm] = useState(loginInitialState);
  const [registerForm, setRegisterForm] = useState(registerInitialState);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  function updateForm(setter, current, field, value) {
    setter({
      ...current,
      [field]: value
    });
  }

  async function handleLoginSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const user = await loginUser(loginForm);
      onAuthenticated(user);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRegisterSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError("");

    try {
      const user = await registerUser({
        ...registerForm,
        age: Number(registerForm.age),
        weight: Number(registerForm.weight)
      });
      onAuthenticated(user);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-shell">
      <div className="background-orb orb-one" />
      <div className="background-orb orb-two" />

      <section className="auth-card glass-card">
        <div className="auth-hero">
          <div className="brand-mark">
            <HeartPulse size={22} />
          </div>
          <p className="eyebrow">Medical Consensus Platform</p>
          <h1>共智专医</h1>
          <p>
            登录后即可进入 AI 诊断协作台，管理患者基础信息、查看多 Agent 诊断输出，
            并在医生审核环节提交专业意见。
          </p>
        </div>

        <div className="auth-switch">
          <button
            className={mode === "login" ? "auth-tab active" : "auth-tab"}
            type="button"
            onClick={() => {
              setMode("login");
              setError("");
            }}
          >
            <LogIn size={16} />
            登录
          </button>
          <button
            className={mode === "register" ? "auth-tab active" : "auth-tab"}
            type="button"
            onClick={() => {
              setMode("register");
              setError("");
            }}
          >
            <UserPlus size={16} />
            注册
          </button>
        </div>

        {mode === "login" ? (
          <form className="auth-form" onSubmit={handleLoginSubmit}>
            <label>
              手机号
              <input
                type="tel"
                autoComplete="tel"
                value={loginForm.phone}
                onChange={(event) =>
                  updateForm(setLoginForm, loginForm, "phone", event.target.value)
                }
                placeholder="请输入注册手机号"
                required
              />
            </label>
            <label>
              密码
              <input
                type="password"
                value={loginForm.password}
                onChange={(event) =>
                  updateForm(setLoginForm, loginForm, "password", event.target.value)
                }
                placeholder="请输入密码"
                required
              />
            </label>

            <button className="primary-button full" type="submit" disabled={submitting}>
              {submitting ? "登录中..." : "登录进入工作台"}
            </button>
          </form>
        ) : (
          <form className="auth-form auth-form-grid" onSubmit={handleRegisterSubmit}>
            <label>
              用户名
              <input
                value={registerForm.username}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "username", event.target.value)
                }
                placeholder="建议使用拼音或英文用户名"
                required
              />
            </label>
            <label>
              密码
              <input
                type="password"
                value={registerForm.password}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "password", event.target.value)
                }
                placeholder="请输入密码"
                required
              />
            </label>
            <label>
              年龄
              <input
                type="number"
                min="0"
                value={registerForm.age}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "age", event.target.value)
                }
                placeholder="年龄"
                required
              />
            </label>
            <label>
              体重
              <input
                type="number"
                min="0"
                step="0.1"
                value={registerForm.weight}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "weight", event.target.value)
                }
                placeholder="体重 (kg)"
                required
              />
            </label>
            <label>
              手机号
              <input
                type="tel"
                autoComplete="tel"
                value={registerForm.phone}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "phone", event.target.value)
                }
                placeholder="手机号"
                required
              />
            </label>
            <label>
              性别
              <select
                value={registerForm.gender}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "gender", event.target.value)
                }
              >
                <option value="">请选择</option>
                <option value="男">男</option>
                <option value="女">女</option>
                <option value="其他">其他</option>
              </select>
            </label>

            <button className="primary-button full auth-submit" type="submit" disabled={submitting}>
              {submitting ? "注册中..." : "注册并进入工作台"}
            </button>
          </form>
        )}

        {error ? <div className="feedback-box">{error}</div> : null}
      </section>
    </div>
  );
}
