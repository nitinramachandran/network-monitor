export const fmtMb = (mb: number) => (mb >= 1024 ? `${(mb / 1024).toFixed(2)} GB` : `${mb.toFixed(mb < 10 ? 2 : 1)} MB`);
export const bytesToMb = (b: number) => b / 1_048_576;
export const fmtDateTime = (iso?: string) => (iso ? new Date(iso).toLocaleString() : '—');
export const fmtTime = (iso?: string) => (iso ? new Date(iso).toLocaleTimeString() : '—');

export function timeAgo(iso?: string): string {
  if (!iso) return 'never';
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return `${Math.floor(s / 86400)} d ago`;
}

/** yyyy-MM-dd in local time (what the API expects for day filters). */
export const isoDay = (d: Date = new Date()) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const daysAgo = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return isoDay(d); };
export const label = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
