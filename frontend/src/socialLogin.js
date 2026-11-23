const providerLabels = new Map([
  ["wechat", "微信"],
  ["qq", "QQ"]
]);

export function readSocialCallback(search) {
  const action = new URLSearchParams(search).get("social");
  return action === "bind" || action === "error" ? action : null;
}

export function enabledSocialProviders(response) {
  if (!Array.isArray(response?.providers)) return [];
  return [...providerLabels.keys()].filter((name) => response.providers.includes(name));
}

export function socialIdentity(provider, role) {
  const label = providerLabels.get(provider);
  if (!label || (role !== "DOCTOR" && role !== "PATIENT")) return null;
  return {
    providerLabel: label,
    roleLabel: role === "DOCTOR" ? "医生" : "患者",
    startPath: "/api/auth/social/" + provider + "/start?role=" + role
  };
}

export function readPendingBinding(data) {
  if (!socialIdentity(data?.provider, data?.role) || typeof data.bindingId !== "string" || !data.bindingId.trim()) return null;
  return { provider: data.provider, role: data.role, bindingId: data.bindingId };
}


export function samePendingBinding(expected, response) {
  const actual = readPendingBinding(response);
  return Boolean(expected && actual && expected.bindingId === actual.bindingId
    && expected.provider === actual.provider && expected.role === actual.role);
}


export function callbackBindingId(fragment) {
  const values = new URLSearchParams(fragment.replace(/^#/, "")).getAll("bindingId");
  return values.length === 1 && /^[A-Za-z0-9_-]+$/.test(values[0]) ? values[0] : null;
}
