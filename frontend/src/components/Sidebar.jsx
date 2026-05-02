import { FilePenLine, Eye, Plus, Trash2 } from "lucide-react";

export function Sidebar({
  patient,
  onPatientChange,
  sessions,
  activeSessionId,
  consultationInput,
  onConsultationInputChange,
  onStartNewConsultation,
  onSubmitConsultation,
  onSelectSession,
  onDeleteSession,
  consultationSubmitting,
  canSubmitConsultation
}) {
  function updatePatientField(field, value) {
    onPatientChange({
      ...(patient || {}),
      [field]: value
    });
  }

  return (
    <aside className="sidebar">
      <section className="glass-card section">
        <div className="section-title">
          <div>
            <p className="eyebrow">Patient Input</p>
            <h2>患者基本信息</h2>
          </div>
          <span className="status-pill success">{patient?.loginStatus || "待填写"}</span>
        </div>

        <div className="patient-form">
          <label className="patient-field full">
            <span>患者姓名</span>
            <input
              value={patient?.name || ""}
              onChange={(event) => updatePatientField("name", event.target.value)}
              placeholder="请输入患者姓名或代号"
            />
          </label>

          <div className="info-grid patient-info-grid">
            <label className="patient-field">
              <span>年龄</span>
              <input
                type="number"
                min="0"
                value={patient?.age || ""}
                onChange={(event) => updatePatientField("age", event.target.value)}
                placeholder="岁"
              />
            </label>
            <label className="patient-field">
              <span>体重</span>
              <input
                type="number"
                min="0"
                step="0.1"
                value={patient?.weight || ""}
                onChange={(event) => updatePatientField("weight", event.target.value)}
                placeholder="kg"
              />
            </label>
            <label className="patient-field">
              <span>性别</span>
              <select
                value={patient?.gender || ""}
                onChange={(event) => updatePatientField("gender", event.target.value)}
              >
                <option value="">请选择</option>
                <option value="男">男</option>
                <option value="女">女</option>
                <option value="其他">其他</option>
              </select>
            </label>
          </div>

          <label className="patient-field full">
            <span>患者电话</span>
            <input
              type="tel"
              value={patient?.phone || ""}
              onChange={(event) => updatePatientField("phone", event.target.value)}
              placeholder="请输入患者联系电话"
            />
          </label>

          <label className="patient-field full">
            <span>主诉</span>
            <textarea
              value={patient?.chiefComplaint || ""}
              onChange={(event) => updatePatientField("chiefComplaint", event.target.value)}
              placeholder="例如：8岁男孩，近1个月体重明显增加，食量大，不爱运动，吃完饭常躺着。"
            />
          </label>
        </div>
      </section>

      <section className="glass-card section">
        <div className="section-title">
          <h2>患者诊断管理</h2>
        </div>

        <button className="primary-button full" type="button" onClick={onStartNewConsultation}>
          <Plus size={18} />
          新建患者诊断
        </button>

        <div className="consultation-composer">
          <textarea
            className="consultation-textarea"
            value={consultationInput}
            onChange={(event) => onConsultationInputChange(event.target.value)}
            placeholder={
              activeSessionId
                ? "继续补充病情、检查结果、用药情况或既往史..."
                : "填写患者基本信息后，可在这里补充检查结果、既往史或用药情况..."
            }
          />
          <button
            className="secondary-button full"
            type="button"
            onClick={onSubmitConsultation}
            disabled={consultationSubmitting || !canSubmitConsultation}
          >
            {consultationSubmitting ? "整理中..." : activeSessionId ? "继续整理该患者病情" : "提交该患者诊断"}
          </button>
        </div>

        <div className="session-list">
          {sessions.map((session) => (
            <article
              className={activeSessionId === session.id ? "session-item active" : "session-item"}
              key={session.id}
            >
              <div>
                <h3>{session.title}</h3>
                <p>{session.status}</p>
                <span>{session.updatedAt}</span>
              </div>

              <div className="session-actions">
                <button
                  className="ghost-icon"
                  type="button"
                  aria-label="查看"
                  onClick={() => onSelectSession(session.id)}
                >
                  <Eye size={16} />
                </button>
                <button className="ghost-icon" type="button" aria-label="编辑">
                  <FilePenLine size={16} />
                </button>
                <button
                  className="ghost-icon danger"
                  type="button"
                  aria-label="删除"
                  onClick={() => onDeleteSession(session.id)}
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>
    </aside>
  );
}
