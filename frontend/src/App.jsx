import { useEffect, useRef, useState } from "react";
import { Check, Bell, Upload, X, FileText, Image, Loader2, Trash2 } from "lucide-react";
import { NavigationSidebar } from "./components/NavigationSidebar";
import { SettingsTab } from "./components/SettingsTab";
import { Sidebar } from "./components/Sidebar";
import { DiagnosticPanel } from "./components/DiagnosticPanel";
import { DoctorPanel } from "./components/DoctorPanel";
import { SocialCallbackPanel } from "./components/SocialLogin";
import { readSocialCallback } from "./socialLogin";
import { AuthPanel } from "./components/AuthPanel";
import { DoctorProfileTab } from "./components/DoctorProfileTab";
import { EvidenceReviewPanel } from "./components/EvidenceReviewPanel";
import { PatientPortal } from "./components/PatientPortal";
import { DoctorCollaborationPanel } from "./components/DoctorCollaborationPanel";
import { fetchCurrentUser, logoutUser } from "./api/auth";
import {
  createPatient,
  deletePatient,
  deleteDiagnosisRecord,
  deleteSession,
  fetchPatients,
  fetchPatient,
  fetchSessionDetail,
  fetchSessions,
  updatePatient,
  submitConsultation,
  simulatePipeline,
  submitDoctorReview,
  importCase,
  analyzeMedicalEvidence,
  fetchDiagnosisRecords,
  fetchDoctorCollaboration,
  approvePatientBinding,
  confirmPatientEvidence,
  publishDiagnosisRecord
} from "./api/workspace";
import { ReportVersions } from "./components/ReportVersions";
import { ReviewerResults } from "./components/ReviewerResults";
import { SessionEvidence } from "./components/EvidenceSource";
import { buildDiagnosisReportHtml, reportFileLabel, isReviewedRecord, escapeHtml } from "./reportExport";
import { draftKey, emptyDraft, updateDraft } from "./consultationDraft";
import { connectPipelineSocket } from "./api/websocket";

const initialEvents = [
  {
    stage: "SYSTEM",
    message: "等待启动诊断工作流",
    progress: 0,
    timestamp: new Date().toISOString()
  }
];

const REMINDER_STORAGE_KEY = "medconsensus-doctor-reminders";
const CARE_MODE_STORAGE_KEY = "medconsensus-care-mode";
const MAX_UPLOAD_BYTES = 50 * 1024 * 1024;

function readStoredReminders(key) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "[]");
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function readStoredCareMode() {
  try {
    return localStorage.getItem(CARE_MODE_STORAGE_KEY) === "enabled";
  } catch {
    return false;
  }
}

const emptyPatient = {
  id: null,
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

function patientFromApi(patient) {
  if (!patient) {
    return emptyPatient;
  }

  return {
    ...emptyPatient,
    ...patient,
    patientId: patient.id ? `PATIENT-${patient.id}` : "PATIENT-DRAFT",
    age: patient.age == null ? "" : String(patient.age),
    weight: patient.weight == null ? "" : String(patient.weight),
    phone: patient.phone || "",
    gender: patient.gender || "",
    chiefComplaint: patient.chiefComplaint || "",
    loginStatus: "患者信息已保存"
  };
}

function patientPayload(patient) {
  return {
    name: patient?.name?.trim() || "",
    gender: patient?.gender || "",
    age: patient?.age === "" || patient?.age == null ? null : Number(patient.age),
    weight: patient?.weight === "" || patient?.weight == null ? null : Number(patient.weight),
    phone: patient?.phone?.trim() || "",
    chiefComplaint: patient?.chiefComplaint?.trim() || ""
  };
}

function buildConsultationMessage(patient, note) {
  const fields = [
    patient?.name ? `患者姓名：${patient.name}` : "",
    patient?.age ? `年龄：${patient.age}岁` : "",
    patient?.weight ? `体重：${patient.weight}kg` : "",
    patient?.gender ? `性别：${patient.gender}` : "",
    patient?.phone ? `患者电话：${patient.phone}` : "",
    patient?.chiefComplaint ? `主诉：${patient.chiefComplaint}` : "",
    note?.trim() ? `本次诉求：${note.trim()}` : ""
  ].filter(Boolean);

  return fields.join("\n");
}

function createSessionId() {
  const randomPart = typeof globalThis.crypto?.randomUUID === "function"
    ? globalThis.crypto.randomUUID().replaceAll("-", "").slice(0, 12)
    : Math.random().toString(36).slice(2, 14);
  return `chat-${randomPart}`;
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
    name: parseField(firstUserMessage, "患者姓名"),
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

function safeFileName(value) {
  return String(value || "未命名患者").replace(/[\\/:*?"<>|]/g, "_").slice(0, 40);
}

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) {
    return "未知大小";
  }
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${(bytes / 1024).toFixed(1)} KB`;
}

function downloadDiagnosisReport(reportHtml, patientName, record) {
  const blob = new Blob([reportHtml], { type: "text/html;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${reportFileLabel(record)}-${safeFileName(patientName)}.html`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function buildPrescriptionHtml({ patient, finalRecord, doctorName }) {
  const treatmentLines = finalRecord?.treatmentAdvice
    ? finalRecord.treatmentAdvice.split("\n").filter(Boolean)
    : [];
  const generatedAt = new Date().toLocaleString("zh-CN");
  const keywords = finalRecord?.treatmentKeywords || [];
  const source = finalRecord?.treatmentSource === "DATABASE" ? "PostgreSQL 命中" : "MiMo 推理";

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>药单-${escapeHtml(patient?.name || "未命名患者")}</title>
  <style>
    body { margin: 0; padding: 32px; color: #102f49; font-family: "Microsoft YaHei", "Noto Sans SC", sans-serif; background: #f4f8fc; }
    .prescription { max-width: 720px; margin: 0 auto; padding: 36px; border-radius: 20px; background: #fff; box-shadow: 0 18px 50px rgba(16, 47, 73, 0.12); }
    .header { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; border-bottom: 2px solid #1d71b8; padding-bottom: 18px; margin-bottom: 24px; }
    .header h1 { margin: 0 0 6px; font-size: 24px; color: #0f4e82; }
    .header p { margin: 0; color: #5b7590; font-size: 13px; }
    .meta { text-align: right; font-size: 13px; color: #5b7590; line-height: 1.7; }
    .patient-info { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 24px; }
    .patient-info div { padding: 10px 14px; border: 1px solid #d9e8f5; border-radius: 12px; background: #f8fbfe; }
    .patient-info span { display: block; font-size: 11px; color: #5b7590; font-weight: 700; margin-bottom: 4px; }
    .patient-info strong { font-size: 14px; color: #10324d; }
    .section-title { font-size: 16px; font-weight: 800; color: #0f4e82; margin: 20px 0 12px; padding-bottom: 8px; border-bottom: 1px solid #e4eef8; }
    .rx-list { list-style: none; padding: 0; margin: 0; }
    .rx-item { display: flex; align-items: flex-start; gap: 12px; padding: 12px 16px; border: 1px solid #d9e8f5; border-radius: 12px; margin-bottom: 10px; background: #fbfdff; }
    .rx-num { flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; background: linear-gradient(135deg, #1d71b8, #49a8df); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 800; }
    .rx-text { flex: 1; line-height: 1.7; font-size: 14px; color: #23425e; }
    .keywords { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .keyword { padding: 4px 12px; border-radius: 999px; font-size: 12px; background: #d9ecfb; color: #0f4e82; font-weight: 700; }
    .footer { margin-top: 32px; padding-top: 18px; border-top: 2px solid #d9e8f5; display: flex; justify-content: space-between; align-items: flex-end; }
    .footer-left { font-size: 13px; color: #5b7590; line-height: 1.7; }
    .footer-right { text-align: right; }
    .footer-right .sign-label { font-size: 12px; color: #5b7590; margin-bottom: 40px; }
    .footer-right .sign-line { width: 180px; border-bottom: 1px solid #10324d; margin-bottom: 6px; }
    .footer-right .sign-name { font-size: 14px; font-weight: 700; color: #10324d; }
    .actions { position: sticky; top: 0; display: flex; justify-content: flex-end; margin: -16px -16px 18px 0; }
    button { border: 0; border-radius: 12px; padding: 10px 16px; color: #fff; background: #1d71b8; font-weight: 700; cursor: pointer; }
    @media print {
      body { padding: 0; background: #fff; }
      .prescription { max-width: none; box-shadow: none; border-radius: 0; }
      .actions { display: none; }
    }
  </style>
</head>
<body>
  <main class="prescription">
    <div class="actions"><button onclick="window.print()">打印药单</button></div>
    <section class="header">
      <div>
        <h1>处方笺</h1>
        <p>本药单由医生结合 AI 辅助诊断结果审核生成。</p>
      </div>
      <div class="meta">
        开方时间：${escapeHtml(generatedAt)}<br />
        开方医生：${escapeHtml(doctorName || "未记录")}<br />
        会话编号：${escapeHtml(finalRecord?.sessionId || "未记录")}
      </div>
    </section>

    <section class="patient-info">
      <div><span>患者姓名</span><strong>${escapeHtml(patient?.name || "未填写")}</strong></div>
      <div><span>性别</span><strong>${escapeHtml(patient?.gender || "未填写")}</strong></div>
      <div><span>年龄</span><strong>${escapeHtml(patient?.age || "未填写")}${patient?.age ? " 岁" : ""}</strong></div>
      <div><span>体重</span><strong>${escapeHtml(patient?.weight || "未填写")}${patient?.weight ? " kg" : ""}</strong></div>
      <div><span>联系电话</span><strong>${escapeHtml(patient?.phone || "未填写")}</strong></div>
      <div><span>主诉</span><strong>${escapeHtml(patient?.chiefComplaint || "未记录")}</strong></div>
    </section>

    ${keywords.length ? `<div class="keywords">${keywords.map((k) => `<span class="keyword">${escapeHtml(k)}</span>`).join("")}</div>` : ""}

    <div class="section-title">处方用药 / 治疗建议（${escapeHtml(source)}）</div>
    ${treatmentLines.length ? `<ol class="rx-list">${treatmentLines.map((line, i) => `<li class="rx-item"><span class="rx-num">${i + 1}</span><span class="rx-text">${escapeHtml(line.replace(/^[•·]\s*/, ""))}</span></li>`).join("")}</ol>` : `<p style="color:#5b7590;font-size:14px;">暂无用药建议</p>`}

    <section class="footer">
      <div class="footer-left">
        药师/发药人签字：<br />
        <span style="display:inline-block;width:180px;border-bottom:1px solid #10324d;margin-top:24px;"></span>
      </div>
      <div class="footer-right">
        <div class="sign-label">开方医生签字</div>
        <div class="sign-line"></div>
        <div class="sign-name">${escapeHtml(doctorName || "医生")}</div>
      </div>
    </section>
  </main>
</body>
</html>`;
}

function downloadPrescription(prescriptionHtml, patientName) {
  const blob = new Blob([prescriptionHtml], { type: "text/html;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `药单-${safeFileName(patientName)}.html`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export default function App() {
  const [currentUser, setCurrentUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [socialCallback, setSocialCallback] = useState(() => readSocialCallback(window.location.search));
  useEffect(() => {
    let active = true;
    async function restoreSession() {
      try {
        const user = await fetchCurrentUser();
        if (active) setCurrentUser(user);
      } catch (error) {
        if (active) setCurrentUser(null);
      } finally {
        if (active) setAuthChecked(true);
      }
    }

    restoreSession();
    return () => { active = false; };
  }, []);

  function finishSocialCallback() {
    window.history.replaceState(null, "", "/");
    setSocialCallback(null);
  }

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

  if (socialCallback) {
    return <SocialCallbackPanel action={socialCallback} onBack={finishSocialCallback} onBound={(user) => {
      handleAuthenticated(user);
      finishSocialCallback();
    }} />;
  }

  if (currentUser?.role === "PATIENT") {
    return <PatientPortal currentUser={currentUser} onLogout={handleLogout} />;
  }

  if (currentUser?.role === "DOCTOR") {
    return <DoctorWorkspace key={currentUser.id || currentUser.username} currentUser={currentUser} handleLogout={handleLogout} />;
  }
  return <AuthPanel onAuthenticated={handleAuthenticated} />;
}

function DoctorWorkspace({ currentUser, handleLogout }) {
  const [patient, setPatient] = useState(null);
  const [patients, setPatients] = useState([]);
  const [sessions, setSessions] = useState([]);
  const [diagnosis, setDiagnosis] = useState(null);
  const [pipelineEvents, setPipelineEvents] = useState(initialEvents);
  const [feedback, setFeedback] = useState("");
  const [demo, setDemo] = useState(null);
  const [busy, setBusy] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const activeSessionIdRef = useRef(null);
  const [sessionDetail, setSessionDetail] = useState(null);
  const [patientSaving, setPatientSaving] = useState(false);
  const [activeTab, setActiveTab] = useState("workspace");
  const reminderKey = REMINDER_STORAGE_KEY + "-" + (currentUser.id || currentUser.username);
  const [reminders, setReminders] = useState(() => readStoredReminders(reminderKey));
  const [activeReminder, setActiveReminder] = useState(null);
  const [careMode, setCareMode] = useState(() => readStoredCareMode());
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [diagnosisRecords, setDiagnosisRecords] = useState([]);
  const [dragOver, setDragOver] = useState(false);
  const [collaboration, setCollaboration] = useState(null);
  const [collaborationBusyAction, setCollaborationBusyAction] = useState("");
  const selectionEpoch = useRef(0);
  const [drafts, setDrafts] = useState({});
  const owner = draftKey(patient?.id, activeSessionId);
  const draft = drafts[owner] || emptyDraft;
  const { opinion, consultationInput, consultationSubmitting, medicalEvidence, medicalEvidenceFile,
    medicalEvidenceAnalyzing, medicalEvidenceConfirmed, medicalEvidenceError, medicalEvidenceReviewNote, submitting } = draft;

  function patchDraft(patch) {
    setDrafts((current) => updateDraft(current, owner, patch));
  }
  const hasUploadedMedicalEvidence = Boolean(medicalEvidenceFile || medicalEvidence);
  const hasConfirmedMedicalEvidence = Boolean(medicalEvidence?.evidenceText && medicalEvidenceConfirmed);
  const canSubmitConsultation = Boolean(
    (patient?.id || (activeSessionId && sessionDetail?.sessionId === activeSessionId))
      && patient?.name?.trim()
      && (consultationInput.trim() || hasUploadedMedicalEvidence || hasConfirmedMedicalEvidence)
  );

  useEffect(() => {
    document.documentElement.classList.toggle("care-mode", careMode);
    localStorage.setItem(CARE_MODE_STORAGE_KEY, careMode ? "enabled" : "disabled");
  }, [careMode]);

  useEffect(() => {
    localStorage.setItem(reminderKey, JSON.stringify(reminders));
  }, [reminders, reminderKey]);

  useEffect(() => {
    const timers = reminders.map((reminder) => {
      const delay = Math.max(0, reminder.dueAt - Date.now());
      return window.setTimeout(() => {
        setActiveReminder(reminder);
        setReminders((current) => current.filter((item) => item.id !== reminder.id));
      }, delay);
    });

    return () => timers.forEach((timer) => window.clearTimeout(timer));
  }, [reminders]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "DOCTOR") {
      return;
    }

    let cancelled = false;
    const epoch = selectionEpoch.current;
    async function bootstrap() {
      const [patientData, sessionData, recordsData, collaborationData] = await Promise.all([
        fetchPatients(),
        fetchSessions(),
        fetchDiagnosisRecords().catch(() => []),
        fetchDoctorCollaboration().catch(() => null)
      ]);

      if (cancelled) return;
      const normalizedPatients = patientData.map(patientFromApi);
      setPatients(normalizedPatients);
      if (selectionEpoch.current === epoch) setPatient(normalizedPatients[0] || emptyPatient);
      setSessions(sessionData);
      setDiagnosisRecords(recordsData);
      setCollaboration(collaborationData);
    }

    bootstrap().catch((error) => {
      if (!cancelled) setFeedback(`初始化失败: ${error.message}`);
    });
    return () => { cancelled = true; selectionEpoch.current += 1; };
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "DOCTOR") {
      return undefined;
    }

    const disconnect = connectPipelineSocket((event) => {
      if (!event.sessionId || event.sessionId !== activeSessionIdRef.current) {
        return;
      }
      setPipelineEvents((current) => [...current, event]);
    });

    return () => disconnect();
  }, [currentUser]);

  function changeSelection(sessionId) {
    selectionEpoch.current += 1;
    activeSessionIdRef.current = sessionId;
    setActiveSessionId(sessionId);
    setPatient(null);
    setSessionDetail(null);
    setDiagnosis(null);
    setFeedback("");
    setPipelineEvents(initialEvents);
    setDemo(null);
    setBusy(false);
  }

  function handleSelectPatient(nextPatient) {
    changeSelection(null);
    setPatient(nextPatient);
  }

  async function handleSavePatient(nextPatient) {
    const epoch = selectionEpoch.current;
    setPatientSaving(true);
    setFeedback("");

    try {
      const savedPatient = nextPatient.id
        ? await updatePatient(nextPatient.id, patientPayload(nextPatient))
        : await createPatient(patientPayload(nextPatient));
      const normalizedPatient = patientFromApi(savedPatient);
      setPatients((current) => {
        const withoutSaved = current.filter((item) => item.id !== normalizedPatient.id);
        return [normalizedPatient, ...withoutSaved];
      });
      if (selectionEpoch.current !== epoch) return;
      if (normalizedPatient.id !== patient?.id) handleSelectPatient(normalizedPatient);
      else setPatient(normalizedPatient);
      setFeedback(nextPatient.id ? "患者信息已更新。" : "患者信息已保存。");
      return normalizedPatient;
    } catch (error) {
      setFeedback(`保存患者失败: ${error.message}`);
      throw error;
    } finally {
      setPatientSaving(false);
    }
  }

  async function handleDeletePatient(patientId) {
    const epoch = selectionEpoch.current;
    setFeedback("");

    try {
      const response = await deletePatient(patientId);
      setPatients((current) => current.filter((item) => item.id !== patientId));
      if (selectionEpoch.current === epoch && patient?.id === patientId) {
        handleSelectPatient(patients.find((item) => item.id !== patientId) || emptyPatient);
      }
      setDrafts((current) => { const next = { ...current }; delete next[draftKey(patientId, null)]; return next; });
      setFeedback(response.message);
    } catch (error) {
      setFeedback(`删除患者失败: ${error.message}`);
    }
  }

  async function handleDeleteSession(sessionId) {
    setFeedback("");

    try {
      const response = await deleteSession(sessionId);
      setSessions((current) => current.filter((session) => session.id !== sessionId));

      if (activeSessionIdRef.current === sessionId) handleSelectPatient(emptyPatient);
      setDrafts((current) => { const next = { ...current }; delete next[draftKey(null, sessionId)]; return next; });

      setFeedback(response.message);
    } catch (error) {
      setFeedback(`归档会话失败：${error.message}`);
    }
  }

  async function handleDeleteDiagnosisRecord(recordId) {
    setFeedback("");

    try {
      const response = await deleteDiagnosisRecord(recordId);
      setDiagnosisRecords((current) => current.filter((r) => r.id !== recordId));
      setSessionDetail((current) => current?.finalRecord?.id === recordId ? { ...current, finalRecord: null } : current);
      setFeedback(response.message);
    } catch (error) {
      setFeedback(`删除诊断报告失败: ${error.message}`);
    }
  }

  function patientForDetail(detail) {
    const recordId = detail.patientRecordId || detail.finalRecord?.patientRecordId;
    return patients.find((item) => item.id === recordId) || { ...patientFromSessionDetail(detail), id: recordId || null };
  }

  async function handleSelectSession(sessionId) {
    changeSelection(sessionId);
    const epoch = selectionEpoch.current;

    try {
      const detail = await fetchSessionDetail(sessionId);
      if (selectionEpoch.current !== epoch) return;
      setSessionDetail(detail);
      setPatient(patientForDetail(detail));
      setDiagnosis(detail.diagnosis || null);
      setFeedback(`已切换到会话 ${sessionId}，已载入完整追问历史。`);
    } catch (error) {
      if (selectionEpoch.current !== epoch) return;
      setFeedback(`读取会话详情失败: ${error.message}`);
    }
  }

  function handleAppendPatientEvidence(evidence) {
    const normalized = evidence.trim();
    if (!normalized) {
      return;
    }
    patchDraft({ consultationInput: [consultationInput.trim(), "补充诊断依据：" + normalized].filter(Boolean).join("\n\n") });
    setActiveTab("workspace");
    setFeedback("补充依据已填入患者诉求，请确认后提交。");
  }

  async function handleSubmitConsultation({
    evidenceConfirmed = medicalEvidenceConfirmed,
    allowMedicalEvidenceSubmission = false
  } = {}) {
    if (hasUploadedMedicalEvidence && !allowMedicalEvidenceSubmission) {
      setFeedback(
        medicalEvidenceAnalyzing
          ? "检查资料仍在识别中，请等待识别完成并由医生确认或清除后再进入诊断 Agent。"
          : "本轮已上传检查资料，请先对 CT/检查资料作出确认或清除决定，再进入诊断 Agent。"
      );
      setActiveTab("evidence");
      return;
    }

    if (medicalEvidenceAnalyzing) {
      setFeedback("检查资料仍在识别中，请等待识别完成并由医生确认后再进入诊断 Agent。");
      setActiveTab("evidence");
      return;
    }

    if (medicalEvidenceFile && !evidenceConfirmed) {
      setFeedback("已上传检查资料但尚未确认，请先到检查资料页面确认或清除后再进入诊断 Agent。");
      setActiveTab("evidence");
      return;
    }

    if (consultationSubmitting || !canSubmitConsultation) return;
    const epoch = selectionEpoch.current;
    patchDraft({ consultationSubmitting: true });
    setFeedback("");
    const requestSessionId = activeSessionId || draft.requestSessionId || createSessionId();
    const createdSession = !activeSessionId;
    if (createdSession) {
      activeSessionIdRef.current = requestSessionId;
      patchDraft({ requestSessionId });
      setPipelineEvents(initialEvents);
    }

    try {
      const consultationNote = consultationInput.trim() || "请结合上传的检查资料进行诊断建议。";
      const confirmedEvidence = buildConfirmedEvidenceText(evidenceConfirmed);
      const response = await submitConsultation(
        buildConsultationMessage(patient, consultationNote),
        requestSessionId,
        {
          ...patient,
          medicalEvidenceId: evidenceConfirmed ? medicalEvidence?.source?.id : undefined,
          medicalEvidence: confirmedEvidence,
          medicalEvidenceFileName: evidenceConfirmed ? (medicalEvidence?.fileName || medicalEvidenceFile?.name || "") : "",
          medicalEvidenceConfirmed: evidenceConfirmed
        }
      );
      patchDraft({ ...emptyDraft, requestSessionId: null });
      setSessions((current) => [response.session, ...current.filter((item) => item.id !== response.session.id)]);
      if (selectionEpoch.current !== epoch) return;
      setActiveSessionId(response.sessionId);
      activeSessionIdRef.current = response.sessionId;
      setDiagnosis(response.diagnosis);
      setPatient((current) =>
        current
          ? {
              ...current,
              loginStatus: "患者信息已填写",
              chiefComplaint: response.chiefComplaint
            }
          : current
      );
      let detail;
      try {
        detail = await fetchSessionDetail(response.sessionId);
      } catch (error) {
        if (selectionEpoch.current === epoch) setFeedback("本轮已提交，读取详情失败：" + error.message);
        return;
      }
      if (selectionEpoch.current !== epoch) return;
      setSessionDetail(detail);
      setFeedback("病情整理 Agent 已完成本轮信息收集。");
    } catch (error) {
      if (selectionEpoch.current !== epoch) return;
      if (createdSession) {
        activeSessionIdRef.current = null;
        setActiveSessionId(null);
      }
      patchDraft({ medicalEvidenceConfirmed: false });
      setFeedback(`病情整理失败: ${error.message}`);
    } finally {
      patchDraft({ consultationSubmitting: false });
    }
  }

  async function handleSimulate() {
    const epoch = selectionEpoch.current;
    setBusy(true);
    setFeedback("");

    try {
      const response = await simulatePipeline();
      if (selectionEpoch.current === epoch) setDemo(response);
    } catch (error) {
      if (selectionEpoch.current !== epoch) return;
      setFeedback(`流程模拟失败: ${error.message}`);
    } finally {
      if (selectionEpoch.current === epoch) setBusy(false);
    }
  }

  async function handleApprove() {
    await handleDoctorSubmit("");
  }

  async function handleDoctorSubmit(customOpinion = opinion) {
    if (!activeSessionId || !diagnosis || submitting || consultationSubmitting) return;
    const epoch = selectionEpoch.current;
    patchDraft({ submitting: true });
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
      if (selectionEpoch.current !== epoch) {
        await refreshCollaboration();
        return;
      }
      setFeedback(response.message);
      if (response.finalRecord) {
        setSessionDetail((current) =>
          current
            ? {
                ...current,
                finalRecord: response.finalRecord
              }
            : {
                finalRecord: response.finalRecord
              }
        );
        const [recordsData, collaborationData] = await Promise.all([
          fetchDiagnosisRecords().catch(() => diagnosisRecords),
          fetchDoctorCollaboration().catch(() => collaboration)
        ]);
        setDiagnosisRecords(recordsData);
        setCollaboration(collaborationData);
      }
      if (!customOpinion) {
        patchDraft({ opinion: "" });
      }
    } catch (error) {
      if (selectionEpoch.current === epoch) setFeedback(`提交失败: ${error.message}`);
    } finally {
      patchDraft({ submitting: false });
    }
  }

  async function reportPatientFor(recordId, detail) {
    if (recordId) {
      const profile = await fetchPatient(recordId);
      if (profile.id !== recordId || !profile.name?.trim()) throw new Error("无法核实报告关联的患者档案。");
      return patientFromApi(profile);
    }
    const historical = patientFromSessionDetail(detail);
    if (!historical.name) throw new Error("历史会话未记录可核实的患者姓名，无法导出报告。");
    return historical;
  }

  function downloadRevision(version) {
    const html = buildDiagnosisReportHtml({ finalRecord: version,
      sessionId: version.sessionId, doctorName: currentUser.username });
    downloadDiagnosisReport(html, version.patientName || "历史档案", version);
  }

  async function handleGenerateReport() {
    if (!activeSessionId) return;
    const epoch = selectionEpoch.current;
    try {
      const detail = await fetchSessionDetail(activeSessionId);
      if (selectionEpoch.current !== epoch || detail.sessionId !== activeSessionId) return;
      const record = isReviewedRecord(detail.finalRecord) ? detail.finalRecord : null;
      if (!detail.diagnosis && !record) {
        setFeedback("当前会话还没有可导出的诊断。");
        return;
      }
      const profileId = record?.patientRecordId || detail.patientRecordId;
      const reportPatient = await reportPatientFor(profileId, detail);
      if (selectionEpoch.current !== epoch) return;
      const html = buildDiagnosisReportHtml({ patient: reportPatient, diagnosis: detail.diagnosis,
        finalRecord: record, sessionId: detail.sessionId, doctorName: currentUser.username });
      downloadDiagnosisReport(html, reportPatient.name, record);
      setFeedback(reportFileLabel(record) + "已下载，可打开文件打印。");
    } catch (error) {
      if (selectionEpoch.current === epoch) setFeedback("读取报告失败：" + error.message);
    }
  }

  async function handleGeneratePrescription() {
    if (!activeSessionId) return;
    const epoch = selectionEpoch.current;
    try {
      const detail = await fetchSessionDetail(activeSessionId);
      if (selectionEpoch.current !== epoch || detail.sessionId !== activeSessionId) return;
      const record = detail.finalRecord;
      if (!isReviewedRecord(record) || !record.treatmentAdvice) {
        setFeedback("当前会话暂无已复核的用药建议。");
        return;
      }
      const reportPatient = await reportPatientFor(record.patientRecordId || detail.patientRecordId, detail);
      if (selectionEpoch.current !== epoch) return;
      const html = buildPrescriptionHtml({ patient: reportPatient, finalRecord: record, doctorName: currentUser.username });
      downloadPrescription(html, reportPatient.name);
      setFeedback("药单已下载，可打开文件打印。");
    } catch (error) {
      if (selectionEpoch.current === epoch) setFeedback("读取药单失败：" + error.message);
    }
  }

  function handleOpenImportModal() {
    setImportModalOpen(true);
    setImportFile(null);
    setImportResult(null);
    setImporting(false);
  }

  function handleCloseImportModal() {
    if (importing) return;
    setImportModalOpen(false);
    setImportFile(null);
    setImportResult(null);
  }

  function handleFileSelect(file) {
    if (!file) return;
    const allowed = [
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "image/jpeg",
      "image/png",
      "image/jpg"
    ];
    if (!allowed.includes(file.type)) {
      setFeedback("不支持的文件格式，请上传 PDF、DOCX 或 JPG/PNG 图片。");
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setFeedback(`文件过大：${formatFileSize(file.size)}。请上传 50MB 以内的文件，或先压缩图片/PDF。`);
      return;
    }
    setImportFile(file);
    setImportResult(null);
  }

  async function handleAnalyzeMedicalEvidence(file) {
    if (!file) return;
    const allowed = [
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "image/jpeg",
      "image/png",
      "image/jpg"
    ];
    if (!allowed.includes(file.type)) {
      setFeedback("不支持的文件格式，请上传 PDF、DOCX 或 JPG/PNG 图片。");
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      patchDraft({ medicalEvidenceError: `文件过大：${formatFileSize(file.size)}。请上传 50MB 以内的文件，或先压缩图片/PDF。` });
      setActiveTab("evidence");
      setFeedback(`检查资料过大：${formatFileSize(file.size)}，请上传 50MB 以内的文件。`);
      return;
    }

    const epoch = selectionEpoch.current;
    patchDraft({ medicalEvidenceFile: file, medicalEvidence: null, medicalEvidenceConfirmed: false,
      medicalEvidenceError: "", medicalEvidenceReviewNote: "", medicalEvidenceAnalyzing: true });
    setFeedback("");

    try {
      const result = await analyzeMedicalEvidence(file, { patientRecordId: patient?.id, sessionId: activeSessionId });
      patchDraft({ medicalEvidence: result });
      if (selectionEpoch.current !== epoch) return;
      setActiveTab("evidence");
      setFeedback("检查资料已识别，请医生确认后再提交给诊断 Agent。");
    } catch (error) {
      patchDraft({ medicalEvidenceFile: null });
      patchDraft({ medicalEvidenceError: error.message });
      if (selectionEpoch.current !== epoch) return;
      setActiveTab("evidence");
      setFeedback(`检查资料识别失败: ${error.message}`);
    } finally {
      patchDraft({ medicalEvidenceAnalyzing: false });
    }
  }

  function handleClearMedicalEvidence() {
    patchDraft({ medicalEvidence: null, medicalEvidenceFile: null, medicalEvidenceConfirmed: false,
      medicalEvidenceError: "", medicalEvidenceReviewNote: "" });
  }

  async function handleConfirmMedicalEvidence() {
    if (!medicalEvidence) return;
    if ((patient?.id || sessionDetail?.sessionId) && (consultationInput.trim() || medicalEvidence.evidenceText)) {
      await handleSubmitConsultation({
        evidenceConfirmed: true,
        allowMedicalEvidenceSubmission: true
      });
      return;
    }
    setFeedback("请先选定患者并填写问诊信息，再确认检查资料。");
    setActiveTab("workspace");
  }

  function buildConfirmedEvidenceText(evidenceConfirmed = medicalEvidenceConfirmed) {
    if (!evidenceConfirmed || !medicalEvidence?.evidenceText) {
      return "";
    }
    const note = medicalEvidenceReviewNote.trim();
    return note
      ? `${medicalEvidence.evidenceText}\n医生确认备注：${note}`
      : medicalEvidence.evidenceText;
  }

  async function handleImportCase() {
    if (!importFile) return;
    setImporting(true);
    setFeedback("");

    try {
      const result = await importCase(importFile);
      setImportResult(result);

      const [patientData, recordsData] = await Promise.all([
        fetchPatients(),
        fetchDiagnosisRecords().catch(() => diagnosisRecords)
      ]);
      const normalizedPatients = patientData.map(patientFromApi);
      setPatients(normalizedPatients);

      setDiagnosisRecords(recordsData);
      setFeedback("病例导入成功，患者信息已同步。");
    } catch (error) {
      setFeedback(`病例导入失败: ${error.message}`);
    } finally {
      setImporting(false);
    }
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    handleFileSelect(file);
  }

  function handleDragOver(e) {
    e.preventDefault();
    setDragOver(true);
  }

  function handleDragLeave() {
    setDragOver(false);
  }

  async function refreshCollaboration() {
    const [collaborationData, recordsData] = await Promise.all([
      fetchDoctorCollaboration(),
      fetchDiagnosisRecords()
    ]);
    setCollaboration(collaborationData);
    setDiagnosisRecords(recordsData);
  }

  async function handleApprovePatientBinding(relationId) {
    setCollaborationBusyAction(`binding-${relationId}`);
    setFeedback("");
    try {
      await approvePatientBinding(relationId);
      const [patientData] = await Promise.all([fetchPatients(), refreshCollaboration()]);
      setPatients(patientData.map(patientFromApi));
      setFeedback("医患绑定已确认，患者现在可以发起问诊。");
    } catch (error) {
      setFeedback(`确认绑定失败: ${error.message}`);
    } finally {
      setCollaborationBusyAction("");
    }
  }

  async function handleConfirmPatientEvidence(consultationId) {
    setCollaborationBusyAction(`evidence-${consultationId}`);
    setFeedback("");
    try {
      await confirmPatientEvidence(consultationId);
      const [sessionData] = await Promise.all([fetchSessions(), refreshCollaboration()]);
      setSessions(sessionData);
      setFeedback("患者检查资料已确认，并已进入诊断 Agent。");
    } catch (error) {
      setFeedback(`确认检查资料失败: ${error.message}`);
    } finally {
      setCollaborationBusyAction("");
    }
  }

  async function handlePublishDiagnosisRecord(recordId, plan) {
    setCollaborationBusyAction(`publish-${recordId}`);
    setFeedback("");
    try {
      await publishDiagnosisRecord(recordId, plan);
      await refreshCollaboration();
      setFeedback("诊断报告已发布给患者。");
    } catch (error) {
      setFeedback(`发布报告失败: ${error.message}`);
    } finally {
      setCollaborationBusyAction("");
    }
  }

  return (
    <div className="app-shell google-studio-layout">
      <div className="background-orb orb-one" />
      <div className="background-orb orb-two" />

      <NavigationSidebar 
        activeTab={activeTab} 
        onTabChange={setActiveTab} 
        currentUser={currentUser} 
        onLogout={handleLogout} 
      />

      <main className="main-content-area">
        {activeTab === "workspace" && (
          <div className="workspace-tab">
            <Sidebar key={"sidebar:" + owner}
              patient={patient}
              patients={patients}
              onSelectPatient={handleSelectPatient}
              onSavePatient={handleSavePatient}
              onDeletePatient={handleDeletePatient}
              patientSaving={patientSaving}
              sessions={sessions}
              activeSessionId={activeSessionId}
              finalRecord={isReviewedRecord(sessionDetail?.finalRecord) ? sessionDetail.finalRecord : null}
              consultationInput={consultationInput}
              onConsultationInputChange={(value) => patchDraft({ consultationInput: value })}
              onSubmitConsultation={handleSubmitConsultation}
              onSelectSession={handleSelectSession}
              onDeleteSession={handleDeleteSession}
              consultationSubmitting={consultationSubmitting || submitting}
              canSubmitConsultation={canSubmitConsultation}
              onGeneratePrescription={handleGeneratePrescription}
              medicalEvidence={medicalEvidence}
              medicalEvidenceFile={medicalEvidenceFile}
              medicalEvidenceAnalyzing={medicalEvidenceAnalyzing}
              medicalEvidenceConfirmed={medicalEvidenceConfirmed}
              onAnalyzeMedicalEvidence={handleAnalyzeMedicalEvidence}
              onClearMedicalEvidence={handleClearMedicalEvidence}
              onOpenEvidencePanel={() => setActiveTab("evidence")}
            />
            <DiagnosticPanel key={"diagnosis:" + owner}
              diagnosis={diagnosis}
              pipelineEvents={pipelineEvents}
              sessionDetail={sessionDetail}
              demo={demo}
              onCloseDemo={() => setDemo(null)}
              onSimulate={handleSimulate}
              onAppendPatientEvidence={handleAppendPatientEvidence}
              busy={busy}
            />
            <DoctorPanel
              activeSessionId={activeSessionId}
              diagnosis={diagnosis}
              patient={patient}
              opinion={opinion}
              onOpinionChange={(value) => patchDraft({ opinion: value })}
              onApprove={handleApprove}
              onSubmit={() => handleDoctorSubmit(opinion)}
              onGenerateReport={handleGenerateReport}
              feedback={feedback}
              submitting={submitting || consultationSubmitting || ["待补充信息", "NEEDS_PATIENT_REPLY"].includes(sessionDetail?.status)}
              finalRecord={isReviewedRecord(sessionDetail?.finalRecord) ? sessionDetail.finalRecord : null}
              reportDisabled={!activeSessionId || !(diagnosis || sessionDetail?.finalRecord)}
            />
          </div>
        )}

        {activeTab === "evidence" && (
          <EvidenceReviewPanel
            evidence={medicalEvidence}
            evidenceFile={medicalEvidenceFile}
            evidenceConfirmed={medicalEvidenceConfirmed}
            evidenceError={medicalEvidenceError}
            evidenceReviewNote={medicalEvidenceReviewNote}
            submitting={consultationSubmitting}
            feedback={feedback}
            onEvidenceTextChange={(text) => patchDraft({ medicalEvidence: { ...medicalEvidence, evidenceText: text }, medicalEvidenceConfirmed: false })}
            onEvidenceReviewNoteChange={(value) => patchDraft({ medicalEvidenceReviewNote: value })}
            onConfirmEvidence={handleConfirmMedicalEvidence}
            onClearEvidence={handleClearMedicalEvidence}
            onBackToWorkspace={() => setActiveTab("workspace")}
          />
        )}
        
        {activeTab === "review" && (
          <div className="review-tab glass-card section">
            <div className="section-title">
              <div>
                <p className="eyebrow">Review Details</p>
                <h2>多模型评审细节</h2>
              </div>
            </div>
            <ReviewerResults reviewers={diagnosis?.reviewers} />
            {activeSessionId && <SessionEvidence sessionId={activeSessionId} />}

            <div className="pipeline-events-section">
              <h3>诊断工作流动态</h3>
              <div className="pipeline-log">
                {pipelineEvents.map((ev, i) => (
                  <div key={i} className="pipeline-log-item">
                    <span className="log-time">{new Date(ev.timestamp).toLocaleTimeString()}</span>
                    <span className="log-stage">[{ev.stage}]</span>
                    <span className="log-msg">{ev.message}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {activeTab === "patients" && (
          <div className="patients-tab glass-card section full-height">
             <div className="section-title">
              <div>
                <p className="eyebrow">Patient Management</p>
                <h2>病例与会诊管理</h2>
              </div>
              <button className="primary-button import-btn" type="button" onClick={handleOpenImportModal}>
                <Upload size={16} />
                病例导入
              </button>
            </div>
            <div className="patient-management-grid">
               <div className="pm-patients">
                 <h3>患者列表</h3>
                 <div className="patient-list">
                    {patients.length ? (
                      patients.map((item) => (
                        <article className="patient-list-item" key={item.id}>
                          <button className="patient-name-button" type="button" onClick={() => { handleSelectPatient(item); setActiveTab("workspace"); }}>
                            {item.name} ({item.gender || "未知"}, {item.age ? item.age + "岁" : "年龄未知"})
                          </button>
                        </article>
                      ))
                    ) : (
                      <p>暂无患者</p>
                    )}
                 </div>
               </div>
               <div className="pm-sessions">
                 <h3>历史诊断报告</h3>
                 <div className="session-list">
                    {diagnosisRecords.length ? (
                      diagnosisRecords.map((record) => (
                        <article className="diagnosis-record-item" key={record.id}>
                          <div className="record-info">
                            <h4>{record.chiefComplaint || "未填写主诉"}</h4>
                            <p className="record-conclusion">{record.finalConclusion || record.aiConclusion || "暂无诊断结论"}</p>
                            <div className="record-meta">
                              <span className={`risk-badge risk-${(record.riskLevel || "").replace("风险", "")}`}>{record.riskLevel || "未知"}</span>
                              <span className="record-time">{record.updatedAt || ""}</span>
                            </div>
                          </div>
                          <div className="record-status">
                            <span className={`status-badge status-${(record.reviewStatus || "").toLowerCase()}`}>
                              {record.reviewStatus === "IMPORTED" ? "导入" : record.reviewStatus || "未知"}
                            </span>
                            <ReportVersions record={record} onDownload={downloadRevision} />
                            {record.reviewStatus !== "IMPORTED" && <button className="secondary-button" type="button" onClick={() => {
                              handleSelectSession(record.sessionId);
                              setActiveTab("workspace");
                            }}>打开复核</button>}
                            <button
                              className="ghost-icon danger"
                              type="button"
                              disabled={record.publishedToPatient}
                              title="删除报告"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDeleteDiagnosisRecord(record.id);
                              }}
                            >
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </article>
                      ))
                    ) : (
                      <p>暂无诊断报告</p>
                    )}
                 </div>
               </div>
            </div>

            {importModalOpen && (
              <div className="import-modal-overlay" onClick={handleCloseImportModal}>
                <div className="import-modal" onClick={(e) => e.stopPropagation()}>
                  <div className="import-modal-header">
                    <h3>病例导入</h3>
                    <button className="icon-btn" type="button" onClick={handleCloseImportModal} disabled={importing}>
                      <X size={18} />
                    </button>
                  </div>

                  {!importResult ? (
                    <div className="import-modal-body">
                      <div
                        className={`file-dropzone ${dragOver ? "drag-over" : ""} ${importFile ? "has-file" : ""}`}
                        onDrop={handleDrop}
                        onDragOver={handleDragOver}
                        onDragLeave={handleDragLeave}
                        onClick={() => document.getElementById("import-file-input").click()}
                      >
                        <input
                          id="import-file-input"
                          type="file"
                          accept=".pdf,.docx,.jpg,.jpeg,.png"
                          style={{ display: "none" }}
                          onChange={(e) => handleFileSelect(e.target.files[0])}
                        />
                        {importFile ? (
                          <>
                            <FileText size={36} />
                            <p className="dropzone-filename">{importFile.name}</p>
                            <p className="dropzone-hint">{(importFile.size / 1024).toFixed(1)} KB</p>
                          </>
                        ) : (
                          <>
                            <Upload size={36} />
                            <p className="dropzone-title">拖拽文件到此处或点击选择</p>
                            <p className="dropzone-hint">支持 PDF、DOCX、JPG、PNG 格式</p>
                          </>
                        )}
                      </div>

                      <button
                        className="primary-button import-submit-btn"
                        type="button"
                        disabled={!importFile || importing}
                        onClick={handleImportCase}
                      >
                        {importing ? (
                          <>
                            <Loader2 size={16} className="spin" />
                            AI 正在解析文档...
                          </>
                        ) : (
                          "开始导入"
                        )}
                      </button>
                    </div>
                  ) : (
                    <div className="import-modal-body import-result">
                      <div className="import-result-header">
                        <Check size={24} />
                        <h4>导入成功</h4>
                      </div>
                      <div className="import-result-card">
                        <h4>患者信息</h4>
                        <div className="result-field"><span>姓名：</span><strong>{importResult.patient?.name}</strong></div>
                        <div className="result-field"><span>性别：</span><strong>{importResult.patient?.gender}</strong></div>
                        <div className="result-field"><span>年龄：</span><strong>{importResult.patient?.age ? importResult.patient.age + "岁" : "未知"}</strong></div>
                        <div className="result-field"><span>主诉：</span><strong>{importResult.patient?.chiefComplaint || "无"}</strong></div>
                      </div>
                      <div className="import-result-card">
                        <h4>诊断报告</h4>
                        <div className="result-field"><span>诊断结论：</span><strong>{importResult.diagnosisRecord?.aiConclusion || "无"}</strong></div>
                        <div className="result-field"><span>风险等级：</span><strong>{importResult.diagnosisRecord?.riskLevel || "未知"}</strong></div>
                        <div className="result-field"><span>治疗建议：</span><strong>{importResult.diagnosisRecord?.treatmentAdvice || "无"}</strong></div>
                      </div>
                      {importResult.extractedSummary && (
                        <div className="import-result-card">
                          <h4>文档摘要</h4>
                          <p>{importResult.extractedSummary}</p>
                        </div>
                      )}
                      <button className="primary-button" type="button" onClick={handleCloseImportModal}>
                        完成
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === "collaboration" && (
          <DoctorCollaborationPanel
            collaboration={collaboration}
            onRefresh={refreshCollaboration}
            diagnosisRecords={diagnosisRecords}
            busyAction={collaborationBusyAction}
            feedback={feedback}
            onApproveBinding={handleApprovePatientBinding}
            onConfirmEvidence={handleConfirmPatientEvidence}
            onOpenSession={(sessionId) => {
              handleSelectSession(sessionId);
              setActiveTab("workspace");
            }}
            onPublishReport={handlePublishDiagnosisRecord}
          />
        )}

        {activeTab === "doctor" && (
          <DoctorProfileTab
            currentUser={currentUser}
            sessions={sessions}
            patients={patients}
            pipelineEvents={pipelineEvents}
          />
        )}

        {activeTab === "settings" && (
          <SettingsTab 
            careMode={careMode}
            onCareModeChange={setCareMode}
            reminders={reminders}
            onAddReminder={(reminder) => setReminders(curr => [...curr, reminder].sort((a,b) => a.dueAt - b.dueAt))}
            onRemoveReminder={(id) => setReminders(curr => curr.filter(r => r.id !== id))}
          />
        )}
      </main>

      {activeReminder && (
        <section className="reminder-alert" role="alert">
          <span className="reminder-alert-icon">
            <Bell size={20} />
          </span>
          <div>
            <p className="eyebrow">Doctor Reminder</p>
            <h2>{activeReminder.text}</h2>
            <p>这是你设定的定时提醒。</p>
          </div>
          <button className="primary-button" type="button" onClick={() => setActiveReminder(null)}>
            <Check size={16} />
            知道了
          </button>
        </section>
      )}
    </div>
  );
}
