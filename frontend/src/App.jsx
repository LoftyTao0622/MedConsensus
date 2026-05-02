import { useEffect, useState } from "react";
import { Header } from "./components/Header";
import { Sidebar } from "./components/Sidebar";
import { DiagnosticPanel } from "./components/DiagnosticPanel";
import { DoctorPanel } from "./components/DoctorPanel";
import { AuthPanel } from "./components/AuthPanel";
import { fetchCurrentUser, logoutUser } from "./api/auth";
import {
  deleteSession,
  fetchDiagnosis,
  fetchPatientProfile,
  fetchSessionDetail,
  fetchSessions,
  submitConsultation,
  simulatePipeline,
  submitDoctorReview
} from "./api/workspace";
import { connectPipelineSocket } from "./api/websocket";

const initialEvents = [
  {
    stage: "SYSTEM",
    message: "等待启动诊断工作流",
    progress: 0,
    timestamp: new Date().toISOString()
  }
];

const emptyPatient = {
  patientId: "PATIENT-DRAFT",
  name: "",
  age: "",
  weight: "",
  phone: "",
  gender: "",
  loginStatus: "患者信息待填写",
  chiefComplaint: "",
  highlights: ["病情整理 Agent 已启用"]
};

function buildConsultationMessage(patient, note) {
  const fields = [
    patient?.name ? `患者姓名：${patient.name}` : "",
    patient?.age ? `年龄：${patient.age}岁` : "",
    patient?.weight ? `体重：${patient.weight}kg` : "",
    patient?.gender ? `性别：${patient.gender}` : "",
    patient?.phone ? `患者电话：${patient.phone}` : "",
    patient?.chiefComplaint ? `主诉：${patient.chiefComplaint}` : "",
    note?.trim() ? `补充病情：${note.trim()}` : ""
  ].filter(Boolean);

  return fields.join("\n");
}

function parseField(text, label) {
  const match = text?.match(new RegExp(`${label}：([^\\n]+)`));
  return match?.[1]?.trim() || "";
}

function patientFromSessionDetail(detail) {
  const firstUserMessage = detail?.history?.find((item) => item.role === "user")?.content || "";
  return {
    ...emptyPatient,
    loginStatus: "患者信息已载入",
    name: parseField(firstUserMessage, "患者姓名") || detail?.title || "",
    age: parseField(firstUserMessage, "年龄").replace(/岁$/, ""),
    weight: parseField(firstUserMessage, "体重").replace(/kg$/i, ""),
    phone: parseField(firstUserMessage, "患者电话"),
    gender: parseField(firstUserMessage, "性别"),
    chiefComplaint:
      parseField(firstUserMessage, "主诉") ||
      detail?.finalRecord?.chiefComplaint ||
      detail?.title ||
      ""
  };
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function listHtml(items) {
  if (!items?.length) {
    return "<p>未记录</p>";
  }
  return `<ul>${items.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>`;
}

function safeFileName(value) {
  return String(value || "未命名患者").replace(/[\\/:*?"<>|]/g, "_").slice(0, 40);
}

function buildDiagnosisReportHtml({ patient, diagnosis, opinion, finalRecord, doctorName }) {
  const doctorOpinion = finalRecord?.doctorOpinion || opinion || "医生确认 AI 结论，无额外修正。";
  const finalConclusion = finalRecord?.finalConclusion || doctorOpinion || diagnosis?.conclusion || "未形成最终结论";
  const generatedAt = new Date().toLocaleString("zh-CN");

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>诊断报告-${escapeHtml(patient?.name || "未命名患者")}</title>
  <style>
    body { margin: 0; padding: 32px; color: #102f49; font-family: "Microsoft YaHei", "Noto Sans SC", sans-serif; background: #f4f8fc; }
    .report { max-width: 920px; margin: 0 auto; padding: 36px; border-radius: 20px; background: #fff; box-shadow: 0 18px 50px rgba(16, 47, 73, 0.12); }
    .header { display: flex; justify-content: space-between; gap: 20px; border-bottom: 2px solid #d9e8f5; padding-bottom: 18px; margin-bottom: 24px; }
    h1 { margin: 0 0 8px; font-size: 28px; }
    h2 { margin: 26px 0 12px; font-size: 20px; color: #0f4e82; }
    p { line-height: 1.8; margin: 0; }
    ul { margin: 0; padding-left: 22px; }
    li { line-height: 1.8; margin: 4px 0; }
    .meta { color: #5b7590; font-size: 13px; line-height: 1.7; text-align: right; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
    .field { padding: 14px 16px; border: 1px solid #d9e8f5; border-radius: 14px; background: #f8fbfe; }
    .field span { display: block; margin-bottom: 6px; color: #5b7590; font-size: 12px; font-weight: 700; }
    .block { padding: 16px; border: 1px solid #d9e8f5; border-radius: 14px; background: #fbfdff; }
    .conclusion { border-color: #9bc9ee; background: #eef7ff; font-weight: 700; }
    .actions { position: sticky; top: 0; display: flex; justify-content: flex-end; margin: -16px -16px 18px 0; }
    button { border: 0; border-radius: 12px; padding: 10px 16px; color: #fff; background: #1d71b8; font-weight: 700; cursor: pointer; }
    @media print {
      body { padding: 0; background: #fff; }
      .report { max-width: none; box-shadow: none; border-radius: 0; }
      .actions { display: none; }
    }
  </style>
</head>
<body>
  <main class="report">
    <div class="actions"><button onclick="window.print()">打印报告</button></div>
    <section class="header">
      <div>
        <h1>智能辅助诊断报告</h1>
        <p>本报告由医生结合 AI 辅助诊断结果审核生成，最终诊断以医生意见为准。</p>
      </div>
      <div class="meta">
        生成时间：${escapeHtml(generatedAt)}<br />
        诊断医生：${escapeHtml(doctorName || "未记录")}<br />
        会话编号：${escapeHtml(finalRecord?.sessionId || "未记录")}
      </div>
    </section>

    <h2>一、患者基本信息</h2>
    <section class="grid">
      <div class="field"><span>患者姓名</span>${escapeHtml(patient?.name || "未填写")}</div>
      <div class="field"><span>患者电话</span>${escapeHtml(patient?.phone || "未填写")}</div>
      <div class="field"><span>年龄</span>${escapeHtml(patient?.age || "未填写")} ${patient?.age ? "岁" : ""}</div>
      <div class="field"><span>体重</span>${escapeHtml(patient?.weight || "未填写")} ${patient?.weight ? "kg" : ""}</div>
      <div class="field"><span>性别</span>${escapeHtml(patient?.gender || "未填写")}</div>
      <div class="field"><span>风险等级</span>${escapeHtml(diagnosis?.riskLevel || finalRecord?.riskLevel || "待评估")}</div>
    </section>

    <h2>二、主诉</h2>
    <section class="block"><p>${escapeHtml(patient?.chiefComplaint || finalRecord?.chiefComplaint || "未记录")}</p></section>

    <h2>三、AI 辅助诊断结果</h2>
    <section class="block">
      <p>${escapeHtml(diagnosis?.conclusion || finalRecord?.aiConclusion || "未生成")}</p>
      <p>AI 置信度：${Math.round((diagnosis?.confidence || finalRecord?.confidence || 0) * 100)}%</p>
    </section>

    <h2>四、诊断依据</h2>
    <section class="block">${listHtml(diagnosis?.structuredAnalysis)}</section>

    <h2>五、医生意见</h2>
    <section class="block"><p>${escapeHtml(doctorOpinion)}</p></section>

    <h2>六、最终诊断结果</h2>
    <section class="block conclusion"><p>${escapeHtml(finalConclusion)}</p></section>
  </main>
</body>
</html>`;
}

function downloadDiagnosisReport(reportHtml, patientName) {
  const blob = new Blob([reportHtml], { type: "text/html;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `诊断报告-${safeFileName(patientName)}.html`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function App() {
  const [patient, setPatient] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [diagnosis, setDiagnosis] = useState(null);
  const [pipelineEvents, setPipelineEvents] = useState(initialEvents);
  const [opinion, setOpinion] = useState("");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [consultationInput, setConsultationInput] = useState("");
  const [consultationSubmitting, setConsultationSubmitting] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [sessionDetail, setSessionDetail] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const canSubmitConsultation = Boolean(
    patient?.name?.trim() && (consultationInput.trim() || patient?.chiefComplaint?.trim())
  );

  useEffect(() => {
    async function restoreSession() {
      try {
        const user = await fetchCurrentUser();
        setCurrentUser(user);
      } catch (error) {
        setCurrentUser(null);
      } finally {
        setAuthChecked(true);
      }
    }

    restoreSession();
  }, []);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    async function bootstrap() {
      const [patientData, sessionData, diagnosisData] = await Promise.all([
        fetchPatientProfile(),
        fetchSessions(),
        fetchDiagnosis()
      ]);

      setPatient(patientData);
      setSessions(sessionData);
      setDiagnosis(diagnosisData);
    }

    bootstrap().catch((error) => {
      setFeedback(`初始化失败: ${error.message}`);
    });
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser) {
      return undefined;
    }

    const disconnect = connectPipelineSocket((event) => {
      setPipelineEvents((current) => [...current, event]);
    });

    return () => disconnect();
  }, [currentUser]);

  function handleAuthenticated(user) {
    setCurrentUser(user);
    setAuthChecked(true);
  }

  async function handleLogout() {
    try {
      await logoutUser();
    } catch (error) {
      console.error("Logout failed:", error);
    } finally {
      setCurrentUser(null);
      setPatient(null);
      setSessions([]);
      setDiagnosis(null);
      setFeedback("");
      setOpinion("");
      setConsultationInput("");
      setActiveSessionId(null);
      setSessionDetail(null);
      setPipelineEvents(initialEvents);
      setAuthChecked(true);
    }
  }

  function handleStartNewConsultation() {
    setActiveSessionId(null);
    setPatient(emptyPatient);
    setConsultationInput("");
    setFeedback("");
    setSessionDetail(null);
    setDiagnosis(null);
  }

  async function handleDeleteSession(sessionId) {
    setFeedback("");

    try {
      const response = await deleteSession(sessionId);
      setSessions((current) => current.filter((session) => session.id !== sessionId));

      if (activeSessionId === sessionId) {
        setActiveSessionId(null);
        setSessionDetail(null);
        setDiagnosis(null);
        setConsultationInput("");
        setPatient(emptyPatient);
      }

      setFeedback(response.message);
    } catch (error) {
      setFeedback(`删除会话失败: ${error.message}`);
    }
  }

  async function handleSelectSession(sessionId) {
    setActiveSessionId(sessionId);
    setFeedback("");

    try {
      const detail = await fetchSessionDetail(sessionId);
      setSessionDetail(detail);
      setPatient(patientFromSessionDetail(detail));
      if (detail?.diagnosis) {
        setDiagnosis(detail.diagnosis);
      }
      setFeedback(`已切换到会话 ${sessionId}，已载入完整追问历史。`);
    } catch (error) {
      setFeedback(`读取会话详情失败: ${error.message}`);
    }
  }

  async function handleSubmitConsultation() {
    setConsultationSubmitting(true);
    setFeedback("");

    try {
      const response = await submitConsultation(
        buildConsultationMessage(patient, consultationInput),
        activeSessionId,
        patient
      );
      setActiveSessionId(response.sessionId);
      setConsultationInput("");
      setDiagnosis(response.diagnosis);
      setSessions((current) => {
        const next = current.filter((item) => item.id !== response.session.id);
        return [response.session, ...next];
      });
      setPatient((current) =>
        current
          ? {
              ...current,
              loginStatus: "患者信息已填写",
              chiefComplaint: response.chiefComplaint
            }
          : current
      );
      const detail = await fetchSessionDetail(response.sessionId);
      setSessionDetail(detail);
      setFeedback("病情整理 Agent 已完成本轮信息收集。");
    } catch (error) {
      setFeedback(`病情整理失败: ${error.message}`);
    } finally {
      setConsultationSubmitting(false);
    }
  }

  async function handleSimulate() {
    setBusy(true);
    setPipelineEvents(initialEvents);
    setFeedback("");

    try {
      const response = await simulatePipeline();
      setDiagnosis(response);
    } catch (error) {
      setFeedback(`流程模拟失败: ${error.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function handleApprove() {
    await handleDoctorSubmit("");
  }

  async function handleDoctorSubmit(customOpinion = opinion) {
    setSubmitting(true);
    setFeedback("");

    try {
      const response = await submitDoctorReview({
        sessionId: activeSessionId,
        aiConclusion: diagnosis?.conclusion,
        chiefComplaint: patient?.chiefComplaint,
        riskLevel: diagnosis?.riskLevel,
        confidence: diagnosis?.confidence,
        opinion: customOpinion
      });
      setFeedback(response.message);
      if (response.finalRecord) {
        setSessionDetail((current) =>
          current
            ? {
                ...current,
                finalRecord: response.finalRecord
              }
            : current
        );
      }
      if (!customOpinion) {
        setOpinion("");
      }
    } catch (error) {
      setFeedback(`提交失败: ${error.message}`);
    } finally {
      setSubmitting(false);
    }
  }

  function handleGenerateReport() {
    if (!diagnosis || !patient) {
      setFeedback("请先完成患者诊断后再生成报告。");
      return;
    }

    const reportHtml = buildDiagnosisReportHtml({
      patient,
      diagnosis,
      opinion,
      finalRecord: sessionDetail?.finalRecord,
      doctorName: currentUser?.username
    });
    downloadDiagnosisReport(reportHtml, patient.name);
    setFeedback("诊断报告已生成，可打开下载的 HTML 文件进行打印。");
  }

  if (!authChecked) {
    return (
      <div className="auth-shell">
        <div className="background-orb orb-one" />
        <div className="background-orb orb-two" />
        <section className="auth-card glass-card">
          <p className="eyebrow">Loading Session</p>
          <h1>正在校验登录状态</h1>
          <p className="doctor-copy">系统正在从后端读取当前登录用户，请稍候。</p>
        </section>
      </div>
    );
  }

  if (!currentUser) {
    return <AuthPanel onAuthenticated={handleAuthenticated} />;
  }

  return (
    <div className="app-shell">
      <div className="background-orb orb-one" />
      <div className="background-orb orb-two" />

      <Header currentUser={currentUser} onLogout={handleLogout} />

      <main className="workspace-grid">
        <Sidebar
          patient={patient}
          onPatientChange={setPatient}
          sessions={sessions}
          activeSessionId={activeSessionId}
          consultationInput={consultationInput}
          onConsultationInputChange={setConsultationInput}
          onStartNewConsultation={handleStartNewConsultation}
          onSubmitConsultation={handleSubmitConsultation}
          onSelectSession={handleSelectSession}
          onDeleteSession={handleDeleteSession}
          consultationSubmitting={consultationSubmitting}
          canSubmitConsultation={canSubmitConsultation}
        />
        <DiagnosticPanel
          diagnosis={diagnosis}
          pipelineEvents={pipelineEvents}
          sessionDetail={sessionDetail}
          onSimulate={handleSimulate}
          busy={busy}
        />
        <DoctorPanel
          activeSessionId={activeSessionId}
          diagnosis={diagnosis}
          patient={patient}
          opinion={opinion}
          onOpinionChange={setOpinion}
          onApprove={handleApprove}
          onSubmit={() => handleDoctorSubmit(opinion)}
          onGenerateReport={handleGenerateReport}
          feedback={feedback}
          submitting={submitting}
          reportDisabled={!activeSessionId || !diagnosis}
        />
      </main>
    </div>
  );
}
