import { useQuery } from '@tanstack/react-query';
import { Navigate, Route, Routes } from 'react-router-dom';
import { api } from './api/api';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import { Loading } from './components/ui';
import Account from './pages/Account';
import Activity from './pages/Activity';
import Alerts from './pages/Alerts';
import Dashboard from './pages/Dashboard';
import DeviceDetail from './pages/DeviceDetail';
import Devices from './pages/Devices';
import Health from './pages/Health';
import Login from './pages/Login';
import Settings from './pages/Settings';
import Setup from './pages/Setup';
import Usage from './pages/Usage';
import Users from './pages/Users';

export default function App() {
  const { user, loading, isRoot } = useAuth();
  const setup = useQuery({ queryKey: ['setup'], queryFn: api.setupStatus, enabled: !user });

  if (loading || (!user && setup.isLoading)) return <div className="center-screen"><Loading /></div>;
  if (!user) {
    return setup.data?.setupRequired ? <Setup onDone={() => void setup.refetch()} /> : <Login />;
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="devices" element={<Devices />} />
        <Route path="devices/:id" element={<DeviceDetail />} />
        <Route path="usage" element={<Usage />} />
        <Route path="activity" element={<Activity />} />
        <Route path="alerts" element={<Alerts />} />
        <Route path="health" element={<Health />} />
        <Route path="settings" element={<Settings />} />
        <Route path="account" element={<Account />} />
        {isRoot && <Route path="users" element={<Users />} />}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
