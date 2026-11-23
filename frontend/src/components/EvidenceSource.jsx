import { useEffect, useState } from "react";
import { csrfHeaders } from "../api/csrf";

async function evidenceRequest(path, options) {
  const response = await fetch(`/api/evidence${path}`, { credentials: "same-origin", ...options });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || `读取或更新资料失败（${response.status}）`);
  }
  return response.status === 204 ? null : response.json();
}

export function SourceDetails({ source }) {
  if (!source) return null;
  const original = `/api/evidence/${encodeURIComponent(source.id)}/original`;
  return (
    <section className="evidence-section">
      <strong>{source.fileName}</strong>
      <p>{source.scope} · <a href={original} target="_blank" rel="noreferrer">打开原件</a></p>
      {!!source.citations?.length && <h4>初始识别条目与引用</h4>}
      {!!source.citations?.length && <ul>{source.citations.map(({finding, quote, page, location}) =>
        <li key={finding}>
          <strong>{finding}</strong>{quote && <span> · {location === "原文已匹配" ? "原文" : "识别摘录"}「{quote}」</span>}
          {" · "}{page ? <a href={`${original}#page=${page}`} target="_blank" rel="noreferrer">第 {page} 页</a> : null}
          {" "}{location}
        </li>)}</ul>}
      {source.reviewedText && source.reviewedText !== source.originalText &&
        <details><summary>医生确认的资料文本</summary><p style={{whiteSpace: "pre-wrap"}}>{source.reviewedText}</p></details>}
    </section>
  );
}

export function SessionEvidence({ sessionId, reportId, revision }) {
  return <ArchivedSources key={reportId ? `report-${reportId}-${revision}` : sessionId}
    sessionId={sessionId} reportId={reportId} revision={revision} />;
}

function ArchivedSources({ sessionId, reportId, revision }) {
  const [expanded, setExpanded] = useState(false);
  const [sources, setSources] = useState([]);
  const [feedback, setFeedback] = useState("");
  useEffect(() => {
    let active = true;
    setSources([]);
    if ((reportId ? revision : sessionId) && expanded) {
      setFeedback("正在读取…");
      evidenceRequest(reportId ? `/reports/${encodeURIComponent(reportId)}/revisions/${encodeURIComponent(revision)}` : `?sessionId=${encodeURIComponent(sessionId)}`).then((list) => {
        if (active) { setSources(list); setFeedback(list.length ? "" : (reportId ? "本版报告没有已归档的引用来源。" : "该会话没有已归档的原件。")); }
      }).catch((error) => { if (active) setFeedback(error.message); });
    }
    return () => { active = false; };
  }, [expanded, sessionId, reportId, revision]);
  if (reportId ? !revision : !sessionId) return null;
  return <details onToggle={(event) => setExpanded(event.currentTarget.open)}>
    <summary>检查依据与原件</summary>
    {feedback && <p role="status">{feedback}</p>}
    {sources.map((source) => <SourceDetails key={source.id} source={source} />)}
  </details>;
}

export function EvidenceCorrection(props) {
  return <CorrectionEditor key={props.sourceId} {...props} />;
}

function CorrectionEditor({ sourceId, initialText, onDirtyChange }) {
  const [text, setText] = useState(initialText || "");
  const [saved, setSaved] = useState(initialText || "");
  const [feedback, setFeedback] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    onDirtyChange?.(busy || text !== saved);
  }, [busy, text, saved, onDirtyChange]);
  useEffect(() => {
    setText(initialText || ""); setSaved(initialText || ""); setFeedback("");
  }, [sourceId, initialText]);
  if (!sourceId) return <p>{initialText}</p>;
  async function save() {
    setBusy(true);
    try {
      await evidenceRequest(`/${encodeURIComponent(sourceId)}/correction`, {
        method: "PUT", headers: {"Content-Type": "application/json", ...csrfHeaders()},
        body: JSON.stringify({text})
      });
      setSaved(text); setFeedback("已保存，确认资料时将使用此版本。");
    } catch (error) { setFeedback(error.message); }
    finally { setBusy(false); }
  }
  return <div>
    <label>进入诊断的资料文本
      <textarea rows={5} maxLength={12000} value={text} disabled={busy} onChange={(event) => setText(event.target.value)} />
    </label>
    <button className="secondary-button" type="button" disabled={busy || !text.trim() || text === saved} onClick={save}>
      {busy ? "正在保存" : "保存校正"}
    </button>
    {text !== saved && <p>此处有尚未保存的校正。</p>}
    {feedback && <p role="status">{feedback}</p>}
  </div>;
}
