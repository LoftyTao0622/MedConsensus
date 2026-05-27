import { AlertTriangle, CheckCircle2, FileSearch, RotateCcw, ShieldAlert, XCircle } from "lucide-react";

function asList(value) {
  return Array.isArray(value) ? value.filter(Boolean) : [];
}

function percent(value) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return "未返回";
  }
  return `${Math.round(numeric * 100)}%`;
}

function EvidenceList({ title, items, emptyText }) {
  return (
    <section className="evidence-section">
      <h3>{title}</h3>
      {items.length ? (
        <ul>
          {items.map((item, index) => (
            <li key={`${title}-${index}`}>{item}</li>
          ))}
        </ul>
      ) : (
        <p className="evidence-empty-line">{emptyText}</p>
      )}
    </section>
  );
}

export function EvidenceReviewPanel({
  evidence,
  evidenceFile,
  evidenceConfirmed,
  evidenceError,
  evidenceReviewNote,
  submitting,
  onEvidenceReviewNoteChange,
  onConfirmEvidence,
  onClearEvidence,
  onBackToWorkspace
}) {
  const extracted = evidence?.extracted || {};
  const redFlags = asList(extracted.redFlags);
  const imagingFindings = asList(extracted.imagingFindings);
  const labFindings = asList(extracted.labFindings);
  const keyMeasurements = asList(extracted.keyMeasurements);
  const fileName = evidence?.fileName || evidenceFile?.name || "未上传文件";

  return (
    <section className="evidence-review-page glass-card section">
      <div className="section-title evidence-page-title">
        <div>
          <p className="eyebrow">Medical Evidence Review</p>
          <h2>检查资料识别确认</h2>
        </div>
        <button className="secondary-button" type="button" onClick={onBackToWorkspace}>
          返回工作台
        </button>
      </div>

      <div className="evidence-compliance-banner">
        <ShieldAlert size={18} />
        <p>AI 识别结果仅用于辅助病情整理和诊断推理，不能替代放射科报告、检验报告或医生最终诊断。</p>
      </div>

      {evidenceError ? (
        <div className="evidence-state-panel error">
          <XCircle size={28} />
          <div>
            <h3>识别失败</h3>
            <p>{evidenceError}</p>
            <small>请检查 OPENAI_API_KEY、模型可用性、文件清晰度和文件格式后重新上传。</small>
          </div>
        </div>
      ) : !evidence ? (
        <div className="evidence-state-panel empty">
          <FileSearch size={30} />
          <div>
            <h3>暂无待确认资料</h3>
            <p>请在工作台的“上传CT/检查报告”入口上传 JPG、PNG、PDF 或 DOCX 文件。</p>
          </div>
        </div>
      ) : (
        <>
          <div className="evidence-status-grid">
            <div className="evidence-status-item">
              <span>文件名</span>
              <strong>{fileName}</strong>
            </div>
            <div className="evidence-status-item">
              <span>识别状态</span>
              <strong>{evidenceConfirmed ? "医生已确认" : "待医生确认"}</strong>
            </div>
            <div className="evidence-status-item">
              <span>资料类型</span>
              <strong>{extracted.modality || extracted.examType || "未识别"}</strong>
            </div>
            <div className="evidence-status-item">
              <span>模型置信度</span>
              <strong>{percent(extracted.confidence)}</strong>
            </div>
          </div>

          {redFlags.length ? (
            <div className="evidence-red-flags">
              <AlertTriangle size={18} />
              <div>
                <strong>需要优先复核</strong>
                <p>{redFlags.join("；")}</p>
              </div>
            </div>
          ) : null}

          <div className="evidence-detail-grid">
            <section className="evidence-section">
              <h3>资料摘要</h3>
              <p>{evidence.summary || extracted.summary || "模型未返回摘要。"}</p>
            </section>
            <section className="evidence-section">
              <h3>检查印象</h3>
              <p>{extracted.impression || extracted.diagnosis || "未识别到明确检查印象。"}</p>
            </section>
            <EvidenceList title="影像所见" items={imagingFindings} emptyText="未识别到影像所见。" />
            <EvidenceList title="检验结果" items={labFindings} emptyText="未识别到检验结果。" />
            <EvidenceList title="关键数值" items={keyMeasurements} emptyText="未识别到关键数值。" />
            <section className="evidence-section">
              <h3>风险分级</h3>
              <p>{extracted.riskLevel || "未返回风险分级。"}</p>
            </section>
          </div>

          <label className="evidence-review-note">
            <span>医生确认备注或修正</span>
            <textarea
              value={evidenceReviewNote}
              onChange={(event) => onEvidenceReviewNoteChange(event.target.value)}
              placeholder="可补充：该资料是否清晰、影像所见是否可信、需要修正的字段或人工复核意见。"
            />
          </label>

          <div className="evidence-review-actions">
            <button className="secondary-button" type="button" onClick={onClearEvidence} disabled={submitting}>
              <RotateCcw size={16} />
              清除资料
            </button>
            <button className="primary-button" type="button" onClick={onConfirmEvidence} disabled={submitting}>
              <CheckCircle2 size={16} />
              {submitting ? "提交中..." : "确认并用于诊断 Agent"}
            </button>
          </div>
        </>
      )}
    </section>
  );
}
