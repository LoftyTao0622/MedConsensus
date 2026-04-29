const AUTH_BASE = "/api/auth";

async function request(path, options = {}) {
  const response = await fetch(`${AUTH_BASE}${path}`, {
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

export function registerUser(form) {
  return request("/register", {
    method: "POST",
    body: JSON.stringify(form)
  });
}

export function loginUser(form) {
  return request("/login", {
    method: "POST",
    body: JSON.stringify(form)
  });
}

export function fetchCurrentUser() {
  return request("/me", {
    method: "GET"
  });
}

export function logoutUser() {
  return request("/logout", {
    method: "POST"
  });
}
