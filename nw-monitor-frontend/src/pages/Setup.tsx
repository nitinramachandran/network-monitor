import { FormEvent, useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/client';
import { Field } from '../components/ui';

/** First-run: creates the one and only root user. */
export default function Setup({ onDone }: { onDone: () => void }) {
  const [f, setF] = useState({ username: '', displayName: '', email: '', password: '', confirm: '' });
  const [error, setError] = useState('');
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (f.password !== f.confirm) return setError('Passwords do not match');
    try {
      await api.setupRoot({ username: f.username, displayName: f.displayName || f.username, email: f.email || undefined, password: f.password });
      onDone();
    } catch (err) { setError(errorMessage(err)); }
  }

  return (
    <div className="center-screen">
      <form className="card auth-card" onSubmit={submit}>
        <h1>Welcome</h1>
        <p className="muted">Create the root account. It is the only root user and can add or remove admins.</p>
        <Field label="Username"><input value={f.username} onChange={set('username')} autoComplete="username" /></Field>
        <Field label="Display name"><input value={f.displayName} onChange={set('displayName')} /></Field>
        <Field label="Email (optional)"><input type="email" value={f.email} onChange={set('email')} /></Field>
        <Field label="Password" hint="At least 10 characters"><input type="password" value={f.password} onChange={set('password')} autoComplete="new-password" /></Field>
        <Field label="Confirm password"><input type="password" value={f.confirm} onChange={set('confirm')} autoComplete="new-password" /></Field>
        {error && <p className="error">{error}</p>}
        <button className="primary" style={{ width: '100%' }}>Create root account</button>
      </form>
    </div>
  );
}
