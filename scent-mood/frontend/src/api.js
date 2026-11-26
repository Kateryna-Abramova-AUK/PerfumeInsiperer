export async function api(path, { method = 'GET', query, body, signal } = {}) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value?.length) params.set(key, Array.isArray(value) ? value.join(',') : value)
  }
  const response = await fetch('/api/' + path + (params.size ? '?' + params : ''), {
    method,
    signal,
    ...(body !== undefined && {
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }),
  })
  if (!response.ok) {
    const detail = await response.json().catch(() => ({}))
    throw new Error(detail.detail || 'The fragrance service is unavailable. Please try again.')
  }
  return response.status === 204 ? undefined : response.json()
}
