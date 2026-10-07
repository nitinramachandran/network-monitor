import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../api/api';
import { Badge, Card, Loading } from '../components/ui';
import { fmtDateTime, timeAgo } from '../format';

const NAMES: Record<string, string> = {
  'network-scan': 'Device discovery scan', 'dns-collector': 'Web activity collector',
  'ingest-usage': 'Agent: data usage', 'ingest-dns': 'Agent: web activity', 'ingest-security': 'Agent: security events',
};

/** Shows whether the monitor can actually see the network end to end. */
export default function Health() {
  const status = useQuery({ queryKey: ['status'], queryFn: api.systemStatus, refetchInterval: 10_000 });
  const scan = useMutation({ mutationFn: api.scanNow });
  const s = status.data;
  if (!s) return <Loading />;

  return (
    <>
      <div className="between"><h1>Connectivity</h1><button onClick={() => scan.mutate()} disabled={scan.isPending}>Run scan now</button></div>
      <div className="grid">
        <Card title="Internet">{s.connectivity.internetUp ? <Badge kind="ok">Up</Badge> : <Badge kind="crit">Down since {fmtDateTime(s.connectivity.downSince)}</Badge>}
          <p className="muted small">Checked {timeAgo(s.connectivity.checkedAt)}</p></Card>
        <Card title="Router / gateway">{s.connectivity.gatewayUp == null ? <Badge kind="neutral">Gateway IP not set</Badge> : s.connectivity.gatewayUp ? <Badge kind="ok">Reachable</Badge> : <Badge kind="crit">Unreachable</Badge>}</Card>
      </div>
      <Card title="Data sources">
        {s.sources.length === 0 ? <p className="muted">No source has reported yet. Configure them under Settings.</p> : (
          <table><thead><tr><th>Source</th><th>State</th><th>Last success</th><th>Detail</th></tr></thead>
            <tbody>{s.sources.map((x) => (
              <tr key={x.name}><td>{NAMES[x.name] ?? x.name}</td>
                <td>{x.consecutiveFailures > 0 ? <Badge kind="crit">Failing ×{x.consecutiveFailures}</Badge> : <Badge kind="ok">Healthy</Badge>}</td>
                <td className="muted">{timeAgo(x.lastSuccess)}</td><td className="small">{x.lastMessage}</td></tr>))}</tbody></table>
        )}
      </Card>
    </>
  );
}
