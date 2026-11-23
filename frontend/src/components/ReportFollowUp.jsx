import { useState } from "react";
import { completeFollowUp, submitFollowUp } from "../api/followUp";

export function PublishReportForm({onPublish, busy}) {
  const [schedule, setSchedule] = useState(false);
  const [date, setDate] = useState("");
  const [instructions, setInstructions] = useState("");
  const today = new Date();
  const minDate = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, "0"),
    String(today.getDate()).padStart(2, "0")].join("-");
  return <form className="follow-up-form" onSubmit={event => {
    event.preventDefault();
    onPublish(schedule ? {dueDate: date, instructions: instructions.trim()} : null);
  }}>
    <label><input type="checkbox" checked={schedule} onChange={event => setSchedule(event.target.checked)} /> 安排复诊随访</label>
    {schedule && <>
      <label>复诊日期<input type="date" required min={minDate} value={date} onChange={event => setDate(event.target.value)} /></label>
      <label>待补检查或随访要求<textarea required maxLength={2000} value={instructions} onChange={event => setInstructions(event.target.value)} /></label>
    </>}
    <button className="primary-button" disabled={busy || (schedule && !instructions.trim())}>
      {busy ? "发布中" : "发布给患者"}
    </button>
  </form>;
}

export function ReportFollowUp({task, patient = false, onChange}) {
  const [result, setResult] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  async function submit(event) {
    event.preventDefault();
    setPending(true);
    setError("");
    try {
      if (patient) await submitFollowUp(task.id, result.trim());
      else await completeFollowUp(task.id);
      onChange();
    } catch (err) { setError(err.message); }
    finally { setPending(false); }
  }
  return <section className="follow-up-card">
    <h4>复诊随访 · {task.dueDate}</h4>
    <p>第 {task.revision} 版报告 · {task.reviewedAt ? "医生已阅" : task.submittedAt ? "待医生查看" : "待患者反馈"}</p>
    <dl><dt>原报告结论</dt><dd>{task.originalConclusion}</dd>
      <dt>随访要求</dt><dd>{task.instructions}</dd>
      {task.submittedAt && <><dt>患者反馈 · {new Date(task.submittedAt).toLocaleDateString("zh-CN")}</dt><dd>{task.patientResult}</dd></>}
    </dl>
    {((patient && !task.submittedAt) || (!patient && task.submittedAt && !task.reviewedAt)) &&
      <form className="follow-up-form" onSubmit={submit}>
        {patient && <label>检查结果或病情变化<textarea required maxLength={6000} value={result} onChange={event => setResult(event.target.value)} /></label>}
        <button className="secondary-button" disabled={pending || (patient && !result.trim())}>
          {pending ? "保存中" : patient ? "提交随访结果" : "确认已查看结果"}
        </button>
      </form>}
    {error && <p role="alert">{error}</p>}
  </section>;
}
