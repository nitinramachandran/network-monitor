import { ReactNode } from 'react';
import type { AlertType, DeviceStatus, Severity } from '../api/types';
import { label } from '../format';

export function Badge({ kind, children }: { kind: 'ok' | 'warn' | 'crit' | 'info' | 'neutral'; children: ReactNode }) {
  return <span className={`badge ${kind}`}>{children}</span>;
}

export const SeverityBadge = ({ s }: { s: Severity }) =>
  <Badge kind={s === 'CRITICAL' ? 'crit' : s === 'WARNING' ? 'warn' : 'info'}>{label(s)}</Badge>;

export const StatusBadge = ({ s }: { s: DeviceStatus }) =>
  <Badge kind={s === 'ONLINE' ? 'ok' : 'neutral'}>{label(s)}</Badge>;

export const TypeLabel = ({ t }: { t: AlertType }) => <>{label(t)}</>;

export function Card({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && <div className="between" style={{ marginBottom: '.5rem' }}>{title && <h2 style={{ margin: 0 }}>{title}</h2>}{actions}</div>}
      {children}
    </section>
  );
}

export function Stat({ value, label: l, tone }: { value: ReactNode; label: string; tone?: 'crit' | 'warn' }) {
  return (
    <div className="card stat">
      <div className="v" style={tone ? { color: `var(--${tone})` } : undefined}>{value}</div>
      <div className="l">{l}</div>
    </div>
  );
}

export const Loading = () => <p className="muted">Loading…</p>;
export const ErrorText = ({ error }: { error: unknown }) => error ? <p className="error">{error instanceof Error ? error.message : String(error)}</p> : null;

export function Field({ label: l, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return <label className="field">{l}{children}{hint && <span className="hint">{hint}</span>}</label>;
}

export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="row end" style={{ marginTop: '.7rem' }}>
      <button disabled={page === 0} onClick={() => onChange(page - 1)}>Prev</button>
      <span className="muted small">Page {page + 1} / {totalPages}</span>
      <button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  );
}
