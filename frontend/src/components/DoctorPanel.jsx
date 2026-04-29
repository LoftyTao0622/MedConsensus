import { CheckCircle2, SendHorizonal } from "lucide-react";

export function DoctorPanel({
  activeSessionId,
  diagnosis,
  patient,
  opinion,
  onOpinionChange,
  onApprove,
  onSubmit,
  feedback,
  submitting
}) {
  const disabled = submitting || !activeSessionId || !diagnosis;

  return (
    <aside className="doctor-panel glass-card section">
      <div className="section-title">
        <div>
          <p className="eyebrow">Doctor's Input</p>
          <h2>人类医生评判 / 意见</h2>
        </div>
        <span className="status-pill info">Human-in-the-loop</span>
      </div>

      <p className="doctor-copy">
        当 AI 置信度偏低、Reviewer 共识分歧明显，或存在高风险病情时，医生可直接给出专业判断，
        作为最终诊断输出的重要依据。
      </p>

      {!activeSessionId || !diagnosis ? (
        <div className="feedback-box">
          请先在左侧选择一个已有会话，或完成一轮病情整理后，再提交人工审核意见。
        </div>
      ) : (
        <div className="feedback-box">
          当前审核会话：{activeSessionId}
          <br />
          主诉：{patient?.chiefComplaint || "未记录"}
        </div>
      )}

      {diagnosis ? (
        <div className="feedback-box">
          AI 初步意见：{diagnosis.conclusion}
          <br />
          AI 置信度：{Math.round((diagnosis.confidence || 0) * 100)}%
          <br />
          风险等级：{diagnosis.riskLevel || "待评估"}
        </div>
      ) : null}

      <textarea
        className="doctor-textarea"
        value={opinion}
        onChange={(event) => onOpinionChange(event.target.value)}
        placeholder="请输入医生专业评判、补充检查建议或对 AI 结论的修正意见..."
        disabled={disabled}
      />

      <div className="doctor-actions">
        <button className="secondary-button full" type="button" onClick={onApprove} disabled={disabled}>
          <CheckCircle2 size={18} />
          确认 AI 诊断 (无异议)
        </button>

        <button className="primary-button full" type="button" onClick={onSubmit} disabled={disabled}>
          <SendHorizonal size={18} />
          提交我的意见
        </button>
      </div>

      {feedback ? <div className="feedback-box">{feedback}</div> : null}
    </aside>
  );
}
