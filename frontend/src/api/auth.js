const AUTH_BASE = "/api/auth";
import { csrfHeaders } from "./csrf";

async function request(path, options = {}) {
  const response = await fetch(`${AUTH_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...csrfHeaders(),
      ...(options.headers || {})
    },
    ...options
  });

  const payload = await response.json().catch(() => ({}));

  if (!response.ok) {
    const error = new Error(payload.message || `Request failed: ${response.status}`);
    error.status = response.status;
    throw error;
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

export function fetchSocialProviders() {
  return request("/social/providers");
}

export function fetchSocialPending() {
  return request("/social/pending");
}

export function bindSocialAccount(phone, password, bindingId) {
  return request("/social/bind", { method: "POST", body: JSON.stringify({ phone, password, bindingId }) });
}

export function cancelSocialBinding(bindingId) {
  return request("/social/cancel", { method: "POST", body: JSON.stringify({ bindingId }) });
}
