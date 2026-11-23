import assert from "node:assert/strict";
import test from "node:test";
import { selectQueue, waitingTime } from "../src/doctorQueue.js";

test("问诊待办包含待阅随访，按实际开始等待的时间排序", () => {
  const queue = [
    { id: 1, status: "WAITING_REPORT", updatedAt: "2026-11-20T08:00:00Z" },
    { id: 2, status: "REPORT_PUBLISHED", reportId: 24, updatedAt: "2026-10-20T08:00:00Z" },
    { id: 3, status: "NEEDS_PATIENT_REPLY", updatedAt: "2026-11-19T08:00:00Z" },
    { id: 4, status: "REPORT_READY", reportId: 25, updatedAt: "2026-11-18T08:00:00Z" }
  ];
  const followUps = [{recordId: 24, submittedAt: "2026-11-21T08:00:00Z"}];
  assert.deepEqual(selectQueue(queue, followUps, "active").map(row => row.item.id), [4, 1, 2]);
  assert.deepEqual(selectQueue(queue, followUps, "followUp").map(row => row.item.id), [2]);
  assert.deepEqual(selectQueue(queue, followUps, "publish").map(row => row.item.id), [4]);
  assert.equal(waitingTime(Date.parse(followUps[0].submittedAt), Date.parse("2026-11-22T08:00:00Z")), "1 天");
});
