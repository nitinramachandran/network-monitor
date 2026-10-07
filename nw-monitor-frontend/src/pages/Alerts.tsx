import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import { Badge, Card, Loading, Pager, SeverityBadge, TypeLabel } from '../components/ui';
import { fmtDateTime } from '../format';

export default function Alerts() {
  const qc = useQueryClient();
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(0);
  const alerts = useQuery({ queryKey: ['alerts', status, page], queryFn: () => api.alerts({ status: status || undefined, page, size: 20 }), refetchInterval: 15_000 });
  const refresh = () => void qc.invalidateQueries({ queryKey: ['alerts'] });
  const ack = useMutation({ mutationFn: api.ackAlert, onSuccess: refresh });
  const ackAll = useMutation({ mutationFn: api.ackAll, onSuccess: refresh });

  return (
    <>
      <div className="between"><h1>Alerts</h1>
        <div className="row">
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="OPEN">Open</option><option value="ACKNOWLEDGED">Acknowledged</option><option value="">All</option></select>
          <button onClick={() => ackAll.mutate()} disabled={ackAll.isPending}>Acknowledge all</button>
        </div></div>
      {!alerts.data ? <Loading /> : alerts.data.items.length === 0 ? <Card><p className="muted">No alerts here. 🎉</p></Card> : alerts.data.items.map((a) => (
        <Card key={a.id}>
          <div className="between">
            <div><SeverityBadge s={a.severity} /> <b>{a.title}</b> {a.occurrences > 1 && <Badge kind="neutral">×{a.occurrences}</Badge>}
              <div className="muted small"><TypeLabel t={a.type} /> · {fmtDateTime(a.lastOccurredAt)}{a.sourceIp ? ` · ${a.sourceIp}` : ''}</div></div>
            <div className="row">
              {a.deviceId && <Link to={`/devices/${a.deviceId}`}>View device</Link>}
              {a.status === 'OPEN' ? <button onClick={() => ack.mutate(a.id)}>Acknowledge</button> : <span className="muted small">Ack by {a.acknowledgedBy}</span>}
            </div>
          </div>
          {a.message && <p style={{ margin: '.5rem 0 0' }}>{a.message}</p>}
        </Card>
      ))}
      <Pager page={page} totalPages={alerts.data?.totalPages ?? 0} onChange={setPage} />
    </>
  );
}
