import { http } from './client';
import type * as T from './types';

const get = <R,>(url: string, params?: object) => http.get<R>(url, { params }).then((r) => r.data);
const post = <R,>(url: string, body?: unknown) => http.post<R>(url, body).then((r) => r.data);
const put = <R,>(url: string, body?: unknown) => http.put<R>(url, body).then((r) => r.data);
const patch = <R,>(url: string, body?: unknown) => http.patch<R>(url, body).then((r) => r.data);
const del = (url: string) => http.delete(url).then(() => undefined);

export const api = {
  setupStatus: () => get<{ setupRequired: boolean }>('/setup/status'),
  setupRoot: (b: { username: string; displayName: string; email?: string; password: string }) => post<T.User>('/setup/root', b),
  login: (username: string, password: string) => post<T.TokenResponse>('/auth/login', { username, password }),
  logout: (refreshToken: string) => post<void>('/auth/logout', { refreshToken }),
  me: () => get<T.User>('/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) => post<void>('/auth/change-password', { currentPassword, newPassword }),

  dashboard: () => get<T.Dashboard>('/dashboard'),
  systemStatus: () => get<T.SystemStatus>('/system/status'),

  devices: (p?: { status?: string; trusted?: boolean; q?: string }) => get<T.Device[]>('/devices', p),
  device: (id: string) => get<T.Device>(`/devices/${id}`),
  updateDevice: (id: string, b: Partial<Pick<T.Device, 'label' | 'deviceType' | 'trusted'>> & { parentId?: string; clearParent?: boolean }) =>
    patch<T.Device>(`/devices/${id}`, b),
  forgetDevice: (id: string) => del(`/devices/${id}`),
  deviceUsage: (id: string, from: string, to: string) => get<T.DayUsage[]>(`/devices/${id}/usage`, { from, to }),

  usageDaily: (date?: string) => get<T.DeviceUsage[]>('/usage/daily', { date }),
  usageTotals: (from: string, to: string) => get<T.DayUsage[]>('/usage/totals', { from, to }),

  activity: (p: { deviceId?: string; date?: string; domain?: string; page?: number; size?: number }) => get<T.Page<T.Activity>>('/activity', p),
  topDomains: (p: { deviceId?: string; date?: string; limit?: number }) => get<T.TopDomain[]>('/activity/top-domains', p),

  alerts: (p: { status?: string; severity?: string; type?: string; page?: number; size?: number }) => get<T.Page<T.Alert>>('/alerts', p),
  openAlertCount: () => get<{ open: number }>('/alerts/open-count'),
  ackAlert: (id: string) => post<T.Alert>(`/alerts/${id}/acknowledge`),
  ackAll: () => post<{ acknowledged: number }>('/alerts/acknowledge-all'),

  network: () => get<T.NetworkSettingsView>('/settings/network'),
  saveNetwork: (b: T.NetworkSettings) => put<T.NetworkSettingsView>('/settings/network', b),
  scanNow: () => post<void>('/settings/network/scan'),
  dnsCollector: () => get<T.DnsCollectorSettings>('/settings/dns-collector'),
  saveDnsCollector: (b: T.DnsCollectorSettings) => put<T.DnsCollectorSettings>('/settings/dns-collector', b),
  testDnsCollector: (b: T.DnsCollectorSettings) => post<T.TestResult>('/settings/dns-collector/test', b),
  alerting: () => get<T.AlertingSettings>('/settings/alerting'),
  saveAlerting: (b: T.AlertingSettings) => put<T.AlertingSettings>('/settings/alerting', b),
  testTelegram: (b: T.AlertingSettings) => post<T.TestResult>('/settings/alerting/test-telegram', b),
  ingest: () => get<{ keyConfigured: boolean }>('/settings/ingest'),
  rotateIngestKey: () => post<{ apiKey: string }>('/settings/ingest/rotate-key'),

  users: () => get<T.User[]>('/users'),
  createUser: (b: { username: string; displayName: string; email?: string; password: string }) => post<T.User>('/users', b),
  updateUser: (id: string, b: { displayName?: string; email?: string; enabled?: boolean }) => patch<T.User>(`/users/${id}`, b),
  resetPassword: (id: string, newPassword: string) => post<void>(`/users/${id}/reset-password`, { newPassword }),
  deleteUser: (id: string) => del(`/users/${id}`),
};
