export const queueFilters = {
  active: "待我处理", evidence: "待核资料", review: "待复核", publish: "待发布", followUp: "待看随访", all: "全部问诊"
};

function actions(item, followUps) {
  const result = [];
  if (item.evidenceStatus === "PENDING_DOCTOR") result.push("evidence");
  if (["WAITING_DOCTOR", "WAITING_REPORT"].includes(item.status)) result.push("review");
  if (item.status === "REPORT_READY" && !item.reportPublished) result.push("publish");
  if (followUps.some(task => task.recordId === item.reportId && task.submittedAt && !task.reviewedAt)) result.push("followUp");
  return result;
}

export function selectQueue(consultations, followUps, filter) {
  return consultations.flatMap(item => {
    const pending = actions(item, followUps);
    if (filter !== "all" && !(filter === "active" ? pending.length : pending.includes(filter))) return [];
    const dates = [];
    if (pending.some(action => action !== "followUp")) dates.push(item.updatedAt);
    if (pending.includes("followUp")) dates.push(...followUps.filter(task => task.recordId === item.reportId && task.submittedAt && !task.reviewedAt).map(task => task.submittedAt));
    const since = dates.filter(Boolean).map(Date.parse).filter(Number.isFinite);
    return [{item, since: since.length ? Math.min(...since) : null}];
  }).sort((a, b) => (a.since ?? Infinity) - (b.since ?? Infinity) || Date.parse(b.item.updatedAt) - Date.parse(a.item.updatedAt));
}

export function waitingTime(since, now = Date.now()) {
  const minutes = Math.max(0, Math.floor((now - since) / 60000));
  if (minutes < 60) return minutes + " 分钟";
  if (minutes < 1440) return Math.floor(minutes / 60) + " 小时";
  return Math.floor(minutes / 1440) + " 天";
}
