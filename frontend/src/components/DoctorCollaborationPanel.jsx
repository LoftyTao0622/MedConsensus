import { BadgeCheck, CheckCircle2, ClipboardCopy, FileCheck2, Link2, Users } from "lucide-react";
import { useState } from "react";
import { useFollowUps } from "../api/followUp";
import { queueFilters, selectQueue, waitingTime } from "../doctorQueue";
import { EvidenceCorrection, SessionEvidence } from "./EvidenceSource";
import { PublishReportForm, ReportFollowUp } from "./ReportFollowUp";
import "./followUp.css";

const consultationStatusCopy = {
  NEEDS_PATIENT_REPLY: "等待患者补充",
  WAITING_DOCTOR: "等待医生处理",
  WAITING_REPORT: "等待医生复核",
  WAITING_DOCTOR_EVIDENCE_REVIEW: "检查资料待确认",
  REPORT_READY: "报告待发布",
  REPORT_PUBLISHED: "报告已发布"
};

function PendingEvidence({consultation, onConfirm, busy}) {
  const [dirty, setDirty] = useState(false);
  return <div className="pending-evidence-block">
    <div>
      <span>患者上传：{consultation.evidenceFileName}</span>
      <EvidenceCorrection sourceId={consultation.evidenceSourceId} initialText={consultation.evidenceText} onDirtyChange={setDirty} />
    </div>
    <button className="primary-button" type="button" disabled={busy || dirty} onClick={() => onConfirm(consultation.id)}>
      {busy ? "确认中" : "确认资料并继续诊断"}
    </button>
  </div>;
}

export function DoctorCollaborationPanel({
  collaboration,
  onRefresh,
  diagnosisRecords,
  busyAction,
  feedback,
  onApproveBinding,
  onConfirmEvidence,
  onOpenSession,
  onPublishReport
}) {
  const pendingRelations = collaboration?.relations?.filter((item) => item.status === "PENDING") || [];
  const consultations = collaboration?.consultations || [];
  const [filter, setFilter] = useState("active");
  const [refreshing, setRefreshing] = useState(false);
  const [refreshError, setRefreshError] = useState("");
  const followUps = useFollowUps("workspace", collaboration);
  const queue = selectQueue(consultations, followUps.tasks, filter);

  async function refresh() {
    setRefreshing(true);
    setRefreshError("");
    try { await onRefresh(); }
    catch (error) { setRefreshError(error.message); }
    finally { setRefreshing(false); }
  }

  function copyInviteCode() {
    navigator.clipboard?.writeText(collaboration?.inviteCode || "");
  }

  return (
    <section className="collaboration-page glass-card section">
      <div className="section-title">
        <div>
          <p className="eyebrow">Doctor Patient Collaboration</p>
          <h2>医患协作</h2>
        </div>
      </div>

      {feedback ? <div className="feedback-box">{feedback}</div> : null}

      <div className="doctor-invite-block">
        <div>
          <span className="section-icon"><Link2 size={20} /></span>
          <div>
            <h3>医生邀请码</h3>
            <p>患者提交该邀请码后，你需要在下方确认绑定申请。</p>
          </div>
        </div>
        <button className="invite-code-button" type="button" onClick={copyInviteCode}>
          <strong>{collaboration?.inviteCode || "正在生成"}</strong>
          <ClipboardCopy size={17} />
        </button>
      </div>

      <div className="collaboration-columns">
        <section>
          <div className="collaboration-heading">
            <Users size={18} />
            <h3>待确认绑定</h3>
            <span>{pendingRelations.length}</span>
          </div>
          <div className="collaboration-list">
            {pendingRelations.length ? pendingRelations.map((relation) => (
              <article className="collaboration-row" key={relation.id}>
                <div>
                  <strong>{relation.patientName}</strong>
                  <p>{relation.patientPhone || "未填写手机号"}</p>
                </div>
                <button
                  className="primary-button"
                  type="button"
                  onClick={() => onApproveBinding(relation.id)}
                  disabled={busyAction === `binding-${relation.id}`}
                >
                  <CheckCircle2 size={16} />
                  {busyAction === `binding-${relation.id}` ? "确认中" : "确认绑定"}
                </button>
              </article>
            )) : <p className="treatment-empty">暂无待确认绑定申请。</p>}
          </div>
        </section>

        <section>
          <div className="collaboration-heading">
            <FileCheck2 size={18} />
            <h3>问诊待办</h3>
            <span>{queue.length}</span>
            <button type="button" className="secondary-button" disabled={refreshing} onClick={refresh}>
              {refreshing ? "刷新中" : "刷新待办"}
            </button>
          </div>
          {refreshError && <p role="alert">{refreshError}</p>}
          <label className="queue-filter">查看
            <select value={filter} onChange={event => setFilter(event.target.value)}>
              {Object.entries(queueFilters).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
            </select>
          </label>
          {followUps.error && <p role="alert">{followUps.error} <button type="button" onClick={followUps.refresh}>重试</button></p>}
          <div className="collaboration-list">
            {queue.length ? queue.map(({item: consultation, since}) => {
              const record = consultation.reportId ? {id: consultation.reportId, publishedToPatient: consultation.reportPublished}
                : diagnosisRecords.find((item) => item.sessionId === consultation.sessionId);
              return (
                <article className="collaboration-task" key={consultation.id}>
                  <div className="collaboration-task-header">
                    <div>
                      <strong>{consultation.patientName}</strong>
                      {since != null && <small> · 已等待 {waitingTime(since)}</small>}
                      <p>
                        {consultation.patientPhone || "未填写手机号"} · {consultationStatusCopy[consultation.status] || "处理中"}
                      </p>
                    </div>
                    <button className="secondary-button" type="button" onClick={() => onOpenSession(consultation.sessionId)}>
                      打开诊断会话
                    </button>
                  </div>

                  {consultation.evidenceFileName && <SessionEvidence sessionId={consultation.sessionId} />}
                  {consultation.evidenceStatus === "PENDING_DOCTOR" &&
                    <PendingEvidence key={consultation.evidenceSourceId || consultation.id} consultation={consultation}
                      onConfirm={onConfirmEvidence} busy={busyAction === `evidence-${consultation.id}`} />}

                  {record ? (
                    <div className="publish-report-row">
                      <div>
                        <BadgeCheck size={17} />
                        <span>{record.publishedToPatient ? "报告已发布给患者" : "医生复核已完成，报告尚未发布"}</span>
                      </div>
                      {!record.publishedToPatient ? (
                        <PublishReportForm onPublish={plan => onPublishReport(record.id, plan)} busy={busyAction === `publish-${record.id}`} />
                      ) : null}
                    </div>
                  ) : null}
                  {followUps.tasks.filter(task => task.recordId === record?.id).map(task =>
                    <ReportFollowUp key={task.id} task={task} onChange={followUps.refresh} />)}
                </article>
              );
            }) : <p className="treatment-empty">当前没有{queueFilters[filter]}记录。</p>}
          </div>
        </section>
      </div>
    </section>
  );
}
