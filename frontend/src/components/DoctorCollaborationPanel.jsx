import { BadgeCheck, CheckCircle2, ClipboardCopy, FileCheck2, Link2, Users } from "lucide-react";

const consultationStatusCopy = {
  NEEDS_PATIENT_REPLY: "等待患者补充",
  WAITING_DOCTOR: "等待医生处理",
  WAITING_REPORT: "等待医生复核",
  WAITING_DOCTOR_EVIDENCE_REVIEW: "检查资料待确认",
  REPORT_READY: "报告待发布",
  REPORT_PUBLISHED: "报告已发布"
};

export function DoctorCollaborationPanel({
  collaboration,
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
            <h3>患者问诊任务</h3>
            <span>{consultations.length}</span>
          </div>
          <div className="collaboration-list">
            {consultations.length ? consultations.map((consultation) => {
              const record = diagnosisRecords.find((item) => item.sessionId === consultation.sessionId);
              return (
                <article className="collaboration-task" key={consultation.id}>
                  <div className="collaboration-task-header">
                    <div>
                      <strong>{consultation.patientName}</strong>
                      <p>
                        {consultation.patientPhone || "未填写手机号"} · {consultationStatusCopy[consultation.status] || "处理中"}
                      </p>
                    </div>
                    <button className="secondary-button" type="button" onClick={() => onOpenSession(consultation.sessionId)}>
                      打开诊断会话
                    </button>
                  </div>

                  {consultation.evidenceStatus === "PENDING_DOCTOR" ? (
                    <div className="pending-evidence-block">
                      <div>
                        <span>患者上传：{consultation.evidenceFileName}</span>
                        <p>{consultation.evidenceText || "资料已完成识别，请确认后进入诊断 Agent。"}</p>
                      </div>
                      <button
                        className="primary-button"
                        type="button"
                        onClick={() => onConfirmEvidence(consultation.id)}
                        disabled={busyAction === `evidence-${consultation.id}`}
                      >
                        {busyAction === `evidence-${consultation.id}` ? "确认中" : "确认资料并继续诊断"}
                      </button>
                    </div>
                  ) : null}

                  {record ? (
                    <div className="publish-report-row">
                      <div>
                        <BadgeCheck size={17} />
                        <span>{record.publishedToPatient ? "报告已发布给患者" : "医生复核已完成，报告尚未发布"}</span>
                      </div>
                      {!record.publishedToPatient ? (
                        <button
                          className="primary-button"
                          type="button"
                          onClick={() => onPublishReport(record.id)}
                          disabled={busyAction === `publish-${record.id}`}
                        >
                          {busyAction === `publish-${record.id}` ? "发布中" : "发布给患者"}
                        </button>
                      ) : null}
                    </div>
                  ) : null}
                </article>
              );
            }) : <p className="treatment-empty">暂无患者端发起的问诊。</p>}
          </div>
        </section>
      </div>
    </section>
  );
}
