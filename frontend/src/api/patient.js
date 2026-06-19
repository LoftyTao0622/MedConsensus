const PATIENT_BASE = "/api/patient";

async function request(path, options = {}) {
  const response = await fetch(`${PATIENT_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || `Request failed: ${response.status}`);
  }
  return payload;
}

export function fetchPatientDashboard() {
  return request("/dashboard");
}

export function requestDoctorBinding(inviteCode) {
  return request("/bindings", {
    method: "POST",
    body: JSON.stringify({ inviteCode })
  });
}

export function startPatientConsultation(relationId, message) {
  return request("/consultations", {
    method: "POST",
    body: JSON.stringify({ relationId, message })
  });
}

export function answerPatientQuestion(consultationId, message) {
  return request(`/consultations/${consultationId}/messages`, {
    method: "POST",
    body: JSON.stringify({ message })
  });
}

export async function uploadPatientEvidence(consultationId, file) {
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch(`${PATIENT_BASE}/consultations/${consultationId}/evidence`, {
    credentials: "include",
    method: "POST",
    body: formData
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || `Upload failed: ${response.status}`);
  }
  return payload;
}

export function fetchReportExplanation(reportId) {
  return request(`/reports/${reportId}/explanations`);
}

export function askReportExplanation(reportId, message) {
  return request(`/reports/${reportId}/explanations`, {
    method: "POST",
    body: JSON.stringify({ message })
  });
}
