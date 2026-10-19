import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
  const [csrf, setCsrf] = useState('');
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('Checking your session…');
  const [busy, setBusy] = useState(false);
  const [adminUsers, setAdminUsers] = useState([]);
  const [adminMessage, setAdminMessage] = useState('');
  const [newUser, setNewUser] = useState({ email: '', displayName: '', role: 'OWNER', password: '' });

  async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf');
    if (!response.ok) throw new Error('Could not initialize secure session.');
    const data = await response.json();
    setCsrf(data.token);
    return data.token;
  }

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await refreshCsrf();
        const response = await fetch('/api/auth/me');
        if (response.ok) {
          const current = await response.json();
          if (active) { setUser(current); setMessage(''); }
        } else if (active) setMessage('Sign in with your TrustVault account.');
      } catch (error) {
        if (active) setMessage(error.message);
      }
    })();
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (user?.role !== 'ADMINISTRATOR') return;
    let active = true;
    fetch('/api/admin/users')
      .then(async response => {
        if (!response.ok) throw new Error('Could not load accounts.');
        return response.json();
      })
      .then(accounts => { if (active) setAdminUsers(accounts); })
      .catch(error => { if (active) setAdminMessage(error.message); });
    return () => { active = false; };
  }, [user]);

  async function createAccount(event) {
    event.preventDefault();
    setBusy(true); setAdminMessage('');
    try {
      const token = csrf || await refreshCsrf();
      const response = await fetch('/api/admin/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: JSON.stringify(newUser),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error || 'Account creation failed.');
      setNewUser({ email: '', displayName: '', role: 'OWNER', password: '' });
      setAdminMessage(`Created ${result.role.toLowerCase()} account for ${result.email}.`);
      setAdminUsers(current => [result, ...current]);
    } catch (error) { setAdminMessage(error.message); }
    finally { setBusy(false); }
  }

  async function toggleAccount(account) {
    setBusy(true); setAdminMessage('');
    try {
      const token = csrf || await refreshCsrf();
      const response = await fetch(`/api/admin/users/${account.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: JSON.stringify({ active: !account.active }),
      });
      const result = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(result.error || 'Account status update failed.');
      setAdminUsers(current => current.map(item => item.id === result.id ? result : item));
      setAdminMessage(`${result.email} is now ${result.active ? 'active' : 'deactivated'}.`);
    } catch (error) { setAdminMessage(error.message); }
    finally { setBusy(false); }
  }

  async function signIn(event) {
    event.preventDefault();
    setBusy(true); setMessage('');
    try {
      const token = csrf || await refreshCsrf();
      const body = new URLSearchParams({ email, password });
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': token },
        body,
      });
      if (!response.ok) throw new Error('Sign-in failed. Check your email and password.');
      const newToken = await refreshCsrf();
      setCsrf(newToken);
      const me = await fetch('/api/auth/me');
      if (!me.ok) throw new Error('Your session could not be loaded. Please try again.');
      setUser(await me.json());
      setPassword('');
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }

  async function signOut() {
    setBusy(true);
    try {
      const token = csrf || await refreshCsrf();
      const response = await fetch('/api/auth/logout', { method: 'POST', headers: { 'X-CSRF-TOKEN': token } });
      if (!response.ok) throw new Error('Sign-out failed. Please retry.');
      setUser(null);
      setCsrf(await refreshCsrf());
      setMessage('You are signed out.');
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }

  return (
    <main className="shell">
      <section className="card" aria-labelledby="title">
        <div className="mark" aria-hidden="true">TV</div>
        <p className="eyebrow">SECURE DOCUMENT SHARING</p>
        <h1 id="title">TrustVault</h1>
        {user ? <>
          <p className="intro">Signed in as <strong>{user.displayName}</strong>.</p>
          <div className="status"><span className="dot" /> {user.role.toLowerCase().replace('_', ' ')}</div>
          {user.role === 'ADMINISTRATOR' ? <section className="admin-panel" aria-labelledby="accounts-title">
            <h2 id="accounts-title">Account management</h2>
            <p className="note">Create owner and viewer accounts. Passwords are never shown after creation.</p>
            <form onSubmit={createAccount}>
              <label htmlFor="new-name">Display name</label>
              <input id="new-name" value={newUser.displayName} onChange={e => setNewUser({ ...newUser, displayName: e.target.value })} required maxLength="120" />
              <label htmlFor="new-email">Email</label>
              <input id="new-email" type="email" value={newUser.email} onChange={e => setNewUser({ ...newUser, email: e.target.value })} required maxLength="320" />
              <label htmlFor="new-role">Role</label>
              <select id="new-role" value={newUser.role} onChange={e => setNewUser({ ...newUser, role: e.target.value })}>
                <option value="OWNER">Owner</option>
                <option value="VIEWER">Viewer</option>
              </select>
              <label htmlFor="new-password">Temporary password (12–72 characters)</label>
              <input id="new-password" type="password" value={newUser.password} onChange={e => setNewUser({ ...newUser, password: e.target.value })} required minLength="12" maxLength="72" autoComplete="new-password" />
              <button type="submit" disabled={busy}>{busy ? 'Saving…' : 'Create account'}</button>
            </form>
            {adminMessage && <p className="message" role="status">{adminMessage}</p>}
            <h3>Accounts</h3>
            {adminUsers.length === 0 ? <p className="note">No provisioned accounts yet.</p> : <ul className="account-list">
              {adminUsers.map(account => <li key={account.id}>
                <div><strong>{account.displayName}</strong><span>{account.email} · {account.role.toLowerCase()} · {account.active ? 'active' : 'inactive'}</span></div>
                {account.role !== 'ADMINISTRATOR' && <button className="small-button" onClick={() => toggleAccount(account)} disabled={busy}>
                  {account.active ? 'Deactivate' : 'Reactivate'}
                </button>}
              </li>)}
            </ul>}
          </section> : <p className="note">Document sharing is the next MVP feature.</p>}
          <button className="secondary" onClick={signOut} disabled={busy}>Sign out</button>
        </> : <>
          <p className="intro">Sign in to continue to your secure document workspace.</p>
          <form onSubmit={signIn}>
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} required maxLength="320" />
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} required />
            <button type="submit" disabled={busy || !csrf}>{busy ? 'Signing in…' : 'Sign in'}</button>
          </form>
          {message && <p className="message" role="status">{message}</p>}
          <p className="note">Accounts are created by the TrustVault administrator.</p>
        </>}
      </section>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
