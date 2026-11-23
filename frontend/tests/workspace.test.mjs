import test from "node:test";
import assert from "node:assert/strict";
import { draftKey, emptyDraft, updateDraft } from "../src/consultationDraft.js";
import { buildDiagnosisReportHtml, reportFileLabel } from "../src/reportExport.js";

test("切换患者与会话后，识别结果仍写回发起识别的草稿", () => {
  const patientA = draftKey(1, null);
  const patientB = draftKey(2, null);
  const session = draftKey(1, "history-1");
  const file = { name: "A.pdf" };
  let drafts = updateDraft({}, patientA, { medicalEvidenceFile: file, consultationInput: "A 主诉" });
  drafts = updateDraft(drafts, patientB, { consultationInput: "B 主诉" });
  drafts = updateDraft(drafts, session, { opinion: "历史会话复核" });
  drafts = updateDraft(drafts, patientA, { medicalEvidence: { evidenceText: "A 识别结果" } });
  assert.equal(drafts[patientA].medicalEvidenceFile, file);
  assert.equal(drafts[patientA].consultationInput, "A 主诉");
  assert.deepEqual(drafts[patientB], { ...emptyDraft, consultationInput: "B 主诉" });
  assert.deepEqual(drafts[session], { ...emptyDraft, opinion: "历史会话复核" });
  assert.equal(draftKey(null, "history-1"), session);
});

test("待复核或导入的记录生成草稿，文件名与正文都标明状态", () => {
  for (const record of [null, { reviewStatus: "IMPORTED", doctorOpinion: "历史意见" }]) {
    const html = buildDiagnosisReportHtml({
      patient: { name: "<患者>" }, diagnosis: { conclusion: "当前 AI 建议" },
      sessionId: "case-1", opinion: "尚未保存的意见", finalRecord: record
    });
    assert.equal(reportFileLabel(record), "诊断报告草稿");
    assert.match(html, /<h1>诊断报告草稿<\/h1>/);
    assert.match(html, /&lt;患者&gt;/);
    assert.match(html, /待医生复核/);
    assert.match(html, /case-1/);
    assert.doesNotMatch(html, /尚未保存的意见|医生确认 AI 结论|历史意见/);
  }
});

test("复核报告取服务端记录，保留零置信度且排除屏幕上的未确认内容", () => {
  const record = { reviewStatus: "DOCTOR_OVERRIDDEN", sessionId: "case-2", aiConclusion: "复核时 AI 建议",
    finalConclusion: "复核最终诊断", doctorOpinion: "医生保存的意见", confidence: 0, riskLevel: "低",
    chiefComplaint: "复核时主诉" };
  const html = buildDiagnosisReportHtml({ finalRecord: record, patient: { chiefComplaint: "草拟主诉" },
    opinion: "尚未保存的意见", diagnosis: { conclusion: "另一个初步结论", riskLevel: "草拟风险",
      confidence: 0.99, structuredAnalysis: ["未确认依据"] } });
  assert.match(html, /<h1>医生复核诊断报告<\/h1>/);
  assert.match(html, /复核最终诊断/);
  assert.match(html, /医生保存的意见/);
  assert.match(html, /AI 置信度：0%/);
  assert.doesNotMatch(html, /另一个初步结论|尚未保存的意见|草拟风险|未确认依据|草拟主诉|99%/);
});

test("发布更正后仍可导出指定旧版本，版本号和快照内容同时保留", () => {
  const first = { recordId: 1, revision: 1, sessionId: "case-2", publishedAt: "2026-11-01T16:00:00Z",
    patientName: "发布时患者姓名", aiConclusion: "原始辅助结论", finalConclusion: "首版结论" };
  const second = { ...first, revision: 2, publishedAt: "2026-11-02T16:00:00Z", finalConclusion: "修订结论" };
  const report = (version) => buildDiagnosisReportHtml({ finalRecord: version, patient: { name: "当前页面患者" }, diagnosis: { conclusion: "未发布草稿" } });
  assert.match(report(first), /第 1 版/);
  assert.match(report(first), /首版结论/);
  assert.match(report(first), /发布时患者姓名/);
  assert.doesNotMatch(report(first), /当前页面患者/);
  assert.doesNotMatch(report(first), /修订结论|未发布草稿/);
  assert.match(report({ ...first, patientName: null, patientRecordId: 42 }), /发布时未记录姓名（档案 42）/);
  assert.doesNotMatch(report({ ...first, patientName: null }), /当前页面患者/);
  assert.match(report(second), /第 2 版/);
  assert.match(report(second), /修订结论/);
  assert.doesNotMatch(report(second), /首版结论|未发布草稿/);
});
