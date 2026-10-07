import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import { Badge, Card, Loading, Pager } from '../components/ui';
import { fmtTime, isoDay } from '../format';

export default function Activity() {
  const [day, setDay] = useState(isoDay());
  const [deviceId, setDeviceId] = useState('');
  const [domain, setDomain] = useState('');
  const [page, setPage] = useState(0);
  const devices = useQuery({ queryKey: ['devices', '', ''], queryFn: () => api.devices() });
  const rows = useQuery({
    queryKey: ['activity', day, deviceId, domain, page],
    queryFn: () => api.activity({ date: day, deviceId: deviceId || undefined, domain: domain || undefined, page, size: 50 }),
  });
  const reset = <T,>(set: (v: T) => void) => (v: T) => { set(v); setPage(0); };

  return (
    <>
      <h1>Web activity</h1>
      <p className="muted small">Shows the sites each device looked up (DNS). HTTPS page paths are encrypted and cannot be seen — only domains.</p>
      <Card>
        <div className="row" style={{ marginBottom: '.8rem' }}>
          <input type="date" value={day} max={isoDay()} onChange={(e) => reset(setDay)(e.target.value)} />
          <select value={deviceId} onChange={(e) => reset(setDeviceId)(e.target.value)}>
            <option value="">All devices</option>{devices.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
          </select>
          <input placeholder="Filter domain" value={domain} onChange={(e) => reset(setDomain)(e.target.value)} />
        </div>
        {!rows.data ? <Loading /> : (
          <div className="table-wrap"><table><thead><tr><th>Time</th><th>Device</th><th>Domain</th><th /></tr></thead>
            <tbody>{rows.data.items.map((a) => (
              <tr key={a.id}><td className="muted">{fmtTime(a.queriedAt)}</td><td><Link to={`/devices/${a.deviceId}`}>{a.deviceName}</Link></td>
                <td>{a.domain}</td><td>{a.blocked && <Badge kind="crit">Blocked</Badge>}</td></tr>))}</tbody></table></div>
        )}
        {rows.data?.items.length === 0 && <p className="muted">Nothing recorded. Configure a source under Settings → Web activity.</p>}
        <Pager page={page} totalPages={rows.data?.totalPages ?? 0} onChange={setPage} />
      </Card>
    </>
  );
}
