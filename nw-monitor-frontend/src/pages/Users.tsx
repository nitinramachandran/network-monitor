import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/api';
import { errorMessage } from '../api/client';
import { Badge, Card, Field, Loading } from '../components/ui';
import { fmtDateTime } from '../format';

/** Root-only screen: add, disable, reset or remove admin users. */
export default function Users() {
  const qc = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: api.users });
  const refresh = () => void qc.invalidateQueries({ queryKey: ['users'] });
  const [f, setF] = useState({ username: '', displayName: '', email: '', password: '' });
  const [msg, setMsg] = useState('');

  const create = useMutation({
    mutationFn: () => api.createUser({ ...f, email: f.email || undefined }),
    onSuccess: () => { setF({ username: '', displayName: '', email: '', password: '' }); setMsg(''); refresh(); },
    onError: (e) => setMsg(errorMessage(e)),
  });
  const toggle = useMutation({ mutationFn: (u: { id: string; enabled: boolean }) => api.updateUser(u.id, { enabled: u.enabled }), onSuccess: refresh });
  const remove = useMutation({ mutationFn: api.deleteUser, onSuccess: refresh });
  const reset = useMutation({ mutationFn: (v: { id: string; pw: string }) => api.resetPassword(v.id, v.pw), onError: (e) => alert(errorMessage(e)), onSuccess: () => alert('Password reset') });

  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  return (
    <>
      <h1>Users</h1>
      <Card title="Accounts">
        {!users.data ? <Loading /> : (
          <div className="table-wrap"><table>
            <thead><tr><th>User</th><th>Role</th><th>Last login</th><th /></tr></thead>
            <tbody>{users.data.map((u) => (
              <tr key={u.id}>
                <td>{u.displayName} <span className="muted">@{u.username}</span> {!u.enabled && <Badge kind="neutral">Disabled</Badge>}</td>
                <td><Badge kind={u.role === 'ROOT' ? 'info' : 'neutral'}>{u.role}</Badge></td>
                <td className="muted">{fmtDateTime(u.lastLoginAt)}</td>
                <td>{u.role !== 'ROOT' && <div className="row end">
                  <button onClick={() => { const pw = prompt('New password (min 10 characters)'); if (pw) reset.mutate({ id: u.id, pw }); }}>Reset password</button>
                  <button onClick={() => toggle.mutate({ id: u.id, enabled: !u.enabled })}>{u.enabled ? 'Disable' : 'Enable'}</button>
                  <button className="danger" onClick={() => confirm(`Remove ${u.username}?`) && remove.mutate(u.id)}>Remove</button></div>}</td>
              </tr>))}</tbody>
          </table></div>
        )}
      </Card>
      <Card title="Add an admin">
        <form onSubmit={submit}>
          <div className="grid">
            <Field label="Username"><input value={f.username} onChange={set('username')} /></Field>
            <Field label="Display name"><input value={f.displayName} onChange={set('displayName')} /></Field>
            <Field label="Email (optional)"><input type="email" value={f.email} onChange={set('email')} /></Field>
            <Field label="Temporary password" hint="At least 10 characters"><input type="password" value={f.password} onChange={set('password')} autoComplete="new-password" /></Field>
          </div>
          {msg && <p className="error">{msg}</p>}
          <button className="primary" disabled={create.isPending}>Add admin</button>
        </form>
      </Card>
    </>
  );
}
