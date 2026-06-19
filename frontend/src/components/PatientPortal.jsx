import { useEffect, useMemo, useState } from "react";
import {
  BadgeCheck,
  ClipboardPlus,
  FileCheck2,
  FileUp,
  HeartPulse,
  Link2,
  LogOut,
  MessageSquareMore,
  RefreshCw,
  SendHorizonal,
  Stethoscope
} from "lucide-react";
import {
  askReportExplanation,
  answerPatientQuestion,
  fetchReportExplanation,
  fetchPatientDashboard,
  requestDoctorBinding,
  startPatientConsultation,
  uploadPatientEvidence
} from "../api/patient";

const statusCopy = {
  NEEDS_PATIENT_REPLY: "待补充信息",
  WAITING_DOCTOR: "等待医生处理",
  WAITING_REPORT: "等待医生审核",
  WAITING_DOCTOR_EVIDENCE_REVIEW: "检查资料待医生确认",
  REPORT_READY: "报告待发布",
  REPORT_PUBLISHED: "医生已发布报告"
};

function formatTime(value) {
  if (!value) return "";
  return new Date(value).toLocaleString("zh-CN");
}

export function PatientPortal({ currentUser, onLogout }) {
  const [dashboard, setDashboard] = useState(null);
  const [inviteCode, setInviteCode] = useState("");
  const [relationId, setRelationId] = useState("");
  const [consultationText, setConsultationText] = useState("");
  const [replyText, setReplyText] = useState({});
  const [explanationChats, setExplanationChats] = useState({});
  const [explanationInputs, setExplanationInputs] = useState({});
  const [feedback, setFeedback] = useState("");
  const [busyAction, setBusyAction] = useState("");

  const activeRelations = useMemo(
    () => (dashboard?.relations || []).filter((item) => item.status === "ACTIVE"),
    [dashboard]
  );

  async function refreshDashboard() {
    const data = await fetchPatientDashboard();
    setDashboard(data);
    setRelationId((current) => current || String(data.relations.find((item) => item.status === "ACTIVE")?.id || ""));
  }

  useEffect(() => {
    refreshDashboard().catch((error) => setFeedback(`读取患者服务台失败：${error.message}`));
  }, []);

  async function runAction(key, action, successMessage) {
    setBusyAction(key);
    setFeedback("");
    try {
      await action();
      await refreshDashboard();
      setFeedback(successMessage);
      return true;
    } catch (error) {
      setFeedback(error.message);
      return false;
    } finally {
      setBusyAction("");
    }
  }

  async function handleBinding(event) {
    event.preventDefault();
    const succeeded = await runAction(
      "binding",
      () => requestDoctorBinding(inviteCode.trim()),
      "绑定申请已提交，请等待医生确认。"
    );
    if (succeeded) setInviteCode("");
  }

  async function handleConsultation(event) {
    event.preventDefault();
    const succeeded = await runAction(
      "consultation",
      () => startPatientConsultation(Number(relationId), consultationText.trim()),
      "问诊已提交，医生工作台已收到本次信息。"
    );
    if (succeeded) setConsultationText("");
  }

  async function handleReply(consultationId) {
    const message = replyText[consultationId]?.trim();
    if (!message) return;
    const succeeded = await runAction(
      `reply-${consultationId}`,
      () => answerPatientQuestion(consultationId, message),
      "补充信息已提交。"
    );
    if (succeeded) {
      setReplyText((current) => ({ ...current, [consultationId]: "" }));
    }
  }

  function handleEvidence(consultationId, file) {
    if (!file) return;
    runAction(
      `evidence-${consultationId}`,
      () => uploadPatientEvidence(consultationId, file),
      "检查资料已上传，正在等待医生确认后进入诊断流程。"
    );
  }

  async function handleLoadExplanation(reportId) {
    if (explanationChats[reportId]) return;
    setBusyAction(`explanation-load-${reportId}`);
    try {
      const chat = await fetchReportExplanation(reportId);
      setExplanationChats((current) => ({ ...current, [reportId]: chat.messages || [] }));
    } catch (error) {
      setFeedback(error.message);
    } finally {
      setBusyAction("");
    }
  }

  async function handleExplanationQuestion(reportId) {
    const message = explanationInputs[reportId]?.trim();
    if (!message) return;
    setBusyAction(`explanation-${reportId}`);
    setFeedback("");
    try {
      const chat = await askReportExplanation(reportId, message);
      setExplanationChats((current) => ({ ...current, [reportId]: chat.messages || [] }));
      setExplanationInputs((current) => ({ ...current, [reportId]: "" }));
    } catch (error) {
      setFeedback(error.message);
    } finally {
      setBusyAction("");
    }
  }

  if (!dashboard) {
    return (
      <div className="patient-portal-shell">
        <section className="patient-loading-panel">
          <RefreshCw className="spin" size={22} />
          <p>正在读取患者服务台...</p>
        </section>
      </div>
    );
  }

  const pendingReplyCount = dashboard.consultations.filter(
    (item) => item.status === "NEEDS_PATIENT_REPLY"
  ).length;

  return (
    <div className="patient-portal-shell">
      <aside className="patient-rail">
        <div className="patient-brand">
          <span className="patient-brand-mark"><HeartPulse size={24} /></span>
          <div>
            <p>Medical Consensus</p>
            <h1>共智专医</h1>
          </div>
        </div>

        <nav className="patient-rail-nav" aria-label="患者服务台导航">
          <a href="#patient-home"><Stethoscope size={19} />服务台概览</a>
          <a href="#patient-consult"><ClipboardPlus size={19} />发起问诊</a>
          <a href="#patient-progress"><MessageSquareMore size={19} />问诊与追问</a>
          <a href="#patient-reports"><FileCheck2 size={19} />诊断报告</a>
        </nav>

        <div className="patient-account">
          <span>{currentUser.username?.charAt(0) || "患"}</span>
          <div>
            <strong>{currentUser.username}</strong>
            <small>{currentUser.phone}</small>
          </div>
          <button type="button" onClick={onLogout} aria-label="退出登录" title="退出登录">
            <LogOut size={18} />
          </button>
        </div>
      </aside>

      <main className="patient-main" id="patient-home">
        <header className="patient-page-header">
          <div>
            <p className="eyebrow">Patient Service Desk</p>
            <h2>患者服务台</h2>
            <p>提交真实病情并配合补充资料，最终诊断以医生审核后发布的报告为准。</p>
          </div>
          <button
            className="secondary-button"
            type="button"
            onClick={() => runAction("refresh", refreshDashboard, "信息已更新。")}
            disabled={busyAction === "refresh"}
          >
            <RefreshCw size={17} className={busyAction === "refresh" ? "spin" : ""} />
            刷新状态
          </button>
        </header>

        {feedback ? <div className="patient-feedback" role="status">{feedback}</div> : null}

        <section className="patient-status-strip" aria-label="当前状态">
          <div>
            <span>已绑定医生</span>
            <strong>{activeRelations.length}</strong>
          </div>
          <div>
            <span>待补充信息</span>
            <strong>{pendingReplyCount}</strong>
          </div>
          <div>
            <span>已上传资料</span>
            <strong>{dashboard.consultations.filter((item) => item.evidenceFileName).length}</strong>
          </div>
          <div>
            <span>已发布报告</span>
            <strong>{dashboard.reports.length}</strong>
          </div>
        </section>

        <div className="patient-dashboard-grid">
          <section className="patient-section patient-bind-section">
            <div className="patient-section-heading">
              <span className="section-icon"><Link2 size={20} /></span>
              <div>
                <h3>绑定医生</h3>
                <p>输入医生提供的邀请码，申请建立医患关系。</p>
              </div>
            </div>

            <form className="patient-inline-form" onSubmit={handleBinding}>
              <label>
                医生邀请码
                <input
                  value={inviteCode}
                  onChange={(event) => setInviteCode(event.target.value.toUpperCase())}
                  placeholder="例如：DR-12AB34CD"
                  required
                />
              </label>
              <button className="primary-button" type="submit" disabled={busyAction === "binding"}>
                {busyAction === "binding" ? "正在提交申请" : "申请绑定医生"}
              </button>
            </form>

            <div className="patient-relation-list">
              {dashboard.relations.length ? dashboard.relations.map((relation) => (
                <div className="patient-relation-row" key={relation.id}>
                  <span className={relation.status === "ACTIVE" ? "relation-dot active" : "relation-dot"} />
                  <div>
                    <strong>{relation.doctorName}</strong>
                    <p>{[relation.department, relation.title].filter(Boolean).join(" · ") || "医生信息待完善"}</p>
                  </div>
                  <span className={`patient-state state-${relation.status.toLowerCase()}`}>
                    {relation.status === "ACTIVE" ? "已绑定" : "等待医生确认"}
                  </span>
                </div>
              )) : <p className="patient-empty-copy">尚未绑定医生，绑定后即可发起问诊。</p>}
            </div>
          </section>

          <section className="patient-section patient-consult-section" id="patient-consult">
            <div className="patient-section-heading">
              <span className="section-icon"><ClipboardPlus size={20} /></span>
              <div>
                <h3>发起问诊</h3>
                <p>请描述症状、持续时间、既往病史和正在使用的药物。</p>
              </div>
            </div>

            <form className="patient-consult-form" onSubmit={handleConsultation}>
              <label>
                接诊医生
                <select
                  value={relationId}
                  onChange={(event) => setRelationId(event.target.value)}
                  disabled={!activeRelations.length}
                  required
                >
                  <option value="">{activeRelations.length ? "请选择医生" : "请先完成医生绑定"}</option>
                  {activeRelations.map((relation) => (
                    <option key={relation.id} value={relation.id}>
                      {relation.doctorName} {relation.department ? `· ${relation.department}` : ""}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                本次问诊内容
                <textarea
                  value={consultationText}
                  onChange={(event) => setConsultationText(event.target.value)}
                  placeholder="例如：咳嗽持续5天，昨晚体温38.2℃，目前服用过退烧药，无已知药物过敏。"
                  required
                />
              </label>
              <button
                className="primary-button"
                type="submit"
                disabled={!activeRelations.length || busyAction === "consultation"}
              >
                <SendHorizonal size={17} />
                {busyAction === "consultation" ? "正在提交问诊" : "提交给医生"}
              </button>
            </form>
          </section>
        </div>

        <section className="patient-section patient-progress-section" id="patient-progress">
          <div className="patient-section-heading">
            <span className="section-icon"><MessageSquareMore size={20} /></span>
            <div>
              <h3>问诊进度与资料</h3>
              <p>若系统或医生需要更多信息，请在对应问诊下继续补充。</p>
            </div>
          </div>

          <div className="patient-consultation-list">
            {dashboard.consultations.length ? dashboard.consultations.map((consultation) => (
              <article className="patient-consultation-row" key={consultation.id}>
                <div className="patient-consultation-summary">
                  <div>
                    <span className="patient-state">{statusCopy[consultation.status] || "处理中"}</span>
                    <h4>{consultation.doctorName} · {consultation.department || "接诊医生"}</h4>
                  </div>
                  <small>{formatTime(consultation.updatedAt)}</small>
                </div>

                <div className="patient-chat-thread" aria-label="病情问诊聊天记录">
                  {(consultation.messages || []).map((message, index) => (
                    <div
                      className={`patient-chat-message ${message.role === "assistant" ? "assistant" : "user"}`}
                      key={`${consultation.id}-${message.role}-${index}`}
                    >
                      <span>{message.role === "assistant" ? "病情整理 Agent" : "我"}</span>
                      <p>{message.content}</p>
                    </div>
                  ))}
                </div>

                <div className="patient-consultation-actions">
                  <label className="evidence-file-button">
                    <FileUp size={17} />
                    {consultation.evidenceFileName ? "重新上传检查资料" : "上传检查资料"}
                    <input
                      type="file"
                      accept=".pdf,.docx,.jpg,.jpeg,.png"
                      onChange={(event) => {
                        handleEvidence(consultation.id, event.target.files?.[0]);
                        event.target.value = "";
                      }}
                      disabled={busyAction === `evidence-${consultation.id}`}
                    />
                  </label>
                  {consultation.evidenceFileName ? (
                    <span className="patient-file-status">
                      <FileCheck2 size={16} />
                      {consultation.evidenceFileName}，{consultation.evidenceStatus === "CONFIRMED" ? "医生已确认" : "等待医生确认"}
                    </span>
                  ) : null}
                </div>

                {consultation.status === "NEEDS_PATIENT_REPLY" ? (
                  <div className="patient-reply-box">
                    <label>
                      补充回答
                      <textarea
                        value={replyText[consultation.id] || ""}
                        onChange={(event) =>
                          setReplyText((current) => ({ ...current, [consultation.id]: event.target.value }))
                        }
                        placeholder="请逐项回答上方问题，并补充具体时间、数值或用药名称。"
                      />
                    </label>
                    <button
                      className="primary-button"
                      type="button"
                      onClick={() => handleReply(consultation.id)}
                      disabled={!replyText[consultation.id]?.trim() || busyAction === `reply-${consultation.id}`}
                    >
                      提交补充信息
                    </button>
                  </div>
                ) : null}
              </article>
            )) : <p className="patient-empty-copy">暂无问诊记录。完成医生绑定后，可以从上方发起第一轮问诊。</p>}
          </div>
        </section>

        <section className="patient-section patient-report-section" id="patient-reports">
          <div className="patient-section-heading">
            <span className="section-icon"><BadgeCheck size={20} /></span>
            <div>
              <h3>医生发布的诊断报告</h3>
              <p>这里只展示医生完成审核并明确发布给你的报告。</p>
            </div>
          </div>

          <div className="patient-report-list">
            {dashboard.reports.length ? dashboard.reports.map((report) => (
              <details
                className="patient-report"
                key={report.id}
                onToggle={(event) => {
                  if (event.currentTarget.open) handleLoadExplanation(report.id);
                }}
              >
                <summary>
                  <div>
                    <strong>{report.finalConclusion || "医生诊断报告"}</strong>
                    <span>{report.doctorName} · {formatTime(report.publishedAt)}</span>
                  </div>
                  <span className="patient-state state-active">医生已发布</span>
                </summary>
                <div className="patient-report-body">
                  <section>
                    <h4>本次主诉</h4>
                    <p>{report.chiefComplaint || "未记录"}</p>
                  </section>
                  <section>
                    <h4>医生最终结论</h4>
                    <p>{report.finalConclusion || "未记录"}</p>
                  </section>
                  <section>
                    <h4>医生说明</h4>
                    <p>{report.doctorOpinion || "医生确认诊断结论，无额外修正。"}</p>
                  </section>
                  <section>
                    <h4>治疗与复诊建议</h4>
                    <p>{report.treatmentAdvice || "请遵循医生线下说明。"}</p>
                  </section>
                  <section className="patient-explanation-chat">
                    <div className="patient-explanation-heading">
                      <h4>向报告解释 Agent 提问</h4>
                      <p>只能解释这份已发布报告，不会修改诊断或提供新的用药方案。</p>
                    </div>
                    <div className="patient-chat-thread explanation">
                      {(explanationChats[report.id] || []).map((message, index) => (
                        <div
                          className={`patient-chat-message ${message.role === "assistant" ? "assistant" : "user"}`}
                          key={`${report.id}-explanation-${message.role}-${index}`}
                        >
                          <span>{message.role === "assistant" ? "报告解释 Agent" : "我"}</span>
                          <p>{message.content}</p>
                        </div>
                      ))}
                      {busyAction === `explanation-load-${report.id}` ? (
                        <p className="patient-explanation-loading">正在读取解释会话...</p>
                      ) : null}
                    </div>
                    <div className="patient-explanation-composer">
                      <textarea
                        value={explanationInputs[report.id] || ""}
                        onChange={(event) =>
                          setExplanationInputs((current) => ({
                            ...current,
                            [report.id]: event.target.value
                          }))
                        }
                        placeholder="例如：报告里的“中高风险”是什么意思？哪些情况需要尽快复诊？"
                      />
                      <button
                        className="primary-button"
                        type="button"
                        onClick={() => handleExplanationQuestion(report.id)}
                        disabled={
                          !explanationInputs[report.id]?.trim()
                          || busyAction === `explanation-${report.id}`
                        }
                      >
                        {busyAction === `explanation-${report.id}` ? "正在解释" : "询问报告内容"}
                      </button>
                    </div>
                  </section>
                </div>
              </details>
            )) : <p className="patient-empty-copy">暂无已发布报告。医生完成审核并发布后会显示在这里。</p>}
          </div>
        </section>
      </main>
    </div>
  );
}
