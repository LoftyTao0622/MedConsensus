import { CheckCircle2, FileText, SendHorizonal } from "lucide-react";

export function DoctorPanel({
  activeSessionId,
  diagnosis,
  patient,
  opinion,
  onOpinionChange,
  onApprove,
  onSubmit,
  onGenerateReport,
  feedback,
  submitting,
  finalRecord,
  reportDisabled
}) {
  const disabled = submitting || !activeSessionId || !diagnosis;

  return (
    <aside className="doctor-panel glass-card section">
      <div className="section-title">
        <div>
          <p className="eyebrow">Doctor's Input</p>
          <h2>医生评判 / 意见</h2>
        </div>
        <span className="status-pill info">Human-in-the-loop</span>
      </div>

      <p className="doctor-copy">
        当 AI 置信度偏低、大模型投票共识分歧明显，或存在高风险病情时，医生可直接给出专业判断，
        作为最终诊断的重要依据。
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

      {finalRecord && <div className="feedback-box">当前复核结论：{finalRecord.finalConclusion}<br />{finalRecord.publishedToPatient ? "已发布。修改后可在医患协作中发布新版本。" : "已保存复核记录。"}</div>}
      <textarea
        className="doctor-textarea"
        value={opinion}
        onChange={(event) => onOpinionChange(event.target.value)}
        placeholder="请输入专业评判、补充检查建议或对 AI 结论的修正意见..."
        disabled={disabled}
      />

      <div className="doctor-actions">
        <button className="secondary-button full" type="button" onClick={onApprove} disabled={disabled}>
          <CheckCircle2 size={18} />
          确认 AI 诊断 (无异议)
        </button>

        <button className="primary-button full" type="button" onClick={onSubmit} disabled={disabled || !opinion.trim()}>
          <SendHorizonal size={18} />
          {finalRecord ? "保存修订意见" : "提交我的意见"}
        </button>

        <button
          className="report-button full"
          type="button"
          onClick={onGenerateReport}
          disabled={reportDisabled}
        >
          <FileText size={18} />
          {finalRecord ? "导出医生复核报告" : "导出待复核草稿"}
        </button>
      </div>

      {feedback ? <div className="feedback-box">{feedback}</div> : null}
    </aside>
  );
}
