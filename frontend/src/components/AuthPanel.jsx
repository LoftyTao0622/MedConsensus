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
  phone: "",
  department: "",
  title: ""
};

export function AuthPanel({ onAuthenticated }) {
  const [role, setRole] = useState("DOCTOR");
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
      const user = await loginUser({ ...loginForm, role });
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
      const user = await registerUser({ ...registerForm, role });
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
            {role === "DOCTOR"
              ? "进入 AI 诊断协作台，管理患者资料、处理患者问诊，并在审核环节提交专业意见。"
              : "提交症状和检查资料，回答医生追问，并查看医生审核后发布的诊断报告。"}
          </p>
        </div>

        <div className="role-switch" aria-label="选择登录角色">
          <button
            className={role === "DOCTOR" ? "role-option active" : "role-option"}
            type="button"
            onClick={() => {
              setRole("DOCTOR");
              setError("");
            }}
          >
            我是医生
          </button>
          <button
            className={role === "PATIENT" ? "role-option active" : "role-option"}
            type="button"
            onClick={() => {
              setRole("PATIENT");
              setError("");
            }}
          >
            我是患者
          </button>
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
              {submitting ? "登录中..." : role === "DOCTOR" ? "登录进入医生工作台" : "登录进入患者服务台"}
            </button>
          </form>
        ) : (
          <form className="auth-form auth-form-grid" onSubmit={handleRegisterSubmit}>
            <label>
              {role === "DOCTOR" ? "医生姓名" : "患者姓名"}
              <input
                value={registerForm.username}
                onChange={(event) =>
                  updateForm(setRegisterForm, registerForm, "username", event.target.value)
                }
                placeholder={role === "DOCTOR" ? "请输入医生姓名或登录名" : "请输入患者姓名"}
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
            {role === "DOCTOR" ? (
              <>
                <label>
                  科室
                  <input
                    value={registerForm.department}
                    onChange={(event) =>
                      updateForm(setRegisterForm, registerForm, "department", event.target.value)
                    }
                    placeholder="例如：儿科、呼吸内科"
                  />
                </label>
                <label>
                  职称
                  <input
                    value={registerForm.title}
                    onChange={(event) =>
                      updateForm(setRegisterForm, registerForm, "title", event.target.value)
                    }
                    placeholder="例如：主治医师"
                  />
                </label>
              </>
            ) : (
              <>
                <label>
                  性别
                  <select
                    value={registerForm.gender || ""}
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
                <label>
                  年龄
                  <input
                    type="number"
                    min="0"
                    value={registerForm.age || ""}
                    onChange={(event) =>
                      updateForm(setRegisterForm, registerForm, "age", event.target.value)
                    }
                    placeholder="请输入年龄"
                  />
                </label>
                <label>
                  体重（kg）
                  <input
                    type="number"
                    min="0"
                    step="0.1"
                    value={registerForm.weight || ""}
                    onChange={(event) =>
                      updateForm(setRegisterForm, registerForm, "weight", event.target.value)
                    }
                    placeholder="请输入体重"
                  />
                </label>
              </>
            )}

            <button className="primary-button full auth-submit" type="submit" disabled={submitting}>
              {submitting ? "注册中..." : role === "DOCTOR" ? "注册并进入医生工作台" : "注册并进入患者服务台"}
            </button>
          </form>
        )}

        {error ? <div className="feedback-box">{error}</div> : null}
      </section>
    </div>
  );
}
