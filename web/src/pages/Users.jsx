import { useEffect, useState } from "react";
import api from "../api";
import ResetPasswordModal from "../components/ResetPasswordModal";
import { useConfirm } from "../confirmContext";

const emptyForm = { name: "", email: "", password: "", role: "STAFF" };

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

function ActiveBadge({ active }) {
  return <span className={"badge " + (active ? "bg-success" : "bg-secondary")}>{active ? "Active" : "Inactive"}</span>;
}

export default function Users() {
  const confirm = useConfirm();
  const [resetUser, setResetUser] = useState(null);
  const me = JSON.parse(localStorage.getItem("user") || "{}");
  const [users, setUsers] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    api.get("/admin/users")
      .then((r) => { if (!ignore) { setUsers(r.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [refreshKey]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 5000);
    return () => clearTimeout(t);
  }, [toast]);

  const kw = keyword.trim().toLowerCase();
  const shown = users.filter(
    (u) => !kw || u.name.toLowerCase().includes(kw) || u.email.toLowerCase().includes(kw) || u.role.toLowerCase().includes(kw)
  );

  const isMe = (u) => u.email.toLowerCase() === (me.email || "").toLowerCase();

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function create(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    try {
      await api.post("/admin/users", {
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        role: form.role,
      });
      setOpen(false);
      setForm(emptyForm);
      setToast({ text: "User created." });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(err.response?.status === 409 ? "This email already exists." : errText(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggle(u) {
    const action = u.active ? "deactivate" : "activate";
        if (u.active) {
      const ok = await confirm({
        title: "Deactivate user",
        message: `Deactivate ${u.name}? They will be blocked from signing in right away.`,
        confirmText: "Deactivate",
        danger: true,
      });
      if (!ok) return;
    }
    setError("");
    try {
      await api.patch(`/admin/users/${u.id}/${action}`);
      setToast({ text: `${u.name} ${u.active ? "deactivated" : "activated and unlocked"}.` });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(errText(err));
    }
  }

  function Action({ u }) {
    if (isMe(u)) return <span className="text-secondary small">You</span>;
    return (
      <div className="d-flex gap-2 justify-content-end flex-wrap">
        <button className="btn btn-sm btn-outline-light" onClick={() => setResetUser(u)}>Reset password</button>
        <button
          className={"btn btn-sm " + (u.active ? "btn-outline-danger" : "btn-outline-light")}
          onClick={() => toggle(u)}
        >
          {u.active ? "Deactivate" : "Activate"}
        </button>
      </div>
    );
  }
  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Users</h2>
        <button className="btn btn-accent" onClick={() => { setForm(emptyForm); setFormError(""); setOpen(true); }}>
          + Add user
        </button>
      </div>

      <input
        type="search"
        inputMode="search"
        enterKeyHint="search"
        className="form-control mb-3"
        placeholder="Search by name, email or role"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && shown.length === 0 && <p className="text-secondary">No users found.</p>}

      {shown.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {shown.map((u) => (
                <tr key={u.id}>
                  <td>{u.name}</td>
                  <td>{u.email}</td>
                  <td>{u.role}</td>
                  <td><ActiveBadge active={u.active} /></td>
                  <td className="text-end"><Action u={u} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="d-md-none">
        {shown.map((u) => (
          <div className="product-card" key={u.id}>
            <div className="d-flex justify-content-between">
              <strong>{u.name}</strong>
              <ActiveBadge active={u.active} />
            </div>
            <div className="text-secondary small">{u.email}</div>
            <div className="d-flex justify-content-between align-items-center mt-2">
              <span className="app-role">{u.role}</span>
              <Action u={u} />
            </div>
          </div>
        ))}
      </div>

      {open && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={create}>
                <div className="modal-header">
                  <h5 className="modal-title">Add user</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setOpen(false)} />
                </div>
                <div className="modal-body">
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}

                  <label className="form-label">Name</label>
                  <input className="form-control mb-3" name="name" value={form.name} onChange={change} required />

                  <label className="form-label">Email</label>
                  <input type="email" className="form-control mb-3" name="email" value={form.email} onChange={change} required />

                  <label className="form-label">Password (min 8 characters)</label>
                  <input type="password" minLength={8} autoComplete="new-password" className="form-control mb-3" name="password" value={form.password} onChange={change} required />

                  <label className="form-label">Role</label>
                  <select className="form-select form-control mb-2" name="role" value={form.role} onChange={change}>
                    <option value="STAFF">STAFF</option>
                    <option value="ADMIN">ADMIN</option>
                  </select>
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-light" onClick={() => setOpen(false)}>Cancel</button>
                  <button type="submit" className="btn btn-accent" disabled={saving}>
                    {saving ? "Saving..." : "Create user"}
                  </button>
                </div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      <ResetPasswordModal user={resetUser} onClose={() => setResetUser(null)} onDone={(text) => setToast({ text })} />

      {toast && (
        <div className="toast-box">
          <span>{toast.text}</span>
          <button className="btn btn-sm btn-outline-light" onClick={() => setToast(null)}>✕</button>
        </div>
      )}
    </div>
  );
}