import { useState } from "react";
import { SessionEvidence } from "./EvidenceSource";
import { fetchReportRevisions } from "../api/workspace";

export function ReportVersions({ record, onDownload }) {
  const [versions, setVersions] = useState([]);
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const [downloadError, setDownloadError] = useState("");
  async function download(version) {
    setDownloadError("");
    try {
      await onDownload(version);
    } catch (failure) {
      setDownloadError("导出失败：" + failure.message);
    }
  }

  async function toggleVersions() {
    if (open) {
      setOpen(false);
      return;
    }
    setOpen(true);
    setLoading(true);
    setError("");
    try {
      setVersions(await fetchReportRevisions(record.id));
    } catch (failure) {
      setError(failure.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <button type="button" className="secondary-button" aria-expanded={open} disabled={loading} onClick={toggleVersions}>
        {open ? "收起发布历史" : "发布历史"}
      </button>
      {open && (loading ? <p>正在读取发布记录…</p> : error ? <p role="alert">{error}</p> :
        versions.length ? versions.map((version) => (
          <details key={version.revision}>
            <summary>第 {version.revision} 版 · {new Date(version.publishedAt).toLocaleString("zh-CN")}</summary>
            <p>最终结论：{version.finalConclusion}</p>
            <p>复核意见：{version.doctorOpinion || "确认 AI 结论"}</p>
            {version.treatmentAdvice && <p>治疗建议：{version.treatmentAdvice}</p>}
            <SessionEvidence sessionId={version.sessionId} reportId={record.id} revision={version.revision} />
            <button type="button" className="secondary-button" onClick={() => download(version)}>导出此版报告</button>
          </details>
        )) : <p>暂无已发布版本。</p>)}
      {downloadError && <p role="alert">{downloadError}</p>}
    </div>
  );
}
