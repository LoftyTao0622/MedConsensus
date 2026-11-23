export const emptyDraft = Object.freeze({
  opinion: "",
  consultationInput: "",
  consultationSubmitting: false,
  medicalEvidence: null,
  medicalEvidenceFile: null,
  medicalEvidenceAnalyzing: false,
  medicalEvidenceConfirmed: false,
  medicalEvidenceError: "",
  medicalEvidenceReviewNote: "",
  submitting: false
});

export function draftKey(patientId, sessionId) {
  return sessionId ? "session:" + sessionId : "patient:" + (patientId ?? "new");
}

export function updateDraft(drafts, key, patch) {
  return { ...drafts, [key]: { ...emptyDraft, ...drafts[key], ...patch } };
}
