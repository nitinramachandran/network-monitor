import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/api';
import { errorMessage } from '../api/client';
import { ALERT_TYPES, AlertingSettings, DnsCollectorSettings, NetworkSettings, Severity, TestResult } from '../api/types';
import { Card, Field, Loading } from '../components/ui';
import { label } from '../format';

type Tab = 'network' | 'activity' | 'alerting' | 'agents';
const TABS: [Tab, string][] = [['network', 'Network access'], ['activity', 'Web activity source'], ['alerting', 'Alerting'], ['agents', 'Collector agents']];

export default function Settings() {
  const [tab, setTab] = useState<Tab>('network');
  return (
    <>
      <h1>Settings</h1>
      <div className="tabs">{TABS.map(([k, t]) => <button key={k} className={tab === k ? 'active' : ''} onClick={() => setTab(k)}>{t}</button>)}</div>
      {tab === 'network' && <NetworkTab />}
      {tab === 'activity' && <ActivityTab />}
      {tab === 'alerting' && <AlertingTab />}
      {tab === 'agents' && <AgentsTab />}
    </>
  );
}

function useSave<T>(fn: (v: T) => Promise<unknown>, key: string) {
  const qc = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: () => void qc.invalidateQueries({ queryKey: [key] }) });
}

function Result({ r, saved, error }: { r?: TestResult; saved?: boolean; error?: unknown }) {
  if (error) return <p className="error">{errorMessage(error)}</p>;
  if (r) return <p className={r.ok === 'true' ? 'hint' : 'error'}>{r.ok === 'true' ? '✅ ' : '❌ '}{r.message}</p>;
  return saved ? <p className="hint">✅ Saved</p> : null;
}

function NetworkTab() {
  const q = useQuery({ queryKey: ['net'], queryFn: api.network });
  const [f, setF] = useState<NetworkSettings | null>(null);
  useEffect(() => { if (q.data) setF(q.data.settings); }, [q.data]);
  const save = useSave(api.saveNetwork, 'net');
  if (!f || !q.data) return <Loading />;
  const num = (k: keyof NetworkSettings) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: Number(e.target.value) });

  return (
    <Card title="How the monitor reaches your network">
      <p className="muted small">The monitor box must be on the same LAN as your devices. Leave subnet blank to auto-detect{q.data.detectedSubnet ? ` (detected: ${q.data.detectedSubnet})` : ''}.</p>
      <Field label="Subnet (CIDR)" hint="e.g. 192.168.1.0/24"><input value={f.subnetCidr} onChange={(e) => setF({ ...f, subnetCidr: e.target.value })} placeholder={q.data.detectedSubnet ?? '192.168.1.0/24'} /></Field>
      <Field label="Router / gateway IP" hint="Used for connectivity checks and to tag the ISP router. Usually 192.168.1.1."><input value={f.gatewayIp} onChange={(e) => setF({ ...f, gatewayIp: e.target.value })} placeholder="192.168.1.1" /></Field>
      <label className="check"><input type="checkbox" checked={f.scanEnabled} onChange={(e) => setF({ ...f, scanEnabled: e.target.checked })} /> Scan the network automatically</label>
      <Field label="Scan interval (seconds)"><input type="number" min={30} value={f.scanIntervalSeconds} onChange={num('scanIntervalSeconds')} /></Field>
      <Field label="Mark a device offline after (seconds)"><input type="number" min={60} value={f.offlineAfterSeconds} onChange={num('offlineAfterSeconds')} /></Field>
      <Field label="Keep web activity for (days)"><input type="number" min={1} value={f.activityRetentionDays} onChange={num('activityRetentionDays')} /></Field>
      <button className="primary" onClick={() => save.mutate(f)} disabled={save.isPending}>Save</button>
      <Result saved={save.isSuccess} error={save.error} />
    </Card>
  );
}

function ActivityTab() {
  const q = useQuery({ queryKey: ['dns'], queryFn: api.dnsCollector });
  const [f, setF] = useState<DnsCollectorSettings | null>(null);
  useEffect(() => { if (q.data) setF(q.data); }, [q.data]);
  const save = useSave(api.saveDnsCollector, 'dns');
  const test = useMutation({ mutationFn: api.testDnsCollector });
  if (!f) return <Loading />;

  return (
    <Card title="Where visited sites come from">
      <p className="muted small">Router firmware from ISPs does not expose browsing data. Run Pi-hole on the monitor box and point your router's DHCP DNS at it; this app then reads its query log per device.</p>
      <Field label="Source"><select value={f.type} onChange={(e) => setF({ ...f, type: e.target.value as DnsCollectorSettings['type'] })}>
        <option value="NONE">Disabled</option><option value="PIHOLE">Pi-hole (v6)</option><option value="SIMULATED">Simulated demo data</option></select></Field>
      {f.type === 'PIHOLE' && <>
        <Field label="Pi-hole URL"><input value={f.url} onChange={(e) => setF({ ...f, url: e.target.value })} placeholder="http://192.168.1.2" /></Field>
        <Field label="Pi-hole password" hint="Stored on the server and never shown again"><input type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} autoComplete="off" /></Field>
      </>}
      {f.type !== 'NONE' && <Field label="Poll interval (seconds)"><input type="number" min={10} value={f.pollSeconds} onChange={(e) => setF({ ...f, pollSeconds: Number(e.target.value) })} /></Field>}
      <div className="row">
        <button className="primary" onClick={() => save.mutate(f)} disabled={save.isPending}>Save</button>
        {f.type !== 'NONE' && <button onClick={() => test.mutate(f)} disabled={test.isPending}>Test connection</button>}
      </div>
      <Result saved={save.isSuccess && !test.data} error={save.error ?? test.error} r={test.data} />
    </Card>
  );
}

function AlertingTab() {
  const q = useQuery({ queryKey: ['alerting'], queryFn: api.alerting });
  const [f, setF] = useState<AlertingSettings | null>(null);
  useEffect(() => { if (q.data) setF(q.data); }, [q.data]);
  const save = useSave(api.saveAlerting, 'alerting');
  const test = useMutation({ mutationFn: api.testTelegram });
  if (!f) return <Loading />;
  const toggle = (t: (typeof ALERT_TYPES)[number]) =>
    setF({ ...f, disabledTypes: f.disabledTypes.includes(t) ? f.disabledTypes.filter((x) => x !== t) : [...f.disabledTypes, t] });

  return (
    <>
      <Card title="Telegram notifications">
        <p className="muted small">Create a bot with @BotFather, send it a message, then enter the bot token and your chat id. Alerts always appear in this app too.</p>
        <label className="check"><input type="checkbox" checked={f.telegramEnabled} onChange={(e) => setF({ ...f, telegramEnabled: e.target.checked })} /> Send alerts to Telegram</label>
        <Field label="Bot token"><input type="password" value={f.telegramBotToken} onChange={(e) => setF({ ...f, telegramBotToken: e.target.value })} autoComplete="off" /></Field>
        <Field label="Chat id"><input value={f.telegramChatId} onChange={(e) => setF({ ...f, telegramChatId: e.target.value })} /></Field>
        <Field label="Notify from severity"><select value={f.minNotifySeverity} onChange={(e) => setF({ ...f, minNotifySeverity: e.target.value as Severity })}>
          <option value="INFO">Info and above</option><option value="WARNING">Warning and above</option><option value="CRITICAL">Critical only</option></select></Field>
        <button onClick={() => test.mutate(f)} disabled={test.isPending}>Send test message</button>
        <Result r={test.data} error={test.error} />
      </Card>
      <Card title="What to alert on">
        {ALERT_TYPES.map((t) => <label key={t} className="check"><input type="checkbox" checked={!f.disabledTypes.includes(t)} onChange={() => toggle(t)} /> {label(t)}</label>)}
        <Field label="Group repeats of the same alert for (minutes)" hint="Repeats inside this window add to one alert instead of notifying again">
          <input type="number" min={1} value={f.cooldownMinutes} onChange={(e) => setF({ ...f, cooldownMinutes: Number(e.target.value) })} /></Field>
        <button className="primary" onClick={() => save.mutate(f)} disabled={save.isPending}>Save</button>
        <Result saved={save.isSuccess} error={save.error} />
      </Card>
    </>
  );
}

function AgentsTab() {
  const q = useQuery({ queryKey: ['ingest'], queryFn: api.ingest });
  const rotate = useMutation({ mutationFn: api.rotateIngestKey, onSuccess: () => void q.refetch() });
  const base = `${location.origin}/api/v1/ingest`;
  return (
    <Card title="Collector agents (traffic counters and intrusion events)">
      <p className="muted small">Agents running on a machine that can see your traffic push data here with an API key: <span className="mono">POST {base}/usage | /dns | /security-events</span> with header <span className="mono">X-API-Key</span>.
        Per-device byte counts and inbound-attempt detection need such an agent (see <span className="mono">collector-agent/</span>).</p>
      <p>API key: {q.data?.keyConfigured ? '✅ configured' : 'not generated yet'}</p>
      <button className="primary" onClick={() => confirm('Generating a new key invalidates the old one. Continue?') && rotate.mutate()}>{q.data?.keyConfigured ? 'Rotate key' : 'Generate key'}</button>
      {rotate.data && <div className="code-block mono" style={{ marginTop: '.8rem' }}>{rotate.data.apiKey}<div className="hint">Copy it now — it is shown only once.</div></div>}
    </Card>
  );
}
