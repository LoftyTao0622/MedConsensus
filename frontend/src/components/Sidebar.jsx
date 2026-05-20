import { useEffect, useState } from "react";
import { Check, FilePenLine, Eye, Pill, Plus, Trash2, X, Printer } from "lucide-react";

const blankPatientDraft = {
  id: null,
  name: "",
  age: "",
  weight: "",
  phone: "",
  gender: "",
  chiefComplaint: ""
};

export function Sidebar({
  patient,
  patients,
  onPatientChange,
  onSelectPatient,
  onSavePatient,
  onDeletePatient,
  patientSaving,
  sessions,
  activeSessionId,
  finalRecord,
  consultationInput,
  onConsultationInputChange,
  onSubmitConsultation,
  onSelectSession,
  onDeleteSession,
  consultationSubmitting,
  canSubmitConsultation,
  onGeneratePrescription
}) {
  const [editingPatient, setEditingPatient] = useState(false);
  const [patientDraft, setPatientDraft] = useState(blankPatientDraft);

  useEffect(() => {
    if (!editingPatient) {
      setPatientDraft(patient || blankPatientDraft);
    }
  }, [editingPatient, patient]);

  function updatePatientField(field, value) {
    setPatientDraft((current) => ({
      ...current,
      [field]: value
    }));
  }

  function startCreatePatient() {
    setPatientDraft(blankPatientDraft);
    setEditingPatient(true);
  }

  function startEditPatient(nextPatient) {
    setPatientDraft(nextPatient || blankPatientDraft);
    setEditingPatient(true);
  }

  async function handlePatientSubmit(event) {
    event.preventDefault();
    const savedPatient = await onSavePatient(patientDraft);
    onPatientChange(savedPatient);
    setEditingPatient(false);
  }

  function cancelPatientEdit() {
    setPatientDraft(patient || blankPatientDraft);
    setEditingPatient(false);
  }

  const treatmentLines = finalRecord?.treatmentAdvice
    ? finalRecord.treatmentAdvice.split("\n").filter(Boolean)
    : [];

  return (
    <aside className="sidebar">
      <section className="glass-card section">
        <div className="section-title">
          <div>
            <p className="eyebrow">Patient List</p>
            <h2>病患列表</h2>
          </div>
          <button className="icon-button" type="button" aria-label="新增患者" onClick={startCreatePatient}>
            <Plus size={18} />
          </button>
        </div>

        {editingPatient ? (
          <form className="patient-form" onSubmit={handlePatientSubmit}>
            <label className="patient-field full">
              <span>患者姓名</span>
              <input
                value={patientDraft.name || ""}
                onChange={(event) => updatePatientField("name", event.target.value)}
                placeholder="请输入患者姓名或代号"
                required
              />
            </label>

            <div className="info-grid patient-info-grid">
              <label className="patient-field">
                <span>年龄</span>
                <input
                  type="number"
                  min="0"
                  value={patientDraft.age || ""}
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
                  value={patientDraft.weight || ""}
                  onChange={(event) => updatePatientField("weight", event.target.value)}
                  placeholder="kg"
                />
              </label>
              <label className="patient-field">
                <span>性别</span>
                <select
                  value={patientDraft.gender || ""}
                  onChange={(event) => updatePatientField("gender", event.target.value)}
                  required
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
                value={patientDraft.phone || ""}
                onChange={(event) => updatePatientField("phone", event.target.value)}
                placeholder="请输入患者联系电话"
              />
            </label>

            <label className="patient-field full">
              <span>主诉</span>
              <textarea
                value={patientDraft.chiefComplaint || ""}
                onChange={(event) => updatePatientField("chiefComplaint", event.target.value)}
                placeholder="例如：8岁男孩，近1个月体重明显增加，食量大，不爱运动，吃完饭常躺着。"
              />
            </label>

            <div className="patient-form-actions">
              <button className="secondary-button" type="button" onClick={cancelPatientEdit}>
                <X size={16} />
                取消
              </button>
              <button className="primary-button" type="submit" disabled={patientSaving}>
                <Check size={16} />
                {patientSaving ? "保存中..." : "保存患者"}
              </button>
            </div>
          </form>
        ) : (
          <div className="patient-list">
            {patients.length ? (
              patients.map((item) => (
                <article
                  className={patient?.id === item.id ? "patient-list-item active" : "patient-list-item"}
                  key={item.id}
                  onClick={() => onSelectPatient(item)}
                >
                  <button className="patient-name-button" type="button">
                    {item.name}
                  </button>
                  <div className="session-actions">
                    <button
                      className="ghost-icon"
                      type="button"
                      aria-label="更改患者信息"
                      onClick={(event) => {
                        event.stopPropagation();
                        startEditPatient(item);
                      }}
                    >
                      <FilePenLine size={16} />
                    </button>
                    <button
                      className="ghost-icon danger"
                      type="button"
                      aria-label="删除患者"
                      onClick={(event) => {
                        event.stopPropagation();
                        onDeletePatient(item.id);
                      }}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </article>
              ))
            ) : (
              <p className="patient-list-empty">暂无患者，点击右上角新增。</p>
            )}
          </div>
        )}
      </section>

      <section className="glass-card section">
        <div className="section-title">
          <div>
            <p className="eyebrow">Patient Diagnosis</p>
            <h2>患者诊断管理</h2>
          </div>
        </div>

        <div className="consultation-composer">
          <label className="patient-field full">
            <span>病患姓名</span>
            <select
              value={patient?.id || ""}
              onChange={(event) => {
                const nextPatient = patients.find((item) => String(item.id) === event.target.value);
                if (nextPatient) {
                  onSelectPatient(nextPatient);
                }
              }}
              disabled={!patients.length}
            >
              <option value="">{patients.length ? "请选择病患姓名" : "请先在病患列表新增患者"}</option>
              {patients.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>

          <textarea
            className="consultation-textarea"
            value={consultationInput}
            onChange={(event) => onConsultationInputChange(event.target.value)}
            placeholder={
              activeSessionId
                ? "继续填写该患者本次诉求、检查结果、既往史或用药情况..."
                : "选择病患后，填写患者本次具体诉求、检查结果、既往史或用药情况..."
            }
          />
          <button
            className="secondary-button full"
            type="button"
            onClick={onSubmitConsultation}
            disabled={consultationSubmitting || !canSubmitConsultation}
          >
            {consultationSubmitting ? "整理中..." : activeSessionId ? "继续提交患者诉求" : "提交患者具体诉求"}
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

      <section className="glass-card section treatment-card">
        <div className="section-title treatment-title">
          <div>
            <p className="eyebrow">Treatment Agent</p>
            <h2>开药说明</h2>
          </div>
          <span className="treatment-icon">
            <Pill size={18} />
          </span>
        </div>

        {finalRecord?.treatmentAdvice ? (
          <>
            <div className="treatment-meta">
              <span>{finalRecord.treatmentSource === "DATABASE" ? "PostgreSQL 命中" : "MiMo 推理"}</span>
              {(finalRecord.treatmentKeywords || []).slice(0, 3).map((keyword) => (
                <span key={keyword}>{keyword}</span>
              ))}
            </div>
            <div className="treatment-content">
              {treatmentLines.map((line, index) => (
                <p key={`${line}-${index}`}>{line.replace(/^•\s*/, "")}</p>
              ))}
            </div>
          </>
        ) : (
          <p className="treatment-empty">医生给出最终诊断后，这里会显示 Treatment Agent 生成的开药建议。</p>
        )}
        <button
          className="primary-button full"
          type="button"
          onClick={onGeneratePrescription}
          style={{ marginTop: "14px" }}
        >
          <Printer size={16} />
          生成药单
        </button>
      </section>
    </aside>
  );
}
