import { FormEvent, useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Card, Field } from '../components/ui';

export default function Account() {
  const { user, signOut } = useAuth();
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    try {
      await api.changePassword(cur, next);
      setMsg({ ok: true, text: 'Password changed. Signing you out…' });
      setTimeout(() => void signOut(), 1200);
    } catch (err) { setMsg({ ok: false, text: errorMessage(err) }); }
  }

  return (
    <>
      <h1>Account</h1>
      <Card title={`${user?.displayName} (@${user?.username})`}><p className="muted">Role: {user?.role}</p></Card>
      <Card title="Change password">
        <form onSubmit={submit} style={{ maxWidth: 360 }}>
          <Field label="Current password"><input type="password" value={cur} onChange={(e) => setCur(e.target.value)} autoComplete="current-password" /></Field>
          <Field label="New password" hint="At least 10 characters"><input type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" /></Field>
          {msg && <p className={msg.ok ? 'hint' : 'error'}>{msg.text}</p>}
          <button className="primary" disabled={!cur || next.length < 10}>Change password</button>
        </form>
      </Card>
    </>
  );
}
