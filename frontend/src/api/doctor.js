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

export function fetchDoctorStats() {
  return request("/doctor-stats");
}

export function exploreGraph(query) {
  return request("/graph-explore", {
    method: "POST",
    body: JSON.stringify({ query })
  });
}
