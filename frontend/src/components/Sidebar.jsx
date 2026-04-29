import { FilePenLine, Eye, Plus, Trash2 } from "lucide-react";

export function Sidebar({
  patient,
  sessions,
  activeSessionId,
  consultationInput,
  onConsultationInputChange,
  onStartNewConsultation,
  onSubmitConsultation,
  onSelectSession,
  onDeleteSession,
  consultationSubmitting
}) {
  return (
    <aside className="sidebar">
      <section className="glass-card section">
        <div className="section-title">
          <h2>用户与对话管理</h2>
          <span className="status-pill success">{patient?.loginStatus || "加载中"}</span>
        </div>

        <div className="profile-card">
          <div className="avatar">{patient?.name?.slice(0, 1) || "医"}</div>
          <div>
            <h3>{patient?.name || "患者信息载入中"}</h3>
            <p>ID {patient?.patientId || "--"}</p>
          </div>
        </div>

        <div className="info-grid">
          <div>
            <span>年龄</span>
            <strong>{patient?.age ?? "--"} 岁</strong>
          </div>
          <div>
            <span>体重</span>
            <strong>{patient?.weight ?? "--"} kg</strong>
          </div>
          <div>
            <span>性别</span>
            <strong>{patient?.gender || "--"}</strong>
          </div>
        </div>

        <div className="chief-complaint-card">
          <span>主诉</span>
          <p>{patient?.chiefComplaint || "--"}</p>
        </div>

        <div className="tag-list">
          {(patient?.highlights || []).map((item) => (
            <span key={item} className="tag">
              {item}
            </span>
          ))}
        </div>
      </section>

      <section className="glass-card section">
        <div className="section-title">
          <h2>多对话管理</h2>
        </div>

        <button className="primary-button full" type="button" onClick={onStartNewConsultation}>
          <Plus size={18} />
          发起新咨询
        </button>

        <div className="consultation-composer">
          <textarea
            className="consultation-textarea"
            value={consultationInput}
            onChange={(event) => onConsultationInputChange(event.target.value)}
            placeholder={
              activeSessionId
                ? "继续补充病情、检查结果、用药情况或既往史..."
                : "请输入本次咨询的症状、病程、既往病史或检查依据..."
            }
          />
          <button
            className="secondary-button full"
            type="button"
            onClick={onSubmitConsultation}
            disabled={consultationSubmitting || !consultationInput.trim()}
          >
            {consultationSubmitting ? "整理中..." : activeSessionId ? "继续整理病情" : "提交给病情整理 Agent"}
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
