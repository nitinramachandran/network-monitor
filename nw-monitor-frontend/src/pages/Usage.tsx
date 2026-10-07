import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/api';
import { Card, Loading } from '../components/ui';
import { bytesToMb, fmtMb, isoDay } from '../format';

export default function Usage() {
  const [day, setDay] = useState(isoDay());
  const rows = useQuery({ queryKey: ['usage', day], queryFn: () => api.usageDaily(day) });
  const total = (rows.data ?? []).reduce((s, r) => s + r.bytesTotal, 0);
  const max = Math.max(1, ...(rows.data ?? []).map((r) => r.bytesTotal));

  return (
    <>
      <div className="between"><h1>Data usage</h1><input type="date" value={day} max={isoDay()} onChange={(e) => setDay(e.target.value)} /></div>
      <Card title={`Total: ${fmtMb(bytesToMb(total))}`}>
        {!rows.data ? <Loading /> : rows.data.length === 0 ? <p className="muted">No usage recorded for this day. Traffic counters arrive from a collector agent (see Settings → Agents).</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Device</th><th>IP</th><th>Download</th><th>Upload</th><th>Total</th><th style={{ width: '25%' }} /></tr></thead>
            <tbody>{rows.data.map((r) => (
              <tr key={r.deviceId}>
                <td><Link to={`/devices/${r.deviceId}`}>{r.deviceName}</Link></td><td className="mono">{r.ip ?? '—'}</td>
                <td>{fmtMb(bytesToMb(r.bytesDown))}</td><td>{fmtMb(bytesToMb(r.bytesUp))}</td><td><b>{fmtMb(r.megabytes)}</b></td>
                <td><div className="bar"><i style={{ width: `${(r.bytesTotal / max) * 100}%` }} /></div></td>
              </tr>))}</tbody>
          </table></div>
        )}
      </Card>
    </>
  );
}
