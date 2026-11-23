import test from "node:test";
import assert from "node:assert/strict";
import { callbackBindingId, enabledSocialProviders, readPendingBinding, readSocialCallback, samePendingBinding, socialIdentity } from "../src/socialLogin.js";

test("只呈现服务端启用且前端支持的平台", () => {
  assert.deepEqual(enabledSocialProviders({ providers: ["qq", "qq", "unknown"] }), ["qq"]);
  assert.deepEqual(enabledSocialProviders({ providers: [] }), []);
  assert.deepEqual(enabledSocialProviders({ providers: "wechat" }), []);
  assert.deepEqual(enabledSocialProviders({ providers: ["__proto__", "https://example.org"] }), []);
});

test("登录路径只允许当前站点的微信或 QQ 入口，并须明确选择角色", () => {
  assert.equal(socialIdentity("qq", "PATIENT").startPath, "/api/auth/social/qq/start?role=PATIENT");
  assert.equal(socialIdentity("wechat", "DOCTOR").startPath, "/api/auth/social/wechat/start?role=DOCTOR");
  for (const [provider, role] of [["wechat", null], ["wechat", "ADMIN"], ["//elsewhere", "DOCTOR"], ["constructor", "PATIENT"]]) {
    assert.equal(socialIdentity(provider, role), null);
  }
});

test("回调查询串仅能选择结果页，身份和返回地址由应用控制", () => {
  assert.equal(readSocialCallback("?social=bind&role=DOCTOR&provider=qq&returnUrl=https://example.org"), "bind");
  assert.equal(readSocialCallback("?social=error&message=bad"), "error");
  assert.equal(readSocialCallback("?social=https://example.org"), null);
  assert.equal(readSocialCallback("?role=DOCTOR&provider=qq"), null);
});

test("首次绑定须持有服务端返回的待绑定编号和合法角色", () => {
  const pending = { role: "PATIENT", provider: "qq", bindingId: "single-use-42" };
  assert.deepEqual(readPendingBinding(pending), pending);
  assert.equal(readPendingBinding({ ...pending, bindingId: "" }), null);
  assert.equal(readPendingBinding({ role: "DOCTOR", provider: "qq" }), null);
  assert.equal(readPendingBinding({ ...pending, role: "ADMIN" }), null);
  assert.equal(readPendingBinding(null), null);
});


test("凭证错误后仅允许同一次授权重新输入密码", () => {
  const original = { bindingId: "pending-1", role: "PATIENT", provider: "qq" };
  assert.equal(samePendingBinding(original, { ...original }), true);
  for (const changed of [null, { bindingId: "pending-2" }, { role: "DOCTOR" }, { provider: "wechat" }]) {
    const current = changed && { ...original, ...changed };
    assert.equal(samePendingBinding(original, current), false);
  }
});


test("回调片段只取唯一的绑定编号，角色和平台仍待服务端确认", () => {
  assert.equal(callbackBindingId("#bindingId=abc_42-A&role=ADMIN&provider=test"), "abc_42-A");
  for (const fragment of ["", "#role=DOCTOR", "#bindingId=", "#bindingId=a&bindingId=b", "#bindingId=%2F%2Fother"]) {
    assert.equal(callbackBindingId(fragment), null);
  }
});
