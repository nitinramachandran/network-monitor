import { FormEvent, useState } from 'react';
import { errorMessage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../components/ui';

export default function Login() {
  const { signIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError('');
    try { await signIn(username, password); } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); }
  }

  return (
    <div className="center-screen">
      <form className="card auth-card" onSubmit={submit}>
        <h1>🛰️ Network Monitor</h1>
        <Field label="Username"><input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" /></Field>
        <Field label="Password"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></Field>
        {error && <p className="error">{error}</p>}
        <button className="primary" disabled={busy || !username || !password} style={{ width: '100%' }}>Sign in</button>
      </form>
    </div>
  );
}
