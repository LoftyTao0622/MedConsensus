import { useEffect, useState } from "react";
import { csrfHeaders } from "./csrf";

async function request(path, options) {
  const response = await fetch(path, {credentials: "include", ...options});
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || "随访请求失败，请刷新后重试");
  return data;
}

export function useFollowUps(role, refreshKey) {
  const [tasks, setTasks] = useState([]);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    request(`/api/${role}/follow-ups`).then(data => {
      if (active) { setTasks(data); setError(""); }
    }).catch(err => { if (active) setError(err.message); });
    return () => { active = false; };
  }, [role, refreshKey, reload]);
  return {tasks, error, refresh: () => setReload(value => value + 1)};
}

export function submitFollowUp(id, result) {
  return request(`/api/patient/follow-ups/${id}/result`, {
    method: "POST", headers: {"Content-Type": "application/json", ...csrfHeaders()},
    body: JSON.stringify({result})
  });
}

export function completeFollowUp(id) {
  return request(`/api/workspace/follow-ups/${id}/complete`, {method: "POST", headers: csrfHeaders()});
}
