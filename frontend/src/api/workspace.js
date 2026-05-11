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

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    throw new Error(payload.message || `Request failed: ${response.status}`);
  }

  return payload;
}

export function fetchPatients() {
  return request("/patients");
}

export function createPatient(payload) {
  return request("/patients", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function updatePatient(patientId, payload) {
  return request(`/patients/${patientId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function deletePatient(patientId) {
  return request(`/patients/${patientId}`, {
    method: "DELETE"
  });
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

export function submitConsultation(message, sessionId, patient) {
  return request("/consultations", {
    method: "POST",
    body: JSON.stringify({
      message,
      sessionId,
      patientName: patient?.name || "",
      patientPhone: patient?.phone || "",
      patientGender: patient?.gender || "",
      patientAge: patient?.age ? String(patient.age) : "",
      patientWeight: patient?.weight ? String(patient.weight) : "",
      chiefComplaint: patient?.chiefComplaint || ""
    })
  });
}
