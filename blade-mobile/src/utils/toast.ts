export function showToast(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'info') {
  // Simple implementation - just log in dev mode
  if (import.meta.env.DEV) {
    console.log(`[Toast] ${type}: ${message}`)
  }
  // Use window.alert for errors to ensure visibility
  if (type === 'error') {
    window.alert(message)
  }
}
