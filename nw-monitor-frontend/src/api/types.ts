export type Role = 'ROOT' | 'ADMIN';
export type Severity = 'INFO' | 'WARNING' | 'CRITICAL';
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED';
export type DeviceStatus = 'ONLINE' | 'OFFLINE';

export const ALERT_TYPES = [
  'NEW_DEVICE', 'DEVICE_IP_CONFLICT', 'EXTERNAL_CONNECTION_ATTEMPT', 'PORT_SCAN', 'LOGIN_BRUTE_FORCE',
  'INTERNET_DOWN', 'INTERNET_RESTORED', 'COLLECTOR_FAILURE', 'BASELINE_ESTABLISHED',
] as const;
export type AlertType = (typeof ALERT_TYPES)[number];

export const DEVICE_TYPES = [
  'ROUTER', 'EXTENDER', 'ACCESS_POINT', 'PHONE', 'TABLET', 'LAPTOP', 'DESKTOP', 'TV', 'CONSOLE', 'IOT', 'PRINTER', 'UNKNOWN',
] as const;
export type DeviceType = (typeof DEVICE_TYPES)[number];

export interface User {
  id: string; username: string; displayName: string; email?: string; role: Role;
  enabled: boolean; createdAt: string; lastLoginAt?: string;
}
export interface TokenResponse { accessToken: string; refreshToken: string; expiresInSeconds: number; user: User }

export interface Device {
  id: string; mac: string; ip?: string; hostname?: string; vendor?: string; label?: string; name: string;
  deviceType: DeviceType; parentId?: string; status: DeviceStatus; trusted: boolean; randomizedMac: boolean;
  firstSeen: string; lastSeen: string;
}
export interface DayUsage { date: string; bytesDown: number; bytesUp: number; bytesTotal: number; megabytes: number }
export interface DeviceUsage { deviceId: string; deviceName: string; ip?: string; bytesDown: number; bytesUp: number; bytesTotal: number; megabytes: number }
export interface Activity { id: number; deviceId: string; deviceName: string; domain: string; queriedAt: string; blocked: boolean }
export interface TopDomain { domain: string; hits: number }
export interface Page<T> { items: T[]; page: number; size: number; totalItems: number; totalPages: number }

export interface Alert {
  id: string; type: AlertType; severity: Severity; title: string; message?: string; deviceId?: string; sourceIp?: string;
  occurrences: number; status: AlertStatus; createdAt: string; lastOccurredAt: string; acknowledgedBy?: string; acknowledgedAt?: string;
}

export interface ConnectivityState { internetUp: boolean; gatewayUp?: boolean; downSince?: string; checkedAt?: string }
export interface SourceStatus { name: string; lastSuccess?: string; lastFailure?: string; lastMessage?: string; consecutiveFailures: number }
export interface SystemStatus { connectivity: ConnectivityState; sources: SourceStatus[] }
export interface Dashboard {
  devicesTotal: number; devicesOnline: number; devicesUntrusted: number; openAlerts: number;
  todayBytesDown: number; todayBytesUp: number; todayMegabytes: number;
  topDevices: DeviceUsage[]; topDomains: TopDomain[]; connectivity: ConnectivityState;
}

export interface NetworkSettings {
  subnetCidr: string; gatewayIp: string; scanEnabled: boolean; scanIntervalSeconds: number;
  offlineAfterSeconds: number; activityRetentionDays: number;
}
export interface NetworkSettingsView { settings: NetworkSettings; detectedSubnet?: string }
export interface DnsCollectorSettings { type: 'NONE' | 'PIHOLE' | 'SIMULATED'; url: string; password: string; pollSeconds: number }
export interface AlertingSettings {
  telegramEnabled: boolean; telegramBotToken: string; telegramChatId: string;
  minNotifySeverity: Severity; disabledTypes: AlertType[]; cooldownMinutes: number;
}
export interface TestResult { ok: string; message: string }
