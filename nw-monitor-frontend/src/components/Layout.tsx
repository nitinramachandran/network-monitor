import { useQuery } from '@tanstack/react-query';
import { NavLink, Outlet } from 'react-router-dom';
import { api } from '../api/api';
import { useAuth } from '../auth/AuthContext';

export default function Layout() {
  const { user, isRoot, signOut } = useAuth();
  const open = useQuery({ queryKey: ['alerts', 'open-count'], queryFn: api.openAlertCount, refetchInterval: 15_000 });
  const n = open.data?.open ?? 0;

  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">🛰️ Network Monitor</div>
        <NavLink to="/" end>Dashboard</NavLink>
        <NavLink to="/devices">Devices</NavLink>
        <NavLink to="/usage">Data usage</NavLink>
        <NavLink to="/activity">Web activity</NavLink>
        <NavLink to="/alerts">Alerts {n > 0 && <span className="pill-count">{n}</span>}</NavLink>
        <NavLink to="/health">Connectivity</NavLink>
        <NavLink to="/settings">Settings</NavLink>
        {isRoot && <NavLink to="/users">Users</NavLink>}
        <div className="spacer" />
        <div className="who">{user?.displayName} · {user?.role}</div>
        <NavLink to="/account">Account</NavLink>
        <a href="#" onClick={(e) => { e.preventDefault(); void signOut(); }}>Sign out</a>
      </nav>
      <main className="main"><Outlet /></main>
    </div>
  );
}
