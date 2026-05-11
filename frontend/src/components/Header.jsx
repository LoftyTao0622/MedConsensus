import { useEffect, useMemo, useState } from "react";
import {
  AlarmClock,
  Bell,
  CalendarClock,
  Check,
  CircleUserRound,
  Clock3,
  LogOut,
  Search,
  Settings,
  ShieldPlus,
  Trash2,
  UserRound
} from "lucide-react";

const REMINDER_STORAGE_KEY = "medconsensus-doctor-reminders";
const CARE_MODE_STORAGE_KEY = "medconsensus-care-mode";

function normalize(value) {
  return String(value || "").trim().toLowerCase();
}

function formatResults(patients, sessions, query) {
  const keyword = normalize(query);
  if (!keyword) {
    return [];
  }

  const patientResults = patients
    .filter((patient) => normalize(patient.name).includes(keyword))
    .slice(0, 5)
    .map((patient) => ({
      id: `patient-${patient.id}`,
      type: "patient",
      title: patient.name,
      meta: [patient.gender, patient.age ? `${patient.age}岁` : ""].filter(Boolean).join(" / "),
      payload: patient
    }));

  const sessionResults = sessions
    .filter((session) => normalize(session.title).includes(keyword) || normalize(session.status).includes(keyword))
    .slice(0, 5)
    .map((session) => ({
      id: `session-${session.id}`,
      type: "session",
      title: session.title,
      meta: [session.status, session.updatedAt].filter(Boolean).join(" / "),
      payload: session
    }));

  return [...patientResults, ...sessionResults].slice(0, 8);
}

function readStoredReminders() {
  try {
    const parsed = JSON.parse(localStorage.getItem(REMINDER_STORAGE_KEY) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function readStoredCareMode() {
  try {
    return localStorage.getItem(CARE_MODE_STORAGE_KEY) === "enabled";
  } catch {
    return false;
  }
}

function formatReminderTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function Header({
  currentUser,
  patients = [],
  sessions = [],
  onSelectPatient,
  onSelectSession,
  onLogout
}) {
  const [searchOpen, setSearchOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [reminderOpen, setReminderOpen] = useState(false);
  const [reminderText, setReminderText] = useState("");
  const [reminderMinutes, setReminderMinutes] = useState("5");
  const [reminders, setReminders] = useState(() => readStoredReminders());
  const [activeReminder, setActiveReminder] = useState(null);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [careMode, setCareMode] = useState(() => readStoredCareMode());
  const results = useMemo(() => formatResults(patients, sessions, query), [patients, sessions, query]);

  useEffect(() => {
    document.documentElement.classList.toggle("care-mode", careMode);
    localStorage.setItem(CARE_MODE_STORAGE_KEY, careMode ? "enabled" : "disabled");
  }, [careMode]);

  useEffect(() => {
    localStorage.setItem(REMINDER_STORAGE_KEY, JSON.stringify(reminders));
  }, [reminders]);

  useEffect(() => {
    const timers = reminders.map((reminder) => {
      const delay = Math.max(0, reminder.dueAt - Date.now());
      return window.setTimeout(() => {
        setActiveReminder(reminder);
        setReminders((current) => current.filter((item) => item.id !== reminder.id));
      }, delay);
    });

    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [reminders]);

  function handleResultClick(result) {
    if (result.type === "patient") {
      onSelectPatient?.(result.payload);
    } else {
      onSelectSession?.(result.payload.id);
    }
    setQuery("");
    setSearchOpen(false);
  }

  function handleReminderSubmit(event) {
    event.preventDefault();
    const text = reminderText.trim();
    if (!text) {
      return;
    }

    const minutes = Math.max(1, Number(reminderMinutes) || 5);
    const nextReminder = {
      id: `reminder-${Date.now()}`,
      text,
      dueAt: Date.now() + minutes * 60 * 1000
    };

    setReminders((current) => [...current, nextReminder].sort((left, right) => left.dueAt - right.dueAt));
    setReminderText("");
    setReminderMinutes("5");
    setReminderOpen(false);
  }

  return (
    <>
      <header className="topbar glass-card">
        <div className="brand">
          <div className="brand-mark">
            <ShieldPlus size={22} />
          </div>
          <div>
            <p className="eyebrow">Medical Consensus Workspace</p>
            <h1>共智专医</h1>
          </div>
        </div>

        <div className="topbar-actions">
          <div className={searchOpen ? "topbar-search active" : "topbar-search"}>
            <button
              className="icon-button"
              type="button"
              aria-label="搜索病患或会话"
              onClick={() => {
                setSearchOpen((current) => !current);
                setReminderOpen(false);
                setSettingsOpen(false);
              }}
            >
              <Search size={18} />
            </button>

            {searchOpen ? (
              <div className="search-popover">
                <div className="search-input-wrap">
                  <Search size={16} />
                  <input
                    autoFocus
                    value={query}
                    onChange={(event) => setQuery(event.target.value)}
                    placeholder="搜索病患姓名或诊断会话"
                  />
                </div>

                <div className="search-results">
                  {query ? (
                    results.length ? (
                      results.map((result) => (
                        <button
                          className="search-result-item"
                          type="button"
                          key={result.id}
                          onClick={() => handleResultClick(result)}
                        >
                          <span className="search-result-icon">
                            {result.type === "patient" ? <UserRound size={15} /> : <CalendarClock size={15} />}
                          </span>
                          <span>
                            <strong>{result.title}</strong>
                            <small>{result.type === "patient" ? result.meta || "病患资料" : result.meta || "诊断会话"}</small>
                          </span>
                        </button>
                      ))
                    ) : (
                      <p className="search-empty">没有匹配结果</p>
                    )
                  ) : (
                    <p className="search-empty">输入关键词后，可快速定位病患或历史诊断。</p>
                  )}
                </div>
              </div>
            ) : null}
          </div>
          <div className={reminderOpen ? "topbar-reminder active" : "topbar-reminder"}>
            {reminders.length ? <span className="reminder-count">{reminders.length}</span> : null}
            <button
              className="icon-button"
              type="button"
              aria-label="设置提醒"
              onClick={() => {
                setReminderOpen((current) => !current);
                setSearchOpen(false);
                setSettingsOpen(false);
              }}
            >
              <Bell size={18} />
            </button>

            {reminderOpen ? (
              <div className="reminder-popover">
                <form className="reminder-form" onSubmit={handleReminderSubmit}>
                  <label>
                    提醒内容
                    <input
                      value={reminderText}
                      onChange={(event) => setReminderText(event.target.value)}
                      placeholder="例如：查看口腔溃疡患者复诊结果"
                      required
                    />
                  </label>
                  <label>
                    几分钟后提醒
                    <input
                      type="number"
                      min="1"
                      value={reminderMinutes}
                      onChange={(event) => setReminderMinutes(event.target.value)}
                      required
                    />
                  </label>
                  <button className="primary-button full" type="submit">
                    <AlarmClock size={16} />
                    设置提醒
                  </button>
                </form>

                <div className="reminder-list">
                  {reminders.length ? (
                    reminders.map((reminder) => (
                      <article className="reminder-item" key={reminder.id}>
                        <Clock3 size={15} />
                        <div>
                          <strong>{reminder.text}</strong>
                          <span>{formatReminderTime(reminder.dueAt)} 提醒</span>
                        </div>
                        <button
                          className="ghost-icon danger"
                          type="button"
                          aria-label="删除提醒"
                          onClick={() =>
                            setReminders((current) => current.filter((item) => item.id !== reminder.id))
                          }
                        >
                          <Trash2 size={14} />
                        </button>
                      </article>
                    ))
                  ) : (
                    <p className="search-empty">暂无待提醒事项。</p>
                  )}
                </div>
              </div>
            ) : null}
          </div>
          <div className={settingsOpen ? "topbar-settings active" : "topbar-settings"}>
            <button
              className="icon-button"
              type="button"
              aria-label="显示设置"
              onClick={() => {
                setSettingsOpen((current) => !current);
                setSearchOpen(false);
                setReminderOpen(false);
              }}
            >
              <Settings size={18} />
            </button>

            {settingsOpen ? (
              <div className="settings-popover">
                <div className="settings-title">
                  <p className="eyebrow">Display</p>
                  <strong>显示设置</strong>
                </div>
                <button
                  className={careMode ? "settings-toggle active" : "settings-toggle"}
                  type="button"
                  role="switch"
                  aria-checked={careMode}
                  onClick={() => setCareMode((current) => !current)}
                >
                  <span>
                    <strong>关爱模式</strong>
                    <small>放大文字、按钮和输入区域</small>
                  </span>
                  <i aria-hidden="true" />
                </button>
              </div>
            ) : null}
          </div>
          <div className="user-chip">
            <CircleUserRound size={18} />
            <span>
              {[currentUser?.username, currentUser?.title].filter(Boolean).join(" / ") ||
                "Doctor Online"}
            </span>
          </div>
          <button
            className="icon-button"
            type="button"
            aria-label="退出登录"
            onClick={onLogout}
          >
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {activeReminder ? (
        <section className="reminder-alert" role="alert">
          <span className="reminder-alert-icon">
            <Bell size={20} />
          </span>
          <div>
            <p className="eyebrow">Doctor Reminder</p>
            <h2>{activeReminder.text}</h2>
            <p>这是你设定的定时提醒。</p>
          </div>
          <button className="primary-button" type="button" onClick={() => setActiveReminder(null)}>
            <Check size={16} />
            知道了
          </button>
        </section>
      ) : null}
    </>
  );
}
