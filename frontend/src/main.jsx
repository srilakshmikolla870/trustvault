import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

const iconPaths = {
  grid: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
  users: <><path d="M16 20v-1.5a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20" /><circle cx="9.5" cy="7" r="4" /><path d="M20.5 20v-1.5a4 4 0 0 0-3-3.87M16.5 3.25a4 4 0 0 1 0 7.5" /></>,
  shield: <><path d="M12 22s8-4 8-11V5l-8-3-8 3v6c0 7 8 11 8 11Z" /><path d="m9 12 2 2 4-4" /></>,
  lock: <><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 1 1 8 0v3" /><path d="M12 14v3" /></>,
  plus: <><path d="M12 5v14M5 12h14" /></>,
  search: <><circle cx="10.8" cy="10.8" r="6.8" /><path d="m16 16 4.5 4.5" /></>,
  logout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" /></>,
  eye: <><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
  eyeOff: <><path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8" /><path d="M9.9 5.2A10.8 10.8 0 0 1 12 5c6.5 0 10 7 10 7a15.8 15.8 0 0 1-3.1 4.1M6.2 6.2C3.4 8.1 2 12 2 12s3.5 7 10 7a10.9 10.9 0 0 0 3-.4" /></>,
  close: <><path d="m18 6-12 12M6 6l12 12" /></>,
  arrow: <><path d="M5 12h14M13 6l6 6-6 6" /></>,
};

function Icon({ name, size = 18, className = '' }) {
  return <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{iconPaths[name]}</svg>;
}

function initials(name = '') {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'TV';
}

function App() {
  const [csrf, setCsrf] = useState('');
  const [user, setUser] = useState(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [message, setMessage] = useState('Checking your session…');
  const [busy, setBusy] = useState(false);
  const [adminUsers, setAdminUsers] = useState([]);
  const [adminMessage, setAdminMessage] = useState('');
  const [adminLoading, setAdminLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
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
    setAdminLoading(true);
    fetch('/api/admin/users')
      .then(async response => {
        if (!response.ok) throw new Error('Could not load accounts. Try refreshing the page.');
        return response.json();
      })
      .then(accounts => { if (active) setAdminUsers(accounts); })
      .catch(error => { if (active) setAdminMessage(error.message); })
      .finally(() => { if (active) setAdminLoading(false); });
    return () => { active = false; };
  }, [user]);

  const filteredUsers = useMemo(() => adminUsers.filter(account => {
    const search = searchTerm.trim().toLowerCase();
    const matchesSearch = !search || `${account.displayName} ${account.email}`.toLowerCase().includes(search);
    const matchesRole = roleFilter === 'ALL' || account.role === roleFilter;
    return matchesSearch && matchesRole;
  }), [adminUsers, roleFilter, searchTerm]);

  const accountCounts = useMemo(() => ({
    total: adminUsers.filter(account => account.role !== 'ADMINISTRATOR').length,
    owners: adminUsers.filter(account => account.role === 'OWNER').length,
    viewers: adminUsers.filter(account => account.role === 'VIEWER').length,
    active: adminUsers.filter(account => account.role !== 'ADMINISTRATOR' && account.active).length,
  }), [adminUsers]);

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
      setCreateOpen(false);
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
      setAdminUsers([]);
      setCsrf(await refreshCsrf());
      setMessage('You are signed out.');
    } catch (error) { setMessage(error.message); }
    finally { setBusy(false); }
  }

  if (!user) {
    return <main className="login-page">
      <section className="login-story" aria-label="About TrustVault">
        <div className="story-top"><a className="brand brand-light" href="#home" aria-label="TrustVault home"><span className="brand-mark"><Icon name="shield" size={21} /></span><span>trustvault<span className="brand-period">.</span></span></a><span className="story-label">PRIVATE BY DESIGN</span></div>
        <div className="story-copy">
          <div className="story-kicker"><span className="kicker-line" /> YOUR DOCUMENTS, IN TRUST</div>
          <h1>A safer place<br />for what matters.</h1>
          <p>Keep your team’s documents organized with access that starts with the right people.</p>
          <div className="story-assurances">
            <div className="assurance"><span className="assurance-icon"><Icon name="lock" /></span><div><strong>Private workspace</strong><span>Built for thoughtful access</span></div></div>
            <div className="assurance"><span className="assurance-icon"><Icon name="users" /></span><div><strong>People first</strong><span>Accounts managed by your team</span></div></div>
          </div>
        </div>
        <div className="story-bottom"><span>TRUSTVAULT · SECURE DOCUMENT SHARING</span><span>01 — 02</span></div>
        <div className="story-orbit orbit-one" /><div className="story-orbit orbit-two" />
      </section>
      <section className="login-panel">
        <div className="login-mobile-brand"><a className="brand" href="#home"><span className="brand-mark"><Icon name="shield" size={21} /></span><span>trustvault<span className="brand-period">.</span></span></a></div>
        <div className="login-form-wrap">
          <div className="login-heading"><div className="secure-tag"><Icon name="lock" size={14} /> SECURE SIGN IN</div><h2>Welcome back</h2><p>Sign in to access your workspace.</p></div>
          <form className="auth-form" onSubmit={signIn}>
            <div className="field"><label htmlFor="email">Email address</label><input id="email" type="email" autoComplete="username" placeholder="you@company.com" value={email} onChange={e => setEmail(e.target.value)} required maxLength="320" /></div>
            <div className="field"><div className="field-label-row"><label htmlFor="password">Password</label></div><div className="password-wrap"><input id="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password" placeholder="Enter your password" value={password} onChange={e => setPassword(e.target.value)} required /><button className="password-toggle" type="button" aria-label={showPassword ? 'Hide password' : 'Show password'} aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}><Icon name={showPassword ? 'eyeOff' : 'eye'} size={18} /></button></div></div>
            {message && <p className="form-message" role="status">{message}</p>}
            <button className="primary-button sign-in-button" type="submit" disabled={busy || !csrf}>{busy ? <><span className="spinner" /> Signing in…</> : <>Sign in <Icon name="arrow" size={17} /></>}</button>
          </form>
          <div className="login-help"><span className="help-icon"><Icon name="shield" size={16} /></span><p>Access is managed by your TrustVault administrator.<br /><span>Need an account? Ask your administrator to invite you.</span></p></div>
        </div>
        <div className="login-foot"><span>© 2026 TRUSTVAULT</span><span>YOUR WORKSPACE IS WAITING</span></div>
      </section>
    </main>;
  }

  const isAdmin = user.role === 'ADMINISTRATOR';
  const roleName = user.role.toLowerCase().replace('_', ' ');

  return <div className="app-layout">
    <aside className="sidebar">
      <a className="brand brand-light sidebar-brand" href="#overview"><span className="brand-mark"><Icon name="shield" size={21} /></span><span>trustvault<span className="brand-period">.</span></span></a>
      <div className="workspace-switch"><div className="workspace-avatar"><Icon name="shield" size={16} /></div><div><span className="workspace-overline">WORKSPACE</span><strong>My organization</strong></div><span className="workspace-chevron">⌄</span></div>
      <div className="nav-section-label">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation"><a className="nav-link active" href="#overview"><Icon name="grid" /><span>Overview</span><span className="nav-indicator" /></a>{isAdmin && <a className="nav-link" href="#accounts"><Icon name="users" /><span>Accounts</span><span className="nav-count">{accountCounts.total}</span></a>}</nav>
      <div className="sidebar-note"><span className="note-mark"><Icon name="lock" size={15} /></span><strong>Access matters</strong><p>Only authorized accounts can enter this workspace.</p></div>
      <div className="sidebar-bottom"><div className="online-dot" /> <span>Workspace available</span><button type="button" className="sidebar-logout" onClick={signOut} disabled={busy} aria-label="Sign out"><Icon name="logout" size={17} /></button></div>
    </aside>

    <main className="main-area" id="overview">
      <header className="topbar"><div className="breadcrumb"><span>Workspace</span><span className="crumb-slash">/</span><strong>Overview</strong></div><div className="topbar-actions"><div className="topbar-date"><span className="date-dot" /> PRIVATE WORKSPACE</div><div className="topbar-divider" /><div className="user-menu"><div className="avatar">{initials(user.displayName)}</div><div className="user-menu-text"><strong>{user.displayName}</strong><span>{roleName}</span></div><button type="button" className="topbar-signout" onClick={signOut} disabled={busy}><Icon name="logout" size={16} /><span>Sign out</span></button></div></div></header>

      <div className="content-wrap">
        <section className="welcome-row"><div><div className="page-eyebrow"><span className="eyebrow-line" /> TRUSTVAULT WORKSPACE</div><h1>{isAdmin ? 'Good to see you,' : 'Welcome to your workspace,'}<br /><span>{user.displayName.split(' ')[0]}.</span></h1><p>{isAdmin ? 'A clear view of the people who belong here.' : 'Your account is ready. Your document workspace is being prepared.'}</p></div><div className="welcome-art" aria-hidden="true"><div className="art-ring ring-a" /><div className="art-ring ring-b" /><div className="art-shield"><Icon name="shield" size={36} /></div><span className="art-spark spark-a" /><span className="art-spark spark-b" /></div></section>

        {isAdmin ? <>
          <section className="metrics-grid" aria-label="Account summary">
            <article className="metric-card metric-primary"><div className="metric-top"><span>Total team accounts</span><span className="metric-icon"><Icon name="users" /></span></div><div className="metric-value">{adminLoading ? '—' : accountCounts.total.toString().padStart(2, '0')}</div><div className="metric-foot"><span className="metric-pip" /> Owners and viewers</div></article>
            <article className="metric-card"><div className="metric-top"><span>Owners</span><span className="metric-icon metric-icon-blue"><Icon name="shield" /></span></div><div className="metric-value">{adminLoading ? '—' : accountCounts.owners.toString().padStart(2, '0')}</div><div className="metric-foot">Manage their own workspace</div></article>
            <article className="metric-card"><div className="metric-top"><span>Viewers</span><span className="metric-icon metric-icon-lilac"><Icon name="eye" /></span></div><div className="metric-value">{adminLoading ? '—' : accountCounts.viewers.toString().padStart(2, '0')}</div><div className="metric-foot">Access is individually granted</div></article>
            <article className="metric-card"><div className="metric-top"><span>Active accounts</span><span className="metric-icon metric-icon-green"><Icon name="lock" /></span></div><div className="metric-value">{adminLoading ? '—' : accountCounts.active.toString().padStart(2, '0')}</div><div className="metric-foot"><span className="metric-pip" /> Ready to sign in</div></article>
          </section>

          <section className="accounts-card" id="accounts" aria-labelledby="accounts-title">
            <div className="section-heading"><div><div className="section-kicker">PEOPLE & ACCESS</div><h2 id="accounts-title">Account directory</h2><p>Manage who can sign in to TrustVault.</p></div><button className="primary-button create-button" type="button" onClick={() => { setAdminMessage(''); setCreateOpen(true); }}><Icon name="plus" size={17} /> Create account</button></div>
            <div className="directory-toolbar"><label className="search-field"><Icon name="search" size={17} /><input type="search" aria-label="Search accounts" placeholder="Search by name or email" value={searchTerm} onChange={e => setSearchTerm(e.target.value)} /></label><label className="filter-select"><span>Role</span><select value={roleFilter} onChange={e => setRoleFilter(e.target.value)} aria-label="Filter by role"><option value="ALL">All roles</option><option value="ADMINISTRATOR">Administrator</option><option value="OWNER">Owner</option><option value="VIEWER">Viewer</option></select></label></div>
            {adminMessage && <p className={`admin-feedback ${adminMessage.startsWith('Created') || adminMessage.includes('now active') ? 'success' : ''}`} role="status">{adminMessage}</p>}
            <div className="table-scroll"><table className="accounts-table"><thead><tr><th>ACCOUNT</th><th>ROLE</th><th>STATUS</th><th className="actions-heading">ACTIONS</th></tr></thead><tbody>
              {adminLoading ? <tr><td colSpan="4" className="table-message">Loading accounts…</td></tr> : filteredUsers.length === 0 ? <tr><td colSpan="4" className="table-message">{adminUsers.length === 0 ? 'No accounts yet. Create an owner or viewer account to get started.' : 'No accounts match your search.'}</td></tr> : filteredUsers.map(account => <tr key={account.id}><td><div className="account-identity"><div className={`account-avatar ${account.role === 'ADMINISTRATOR' ? 'avatar-admin' : account.role === 'VIEWER' ? 'avatar-viewer' : ''}`}>{initials(account.displayName)}</div><div className="account-name"><strong>{account.displayName}</strong><span>{account.email}</span></div></div></td><td><span className={`role-pill role-${account.role.toLowerCase()}`}>{account.role === 'ADMINISTRATOR' ? 'Administrator' : account.role === 'OWNER' ? 'Owner' : 'Viewer'}</span></td><td><span className={`status-pill ${account.active ? 'status-active' : 'status-inactive'}`}><span />{account.active ? 'Active' : 'Inactive'}</span></td><td className="actions-cell">{account.role !== 'ADMINISTRATOR' ? <button type="button" className={`row-action ${account.active ? 'deactivate-action' : 'activate-action'}`} onClick={() => toggleAccount(account)} disabled={busy}>{account.active ? 'Deactivate' : 'Reactivate'}</button> : <span className="self-label">You</span>}</td></tr>)}
            </tbody></table></div>
            <div className="directory-footer"><span>Showing <strong>{filteredUsers.length}</strong> of <strong>{adminUsers.length}</strong> accounts</span><span className="directory-secure"><Icon name="lock" size={13} /> Account controls are server-authorized</span></div>
          </section>
        </> : <section className="member-welcome"><div className="member-icon"><Icon name="shield" size={27} /></div><div><div className="section-kicker">YOUR ACCOUNT</div><h2>You’re signed in and ready.</h2><p>Your <strong>{roleName}</strong> account is active. Document browsing and sharing will appear here when those features are available.</p><div className="member-status"><span className="status-pill status-active"><span /> Account active</span><span className="member-email">{user.email}</span></div></div></section>}

        <footer className="page-footer"><span>TRUSTVAULT <span className="footer-dot">·</span> PRIVATE DOCUMENT SHARING</span><span>SESSION-PROTECTED WORKSPACE</span></footer>
      </div>
    </main>

    {createOpen && <div className="modal-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) setCreateOpen(false); }}><section className="create-modal" role="dialog" aria-modal="true" aria-labelledby="create-title"><div className="modal-top"><div className="modal-icon"><Icon name="users" size={20} /></div><button className="modal-close" type="button" aria-label="Close dialog" onClick={() => setCreateOpen(false)}><Icon name="close" /></button></div><div className="modal-heading"><div className="section-kicker">NEW TEAM MEMBER</div><h2 id="create-title">Create an account</h2><p>Add an owner or viewer to your TrustVault workspace.</p></div><form className="create-form" onSubmit={createAccount}><div className="field"><label htmlFor="new-name">Full name</label><input id="new-name" value={newUser.displayName} onChange={e => setNewUser({ ...newUser, displayName: e.target.value })} required maxLength="120" placeholder="e.g. Alex Morgan" autoFocus /></div><div className="field"><label htmlFor="new-email">Email address</label><input id="new-email" type="email" value={newUser.email} onChange={e => setNewUser({ ...newUser, email: e.target.value })} required maxLength="320" placeholder="alex@company.com" /></div><div className="field"><label htmlFor="new-role">Account role</label><select id="new-role" value={newUser.role} onChange={e => setNewUser({ ...newUser, role: e.target.value })}><option value="OWNER">Owner — manages their own documents</option><option value="VIEWER">Viewer — receives granted access</option></select></div><div className="field"><label htmlFor="new-password">Temporary password</label><input id="new-password" type="password" value={newUser.password} onChange={e => setNewUser({ ...newUser, password: e.target.value })} required minLength="12" maxLength="72" autoComplete="new-password" placeholder="At least 12 characters" /><span className="field-hint">12–72 characters. Share it securely with the new member.</span></div>{adminMessage && <p className="form-message" role="alert">{adminMessage}</p>}<div className="modal-actions"><button className="cancel-button" type="button" onClick={() => setCreateOpen(false)}>Cancel</button><button className="primary-button" type="submit" disabled={busy}>{busy ? <><span className="spinner" /> Creating…</> : <><Icon name="plus" size={17} /> Create account</>}</button></div></form><p className="modal-security"><Icon name="lock" size={14} /> Passwords are securely hashed and never shown again.</p></section></div>}
  </div>;
}

createRoot(document.getElementById('root')).render(<App />);
