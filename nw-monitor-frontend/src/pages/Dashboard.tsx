import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../api/api';
import { Card, Loading, Stat } from '../components/ui';
import { bytesToMb, daysAgo, fmtMb, isoDay, timeAgo } from '../format';

export default function Dashboard() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard, refetchInterval: 15_000 });
  const week = useQuery({ queryKey: ['usage', 'week'], queryFn: () => api.usageTotals(daysAgo(6), isoDay()) });
  const d = dash.data;
  if (!d) return <Loading />;

  const c = d.connectivity;
  return (
    <>
      <h1>Dashboard</h1>
      {!c.internetUp
        ? <div className="banner crit">Internet is down since {timeAgo(c.downSince)}{c.gatewayUp === false ? ' — the router is unreachable too.' : '.'}</div>
        : <div className="banner ok">Internet connection OK{c.checkedAt ? ` · checked ${timeAgo(c.checkedAt)}` : ''}</div>}
      <div className="grid">
        <Stat value={`${d.devicesOnline} / ${d.devicesTotal}`} label="Devices online" />
        <Stat value={d.devicesUntrusted} label="Untrusted devices" tone={d.devicesUntrusted ? 'warn' : undefined} />
        <Stat value={d.openAlerts} label="Open alerts" tone={d.openAlerts ? 'crit' : undefined} />
        <Stat value={fmtMb(d.todayMegabytes)} label="Data used today" />
      </div>
      <div className="grid two">
        <Card title="Last 7 days (MB)">
          <div style={{ height: 220 }}>
            <ResponsiveContainer>
              <BarChart data={(week.data ?? []).map((x) => ({ day: x.date.slice(5), Down: +bytesToMb(x.bytesDown).toFixed(1), Up: +bytesToMb(x.bytesUp).toFixed(1) }))}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="day" stroke="var(--muted)" /><YAxis stroke="var(--muted)" />
                <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)' }} />
                <Bar dataKey="Down" stackId="a" fill="var(--accent)" /><Bar dataKey="Up" stackId="a" fill="var(--ok)" />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Top devices today" actions={<Link to="/usage">All</Link>}>
          {d.topDevices.length === 0 ? <p className="muted">No traffic recorded yet.</p> : (
            <table><tbody>{d.topDevices.map((x) => (
              <tr key={x.deviceId}><td><Link to={`/devices/${x.deviceId}`}>{x.deviceName}</Link></td><td style={{ textAlign: 'right' }}>{fmtMb(x.megabytes)}</td></tr>
            ))}</tbody></table>
          )}
        </Card>
      </div>
      <Card title="Top domains today" actions={<Link to="/activity">Browse</Link>}>
        {d.topDomains.length === 0 ? <p className="muted">No web activity yet. Configure a source under Settings → Web activity.</p> : (
          <table><tbody>{d.topDomains.map((x) => <tr key={x.domain}><td>{x.domain}</td><td style={{ textAlign: 'right' }}>{x.hits} lookups</td></tr>)}</tbody></table>
        )}
      </Card>
    </>
  );
}
