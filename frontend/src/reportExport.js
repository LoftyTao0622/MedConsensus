export function escapeHtml(value) {
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

function isPublishedRevision(record) {
  return Number.isInteger(record?.revision) && record.revision > 0 && Boolean(record.publishedAt);
}

export function isReviewedRecord(record) {
  return isPublishedRevision(record) || ["AI_CONFIRMED", "DOCTOR_OVERRIDDEN"].includes(record?.reviewStatus);
}

export function reportFileLabel(record) {
  if (isPublishedRevision(record)) return "已发布诊断报告 · 第 " + record.revision + " 版";
  return isReviewedRecord(record) ? "医生复核诊断报告" : "诊断报告草稿";
}

export function buildDiagnosisReportHtml({ patient, diagnosis, finalRecord, sessionId, doctorName }) {
  if (!isReviewedRecord(finalRecord)) finalRecord = null;
  if (isPublishedRevision(finalRecord)) {
    const historicalName = finalRecord.patientName?.trim() || "发布时未记录姓名";
    patient = { name: historicalName + (finalRecord.patientRecordId ? "（档案 " + finalRecord.patientRecordId + "）" : "") };
  }
  const label = reportFileLabel(finalRecord);
  const doctorOpinion = finalRecord ? (finalRecord.doctorOpinion || "医生确认 AI 结论。") : "待医生复核";
  const finalConclusion = finalRecord?.finalConclusion || "待医生复核";
  const riskLevel = finalRecord ? finalRecord.riskLevel : diagnosis?.riskLevel;
  const aiConclusion = finalRecord ? finalRecord.aiConclusion : diagnosis?.conclusion;
  const confidence = finalRecord ? finalRecord.confidence : diagnosis?.confidence;
  const generatedAt = new Date().toLocaleString("zh-CN");

  return `<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <title>${label}-${escapeHtml(patient?.name || "未命名患者")}</title>
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
        <h1>${label}</h1>
        <p>${finalRecord ? "已按医生复核记录生成。" : "草稿 · AI 辅助诊断尚待医生复核。"}</p>
      </div>
      <div class="meta">
        生成时间：${escapeHtml(generatedAt)}<br />
        诊断医生：${escapeHtml(doctorName || "未记录")}<br />
        会话编号：${escapeHtml(finalRecord?.sessionId || sessionId || "未记录")}
      </div>
    </section>

    <h2>一、患者基本信息</h2>
    <section class="grid">
      <div class="field"><span>患者姓名</span>${escapeHtml(patient?.name || "未填写")}</div>
      <div class="field"><span>患者电话</span>${escapeHtml(patient?.phone || "未填写")}</div>
      <div class="field"><span>年龄</span>${escapeHtml(patient?.age || "未填写")} ${patient?.age ? "岁" : ""}</div>
      <div class="field"><span>体重</span>${escapeHtml(patient?.weight || "未填写")} ${patient?.weight ? "kg" : ""}</div>
      <div class="field"><span>性别</span>${escapeHtml(patient?.gender || "未填写")}</div>
      <div class="field"><span>风险等级</span>${escapeHtml(riskLevel || "待评估")}</div>
    </section>

    <h2>二、主诉</h2>
    <section class="block"><p>${escapeHtml((finalRecord ? finalRecord.chiefComplaint : patient?.chiefComplaint) || "未记录")}</p></section>

    <h2>三、AI 辅助诊断结果</h2>
    <section class="block">
      <p>${escapeHtml(aiConclusion || "未生成")}</p>
      <p>AI 置信度：${confidence == null ? "未记录" : Math.round(confidence * 100) + "%"}</p>
    </section>

    <h2>四、诊断依据</h2>
    <section class="block">${finalRecord ? "<p>详见医生意见与最终诊断。</p>" : listHtml(diagnosis?.structuredAnalysis)}</section>

    <h2>五、医生意见</h2>
    <section class="block"><p>${escapeHtml(doctorOpinion)}</p></section>

    <h2>六、最终诊断结果</h2>
    <section class="block conclusion"><p>${escapeHtml(finalConclusion)}</p></section>
  </main>
</body>
</html>`;
}
