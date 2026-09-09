export function csrfHeaders() {
  const cookie = document.cookie.split('; ').find((item) => item.startsWith('XSRF-TOKEN='));
  if (!cookie) return {};
  return { 'X-XSRF-TOKEN': decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) };
}
