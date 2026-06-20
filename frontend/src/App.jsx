import { useEffect, useRef, useState } from "react";
import { Check, Bell, Upload, X, FileText, Image, Loader2, Trash2 } from "lucide-react";
import { NavigationSidebar } from "./components/NavigationSidebar";
import { SettingsTab } from "./components/SettingsTab";
import { Sidebar } from "./components/Sidebar";
import { DiagnosticPanel } from "./components/DiagnosticPanel";
import { DoctorPanel } from "./components/DoctorPanel";
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
  fetchDiagnosis,
  fetchPatients,
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

function readStoredReminders() {
  try {
    const parsed = JSON.parse(localStorage.getItem(REMINDER_STORAGE_KEY) || "[]");
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

function formatFileSize(bytes) {
  if (!Number.isFinite(bytes)) {
    return "未知大小";
  }
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)} MB`
    : `${(bytes / 1024).toFixed(1)} KB`;
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
  const [patient, setPatient] = useState(null);
  const [patients, setPatients] = useState([]);
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
  const activeSessionIdRef = useRef(null);
  const [sessionDetail, setSessionDetail] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);
  const [authChecked, setAuthChecked] = useState(false);
  const [patientSaving, setPatientSaving] = useState(false);
  const [activeTab, setActiveTab] = useState("workspace");
  const [reminders, setReminders] = useState(() => readStoredReminders());
  const [activeReminder, setActiveReminder] = useState(null);
  const [careMode, setCareMode] = useState(() => readStoredCareMode());
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [diagnosisRecords, setDiagnosisRecords] = useState([]);
  const [dragOver, setDragOver] = useState(false);
  const [medicalEvidence, setMedicalEvidence] = useState(null);
  const [medicalEvidenceFile, setMedicalEvidenceFile] = useState(null);
  const [medicalEvidenceAnalyzing, setMedicalEvidenceAnalyzing] = useState(false);
  const [medicalEvidenceConfirmed, setMedicalEvidenceConfirmed] = useState(false);
  const [medicalEvidenceError, setMedicalEvidenceError] = useState("");
  const [medicalEvidenceReviewNote, setMedicalEvidenceReviewNote] = useState("");
  const [collaboration, setCollaboration] = useState(null);
  const [collaborationBusyAction, setCollaborationBusyAction] = useState("");
  const hasUploadedMedicalEvidence = Boolean(medicalEvidenceFile || medicalEvidence);
  const hasConfirmedMedicalEvidence = Boolean(medicalEvidence?.evidenceText && medicalEvidenceConfirmed);
  const canSubmitConsultation = Boolean(
    patient?.id
      && patient?.name?.trim()
      && (consultationInput.trim() || hasUploadedMedicalEvidence || hasConfirmedMedicalEvidence)
  );

  useEffect(() => {
    document.documentElement.classList.toggle("care-mode", careMode);
    localStorage.setItem(CARE_MODE_STORAGE_KEY, careMode ? "enabled" : "disabled");
  }, [careMode]);

  useEffect(() => {
    localStorage.setItem(REMINDER_STORAGE_KEY, JSON.stringify(reminders));
  }, [reminders]);

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
    if (!currentUser || currentUser.role !== "DOCTOR") {
      return;
    }

    async function bootstrap() {
      const [patientData, sessionData, diagnosisData, recordsData, collaborationData] = await Promise.all([
        fetchPatients(),
        fetchSessions(),
        fetchDiagnosis(),
        fetchDiagnosisRecords().catch(() => []),
        fetchDoctorCollaboration().catch(() => null)
      ]);

      const normalizedPatients = patientData.map(patientFromApi);
      setPatients(normalizedPatients);
      setPatient(normalizedPatients[0] || emptyPatient);
      setSessions(sessionData);
      setDiagnosis(diagnosisData);
      setDiagnosisRecords(recordsData);
      setCollaboration(collaborationData);
    }

    bootstrap().catch((error) => {
      setFeedback(`初始化失败: ${error.message}`);
    });
  }, [currentUser]);

  useEffect(() => {
    activeSessionIdRef.current = activeSessionId;
  }, [activeSessionId]);

  useEffect(() => {
    if (!currentUser || currentUser.role !== "DOCTOR") {
      return undefined;
    }

    const disconnect = connectPipelineSocket((event) => {
      if (event.sessionId && event.sessionId !== activeSessionIdRef.current) {
        return;
      }
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
      setPatients([]);
      setSessions([]);
      setDiagnosis(null);
      setFeedback("");
      setOpinion("");
      setConsultationInput("");
      setActiveSessionId(null);
      setSessionDetail(null);
      setCollaboration(null);
      setPipelineEvents(initialEvents);
      setAuthChecked(true);
    }
  }

  function handleSelectPatient(nextPatient) {
    setPatient(nextPatient);
    setActiveSessionId(null);
    setSessionDetail(null);
    setDiagnosis(null);
    setFeedback("");
  }

  async function handleSavePatient(nextPatient) {
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
      setPatient(normalizedPatient);
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
    setFeedback("");

    try {
      const response = await deletePatient(patientId);
      setPatients((current) => {
        const next = current.filter((item) => item.id !== patientId);
        if (patient?.id === patientId) {
          setPatient(next[0] || emptyPatient);
          setActiveSessionId(null);
          setSessionDetail(null);
          setDiagnosis(null);
        }
        return next;
      });
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

  async function handleDeleteDiagnosisRecord(recordId) {
    setFeedback("");

    try {
      const response = await deleteDiagnosisRecord(recordId);
      setDiagnosisRecords((current) => current.filter((r) => r.id !== recordId));
      setFeedback(response.message);
    } catch (error) {
      setFeedback(`删除诊断报告失败: ${error.message}`);
    }
  }

  async function handleSelectSession(sessionId) {
    activeSessionIdRef.current = sessionId;
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

  function handleAppendPatientEvidence(evidence) {
    const normalized = evidence.trim();
    if (!normalized) {
      return;
    }
    setConsultationInput((current) => {
      const prefix = current.trim() ? `${current.trim()}\n\n` : "";
      return `${prefix}补充诊断依据：${normalized}`;
    });
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

    setConsultationSubmitting(true);
    setFeedback("");
    const requestSessionId = activeSessionId || createSessionId();
    const createdSession = !activeSessionId;
    if (createdSession) {
      activeSessionIdRef.current = requestSessionId;
      setActiveSessionId(requestSessionId);
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
          medicalEvidence: confirmedEvidence,
          medicalEvidenceFileName: evidenceConfirmed ? (medicalEvidence?.fileName || medicalEvidenceFile?.name || "") : "",
          medicalEvidenceConfirmed: evidenceConfirmed
        }
      );
      setActiveSessionId(response.sessionId);
      activeSessionIdRef.current = response.sessionId;
      setConsultationInput("");
      setMedicalEvidence(null);
      setMedicalEvidenceFile(null);
      setMedicalEvidenceConfirmed(false);
      setMedicalEvidenceError("");
      setMedicalEvidenceReviewNote("");
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
      if (createdSession) {
        activeSessionIdRef.current = null;
        setActiveSessionId(null);
      }
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

  function handleGeneratePrescription() {
    if (!patient?.name) {
      setFeedback("请先选择患者后再生成药单。");
      return;
    }
    if (!sessionDetail?.finalRecord?.treatmentAdvice) {
      setFeedback("暂无开药建议，无法生成药单。请先完成诊断流程。");
      return;
    }

    const prescriptionHtml = buildPrescriptionHtml({
      patient,
      finalRecord: sessionDetail.finalRecord,
      doctorName: currentUser?.username
    });
    downloadPrescription(prescriptionHtml, patient.name);
    setFeedback("药单已生成，可打开下载的 HTML 文件进行打印。");
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
      setMedicalEvidenceError(`文件过大：${formatFileSize(file.size)}。请上传 50MB 以内的文件，或先压缩图片/PDF。`);
      setActiveTab("evidence");
      setFeedback(`检查资料过大：${formatFileSize(file.size)}，请上传 50MB 以内的文件。`);
      return;
    }

    setMedicalEvidenceFile(file);
    setMedicalEvidence(null);
    setMedicalEvidenceConfirmed(false);
    setMedicalEvidenceError("");
    setMedicalEvidenceReviewNote("");
    setMedicalEvidenceAnalyzing(true);
    setFeedback("");

    try {
      const result = await analyzeMedicalEvidence(file);
      setMedicalEvidence(result);
      setActiveTab("evidence");
      setFeedback("检查资料已识别，请医生确认后再提交给诊断 Agent。");
    } catch (error) {
      setMedicalEvidenceFile(null);
      setMedicalEvidenceError(error.message);
      setActiveTab("evidence");
      setFeedback(`检查资料识别失败: ${error.message}`);
    } finally {
      setMedicalEvidenceAnalyzing(false);
    }
  }

  function handleClearMedicalEvidence() {
    setMedicalEvidence(null);
    setMedicalEvidenceFile(null);
    setMedicalEvidenceConfirmed(false);
    setMedicalEvidenceError("");
    setMedicalEvidenceReviewNote("");
  }

  async function handleConfirmMedicalEvidence() {
    if (!medicalEvidence) return;
    setMedicalEvidenceConfirmed(true);
    if (patient?.id && (consultationInput.trim() || medicalEvidence.evidenceText)) {
      await handleSubmitConsultation({
        evidenceConfirmed: true,
        allowMedicalEvidenceSubmission: true
      });
      return;
    }
    setFeedback("检查资料已由医生确认，请补充患者文字信息后提交给诊断 Agent。");
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
      setPatient(normalizedPatients[0] || emptyPatient);
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

  async function handlePublishDiagnosisRecord(recordId) {
    setCollaborationBusyAction(`publish-${recordId}`);
    setFeedback("");
    try {
      await publishDiagnosisRecord(recordId);
      await refreshCollaboration();
      setFeedback("诊断报告已发布给患者。");
    } catch (error) {
      setFeedback(`发布报告失败: ${error.message}`);
    } finally {
      setCollaborationBusyAction("");
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

  if (currentUser.role === "PATIENT") {
    return <PatientPortal currentUser={currentUser} onLogout={handleLogout} />;
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
            <Sidebar
              patient={patient}
              patients={patients}
              onPatientChange={setPatient}
              onSelectPatient={handleSelectPatient}
              onSavePatient={handleSavePatient}
              onDeletePatient={handleDeletePatient}
              patientSaving={patientSaving}
              sessions={sessions}
              activeSessionId={activeSessionId}
              finalRecord={sessionDetail?.finalRecord}
              consultationInput={consultationInput}
              onConsultationInputChange={setConsultationInput}
              onSubmitConsultation={handleSubmitConsultation}
              onSelectSession={handleSelectSession}
              onDeleteSession={handleDeleteSession}
              consultationSubmitting={consultationSubmitting}
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
            <DiagnosticPanel
              diagnosis={diagnosis}
              pipelineEvents={pipelineEvents}
              sessionDetail={sessionDetail}
              onSimulate={handleSimulate}
              onAppendPatientEvidence={handleAppendPatientEvidence}
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
            onEvidenceReviewNoteChange={setMedicalEvidenceReviewNote}
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
            {diagnosis?.reviewers && diagnosis.reviewers.length > 0 ? (
              <div className="review-details-grid">
                {diagnosis.reviewers.map((rev, index) => (
                  <div key={index} className="reviewer-card">
                    <h3>{rev.name}</h3>
                    <div className="reviewer-metric">
                      <span className="reviewer-metric-label">权重</span>
                      <div className="reviewer-metric-bar">
                        <div className="reviewer-metric-fill weight" style={{ width: `${Math.round(rev.weight * 100)}%` }} />
                      </div>
                      <span className="reviewer-metric-value">{Math.round(rev.weight * 100)}%</span>
                    </div>
                    <div className="reviewer-metric">
                      <span className="reviewer-metric-label">认可度</span>
                      <div className="reviewer-metric-bar">
                        <div className="reviewer-metric-fill score" style={{ width: `${Math.round(rev.score * 100)}%` }} />
                      </div>
                      <span className="reviewer-metric-value">{Math.round(rev.score * 100)}%</span>
                    </div>
                    <div className="reviewer-comment">
                      <strong>评审观点</strong>
                      {rev.comment}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="treatment-empty">暂无评审细节，请先在工作台完成 AI 诊断。</p>
            )}
            
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
                            <p className="record-conclusion">{record.aiConclusion || "暂无诊断结论"}</p>
                            <div className="record-meta">
                              <span className={`risk-badge risk-${(record.riskLevel || "").replace("风险", "")}`}>{record.riskLevel || "未知"}</span>
                              <span className="record-time">{record.updatedAt || ""}</span>
                            </div>
                          </div>
                          <div className="record-status">
                            <span className={`status-badge status-${(record.reviewStatus || "").toLowerCase()}`}>
                              {record.reviewStatus === "IMPORTED" ? "导入" : record.reviewStatus || "未知"}
                            </span>
                            <button
                              className="ghost-icon danger"
                              type="button"
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
