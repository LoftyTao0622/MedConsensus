import { AlarmClock, Bell, Clock3, Trash2 } from "lucide-react";
import { useState } from "react";

function formatReminderTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function SettingsTab({ careMode, onCareModeChange, reminders, onAddReminder, onRemoveReminder }) {
  const [reminderText, setReminderText] = useState("");
  const [reminderMinutes, setReminderMinutes] = useState("5");

  function handleReminderSubmit(event) {
    event.preventDefault();
    const text = reminderText.trim();
    if (!text) {
      return;
    }

    const minutes = Math.max(1, Number(reminderMinutes) || 5);
    onAddReminder({
      id: `reminder-${Date.now()}`,
      text,
      dueAt: Date.now() + minutes * 60 * 1000
    });

    setReminderText("");
    setReminderMinutes("5");
  }

  return (
    <div className="settings-tab glass-card section full-height">
      <div className="section-title">
        <div>
          <p className="eyebrow">System Settings</p>
          <h2>系统设置与日程提醒</h2>
        </div>
      </div>

      <div className="settings-grid">
        <div className="settings-section">
          <h3>日程提醒 (Reminders)</h3>
          <form className="reminder-form settings-card" onSubmit={handleReminderSubmit}>
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
            <button className="primary-button" type="submit" style={{ marginTop: "10px" }}>
              <AlarmClock size={16} />
              设置提醒
            </button>
          </form>

          <div className="reminder-list settings-card" style={{ marginTop: "16px" }}>
            <h4>待办提醒列表</h4>
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
                    onClick={() => onRemoveReminder(reminder.id)}
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

        <div className="settings-section">
          <h3>界面显示 (Display)</h3>
          <div className="settings-card">
            <button
              className={careMode ? "settings-toggle active" : "settings-toggle"}
              type="button"
              role="switch"
              aria-checked={careMode}
              onClick={() => onCareModeChange(!careMode)}
            >
              <span>
                <strong>关爱模式 (大字体)</strong>
                <small>针对老年医生优化，放大文字、按钮和输入区域</small>
              </span>
              <i aria-hidden="true" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
