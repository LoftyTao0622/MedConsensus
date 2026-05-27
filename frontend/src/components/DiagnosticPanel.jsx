import { useMemo, useState } from "react";
import { NotebookPen } from "lucide-react";

function percent(value) {
  return `${Math.round(value * 100)}%`;
}

export function DiagnosticPanel({ diagnosis, pipelineEvents, onSimulate, onAppendPatientEvidence, busy }) {
  const confidence = diagnosis?.confidence ?? 0;
  const [patientEvidence, setPatientEvidence] = useState("");
  const [submittedEvidence, setSubmittedEvidence] = useState("");

  const filteredAnalysis = useMemo(
    () =>
      (diagnosis?.structuredAnalysis || []).filter(
        (item) => !item.toLowerCase().includes("reviewer")
      ),
    [diagnosis]
  );

  const filteredTimeline = useMemo(
    () => pipelineEvents.filter((event) => event.stage !== "REVIEWERS"),
    [pipelineEvents]
  );

  function handleEvidenceSubmit() {
    const normalized = patientEvidence.trim();
    setSubmittedEvidence(normalized);
    onAppendPatientEvidence?.(normalized);
    setPatientEvidence("");
  }

  return (
    <section className="diagnostic-panel glass-card section">
      <div className="section-title">
        <div>
          <p className="eyebrow">AI Diagnostic Output</p>
          <h2>核心诊断展示区</h2>
        </div>
        <button className="secondary-button" type="button" onClick={onSimulate} disabled={busy}>
          {busy ? "推演中..." : "模拟多 Agent 流程"}
        </button>
      </div>

      <div className="hero-card">
        <div>
          <span className="status-pill warning">{diagnosis?.riskLevel || "待评估"}</span>
          <h3>{diagnosis?.conclusion || "等待系统生成诊断建议"}</h3>
          <p>
            诊断流程覆盖信息收集、大模型诊断、诊断结果决定，医生审核，
            AI初步意见将会提交给医生并最重由医生定夺。
          </p>
        </div>

        <div className="confidence-card">
          <div className="gauge">
            <div
              className="gauge-fill"
              style={{ "--gauge-value": `${confidence * 360}deg` }}
            />
            <div className="gauge-core">
              <strong>{percent(confidence)}</strong>
              <span>AI诊断置信度</span>
            </div>
          </div>
        </div>
      </div>

      <div className="content-grid">
        <div className="content-card">
          <h3>诊断依据说明</h3>
          <ul className="diagnostic-reason-list">
            {filteredAnalysis.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </div>
      </div>

      <div className="content-card timeline-card">
        <h3>流程动态</h3>
        <div className="timeline">
          {filteredTimeline.map((event) => (
            <div className="timeline-item" key={`${event.stage}-${event.timestamp}`}>
              <div className="timeline-dot" />
              <div>
                <strong>{event.stage}</strong>
                <p>{event.message}</p>
                <span>{event.progress}%</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="content-card patient-evidence-card">
        <div className="patient-evidence-header">
          <div>
            <h3>患者诊断依据补充</h3>
            <p className="doctor-copy">
              可补充症状细节、既往病史、检查结果、用药情况或其他有助于诊断的信息。
            </p>
          </div>
          <NotebookPen size={18} />
        </div>

        <textarea
          className="patient-evidence-textarea"
          value={patientEvidence}
          onChange={(event) => setPatientEvidence(event.target.value)}
          placeholder="例如：咳嗽持续几天、是否发热、是否做过血常规/胸片、是否有基础病、目前正在服用的药物等..."
        />

        <div className="patient-evidence-actions">
          <button
            className="primary-button"
            type="button"
            onClick={handleEvidenceSubmit}
            disabled={!patientEvidence.trim()}
          >
            填入患者诉求
          </button>
        </div>

        {submittedEvidence ? (
          <div className="patient-evidence-preview">
            <span>已填入患者诉求</span>
            <p>{submittedEvidence}</p>
          </div>
        ) : null}
      </div>
    </section>
  );
}
