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
    setConsultationInput("");
    setFeedback("");
    setSessionDetail(null);
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
      if (detail?.diagnosis) {
        setDiagnosis(detail.diagnosis);
      }
      if (detail?.finalRecord?.chiefComplaint) {
        setPatient((current) =>
          current
            ? {
                ...current,
                chiefComplaint: detail.finalRecord.chiefComplaint
              }
            : current
        );
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
      const response = await submitConsultation(consultationInput, activeSessionId);
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
          sessions={sessions}
          activeSessionId={activeSessionId}
          consultationInput={consultationInput}
          onConsultationInputChange={setConsultationInput}
          onStartNewConsultation={handleStartNewConsultation}
          onSubmitConsultation={handleSubmitConsultation}
          onSelectSession={handleSelectSession}
          onDeleteSession={handleDeleteSession}
          consultationSubmitting={consultationSubmitting}
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
          feedback={feedback}
          submitting={submitting}
        />
      </main>
    </div>
  );
}
