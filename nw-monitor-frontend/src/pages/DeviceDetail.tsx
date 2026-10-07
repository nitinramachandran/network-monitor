import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/api';
import { DEVICE_TYPES, DeviceType } from '../api/types';
import { Badge, Card, Field, Loading, Pager, StatusBadge } from '../components/ui';
import { bytesToMb, daysAgo, fmtDateTime, fmtMb, fmtTime, isoDay, label } from '../format';

export default function DeviceDetail() {
  const { id = '' } = useParams();
  const nav = useNavigate();
  const qc = useQueryClient();
  const device = useQuery({ queryKey: ['device', id], queryFn: () => api.device(id) });
  const usage = useQuery({ queryKey: ['device-usage', id], queryFn: () => api.deviceUsage(id, daysAgo(13), isoDay()) });
  const [day, setDay] = useState(isoDay());
  const [page, setPage] = useState(0);
  const activity = useQuery({ queryKey: ['device-activity', id, day, page], queryFn: () => api.activity({ deviceId: id, date: day, page, size: 25 }) });
  const top = useQuery({ queryKey: ['device-top', id, day], queryFn: () => api.topDomains({ deviceId: id, date: day, limit: 8 }) });

  const [name, setName] = useState('');
  const [type, setType] = useState<DeviceType>('UNKNOWN');
  useEffect(() => { if (device.data) { setName(device.data.label ?? ''); setType(device.data.deviceType); } }, [device.data]);

  const save = useMutation({
    mutationFn: (b: Parameters<typeof api.updateDevice>[1]) => api.updateDevice(id, b),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['device', id] }); void qc.invalidateQueries({ queryKey: ['devices'] }); },
  });
  const forget = useMutation({ mutationFn: () => api.forgetDevice(id), onSuccess: () => nav('/devices') });

  const d = device.data;
  if (!d) return <Loading />;
  const todayMb = (usage.data ?? []).find((u) => u.date === isoDay())?.megabytes ?? 0;

  return (
    <>
      <p><Link to="/devices">← Devices</Link></p>
      <div className="between"><h1>{d.name} <StatusBadge s={d.status} /> {!d.trusted && <Badge kind="warn">Untrusted</Badge>}</h1>
        <div className="row">
          <button onClick={() => save.mutate({ trusted: !d.trusted })}>{d.trusted ? 'Mark untrusted' : 'Trust device'}</button>
          <button className="danger" onClick={() => confirm('Forget this device and its history?') && forget.mutate()}>Forget</button>
        </div></div>
      <div className="grid two">
        <Card title="Details">
          <p className="mono">{d.ip ?? '—'} · {d.mac}{d.randomizedMac ? ' (randomized MAC)' : ''}</p>
          <p className="muted small">Hostname: {d.hostname ?? '—'} · First seen {fmtDateTime(d.firstSeen)} · Last seen {fmtDateTime(d.lastSeen)}</p>
          <Field label="Label"><input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Dad's phone" /></Field>
          <Field label="Type"><select value={type} onChange={(e) => setType(e.target.value as DeviceType)}>{DEVICE_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}</select></Field>
          <button className="primary" onClick={() => save.mutate({ label: name, deviceType: type })} disabled={save.isPending}>Save</button>
        </Card>
        <Card title={`Data usage · today ${fmtMb(todayMb)}`}>
          <div style={{ height: 200 }}><ResponsiveContainer>
            <BarChart data={(usage.data ?? []).map((u) => ({ day: u.date.slice(5), Down: +bytesToMb(u.bytesDown).toFixed(1), Up: +bytesToMb(u.bytesUp).toFixed(1) }))}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" /><XAxis dataKey="day" stroke="var(--muted)" /><YAxis stroke="var(--muted)" />
              <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)' }} />
              <Bar dataKey="Down" stackId="a" fill="var(--accent)" /><Bar dataKey="Up" stackId="a" fill="var(--ok)" />
            </BarChart></ResponsiveContainer></div>
        </Card>
      </div>
      <Card title="Web activity" actions={<input type="date" value={day} max={isoDay()} onChange={(e) => { setDay(e.target.value); setPage(0); }} />}>
        {top.data && top.data.length > 0 && <p className="small">Top: {top.data.map((t) => `${t.domain} (${t.hits})`).join(' · ')}</p>}
        <div className="table-wrap"><table><thead><tr><th>Time</th><th>Domain</th><th /></tr></thead>
          <tbody>{activity.data?.items.map((a) => (
            <tr key={a.id}><td className="muted">{fmtTime(a.queriedAt)}</td><td>{a.domain}</td><td>{a.blocked && <Badge kind="crit">Blocked</Badge>}</td></tr>))}</tbody></table></div>
        {activity.data?.items.length === 0 && <p className="muted">No activity recorded for this day.</p>}
        <Pager page={page} totalPages={activity.data?.totalPages ?? 0} onChange={setPage} />
      </Card>
    </>
  );
}
