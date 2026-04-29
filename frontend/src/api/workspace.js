const API_BASE = "/api/workspace";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  return response.json();
}

export function fetchPatientProfile() {
  return request("/patient");
}

export function fetchSessions() {
  return request("/sessions");
}

export function fetchSessionDetail(sessionId) {
  return request(`/sessions/${sessionId}`);
}

export function deleteSession(sessionId) {
  return request(`/sessions/${sessionId}`, { method: "DELETE" });
}

export function fetchDiagnosis() {
  return request("/diagnosis");
}

export function simulatePipeline() {
  return request("/simulate", { method: "POST" });
}

export function submitDoctorReview(payload) {
  return request("/doctor-review", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function submitConsultation(message, sessionId) {
  return request("/consultations", {
    method: "POST",
    body: JSON.stringify({ message, sessionId })
  });
}
