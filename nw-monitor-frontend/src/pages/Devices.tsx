import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import { Badge, Card, Loading, StatusBadge } from '../components/ui';
import { label, timeAgo } from '../format';

export default function Devices() {
  const nav = useNavigate();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const devices = useQuery({ queryKey: ['devices', q, status], queryFn: () => api.devices({ q: q || undefined, status: status || undefined }) });
  const scan = useMutation({ mutationFn: api.scanNow, onSuccess: () => setTimeout(() => void qc.invalidateQueries({ queryKey: ['devices'] }), 4000) });
  const trust = useMutation({
    mutationFn: (id: string) => api.updateDevice(id, { trusted: true }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['devices'] }),
  });

  return (
    <>
      <div className="between"><h1>Devices</h1>
        <button onClick={() => scan.mutate()} disabled={scan.isPending}>{scan.isPending ? 'Scanning…' : 'Scan now'}</button></div>
      <Card>
        <div className="row" style={{ marginBottom: '.8rem' }}>
          <input placeholder="Search name, IP or MAC" value={q} onChange={(e) => setQ(e.target.value)} />
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All</option><option value="ONLINE">Online</option><option value="OFFLINE">Offline</option>
          </select>
        </div>
        {!devices.data ? <Loading /> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Type</th><th>IP</th><th>MAC</th><th>Status</th><th>Last seen</th><th /></tr></thead>
            <tbody>{devices.data.map((d) => (
              <tr key={d.id} className="clickable" onClick={() => nav(`/devices/${d.id}`)}>
                <td>{d.name} {!d.trusted && <Badge kind="warn">New</Badge>}</td>
                <td>{label(d.deviceType)}</td>
                <td className="mono">{d.ip ?? '—'}</td>
                <td className="mono">{d.mac}{d.randomizedMac && <span title="Randomized (private) MAC"> 🎲</span>}</td>
                <td><StatusBadge s={d.status} /></td>
                <td className="muted">{timeAgo(d.lastSeen)}</td>
                <td onClick={(e) => e.stopPropagation()}>{!d.trusted && <button onClick={() => trust.mutate(d.id)}>Trust</button>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
        {devices.data?.length === 0 && <p className="muted">No devices found yet. A scan runs automatically every minute.</p>}
      </Card>
    </>
  );
}
