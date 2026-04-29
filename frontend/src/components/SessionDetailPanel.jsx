function roleLabel(role) {
  return role === "assistant" ? "病情整理 Agent" : "患者";
}

export function SessionDetailPanel({ sessionDetail }) {
  if (!sessionDetail) {
    return (
      <div className="content-card session-detail-card">
        <h3>会话详情</h3>
        <p className="doctor-copy">选择左侧会话后，这里会展示 Redis 中保存的完整追问历史与最终结论。</p>
      </div>
    );
  }

  return (
    <div className="content-card session-detail-card">
      <div className="patient-evidence-header">
        <div>
          <h3>会话详情</h3>
          <p className="doctor-copy">
            {sessionDetail.title} · {sessionDetail.status} · 最近更新 {sessionDetail.updatedAt}
          </p>
        </div>
      </div>

      <div className="history-list">
        {(sessionDetail.history || []).map((item, index) => (
          <article className={`history-item ${item.role}`} key={`${item.role}-${index}`}>
            <span>{roleLabel(item.role)}</span>
            <p>{item.content}</p>
          </article>
        ))}
      </div>

      {sessionDetail.finalRecord ? (
        <div className="final-record-card">
          <h3>最终病历 / 诊断结论</h3>
          <div className="final-record-grid">
            <div>
              <span>主诉</span>
              <p>{sessionDetail.finalRecord.chiefComplaint || "未记录"}</p>
            </div>
            <div>
              <span>风险等级</span>
              <p>{sessionDetail.finalRecord.riskLevel || "待评估"}</p>
            </div>
            <div>
              <span>AI 初步结论</span>
              <p>{sessionDetail.finalRecord.aiConclusion || "未生成"}</p>
            </div>
            <div>
              <span>医生审核状态</span>
              <p>{sessionDetail.finalRecord.reviewStatus || "待审核"}</p>
            </div>
            <div>
              <span>医生意见</span>
              <p>{sessionDetail.finalRecord.doctorOpinion || "医生确认 AI 结论，无额外修正。"}</p>
            </div>
            <div>
              <span>最终结论</span>
              <p>{sessionDetail.finalRecord.finalConclusion || "未形成最终结论"}</p>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
