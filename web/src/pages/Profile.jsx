import { useEffect, useState } from "react";
import api from "../api";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.values(d.errors).join(", ");
  return d?.message || "Something went wrong.";
}

export default function Profile() {
  const saved = JSON.parse(localStorage.getItem("user") || "{}");
  const [me, setMe] = useState(saved);
  const [form, setForm] = useState({ current: "", next: "", confirm: "" });
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get("/auth/me").then((r) => setMe(r.data)).catch(() => {});
  }, []);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function submit(e) {
    e.preventDefault();
    setError("");
    setOk("");
    if (form.next.length < 8) return setError("The new password must be at least 8 characters.");
    if (form.next !== form.confirm) return setError("The new passwords do not match.");
    setSaving(true);
    try {
      await api.post("/account/change-password", {
        currentPassword: form.current,
        newPassword: form.next,
      });
      setForm({ current: "", next: "", confirm: "" });
      setOk("Password changed. Use the new password next time you sign in.");
    } catch (err) {
      setError(errText(err));
    } finally {
      setSaving(false);
    }
  }

  const type = show ? "text" : "password";

  return (
    <div>
      <h2 className="mb-3">Profile</h2>

      <div className="product-card" style={{ maxWidth: 520 }}>
        <div className="text-secondary small">Name</div>
        <div className="mb-2 fw-bold">{me.name}</div>
        <div className="text-secondary small">Email</div>
        <div className="mb-2">{me.email}</div>
        <div className="text-secondary small">Role</div>
        <span className="app-role">{me.role}</span>
      </div>

      <form className="product-card" style={{ maxWidth: 520 }} onSubmit={submit}>
        <h5 className="mb-3">Change password</h5>
        {error && <div className="alert alert-danger py-2">{error}</div>}
        {ok && <div className="alert alert-success py-2">{ok}</div>}

        <label className="form-label">Current password</label>
        <input type={type} name="current" autoComplete="current-password" className="form-control mb-3" value={form.current} onChange={change} required />

        <label className="form-label">New password (min 8 characters)</label>
        <input type={type} name="next" autoComplete="new-password" className="form-control mb-3" value={form.next} onChange={change} required />

        <label className="form-label">Confirm new password</label>
        <input type={type} name="confirm" autoComplete="new-password" className="form-control mb-3" value={form.confirm} onChange={change} required />

        <div className="form-check mb-3">
          <input id="showpw" type="checkbox" className="form-check-input" checked={show} onChange={(e) => setShow(e.target.checked)} />
          <label htmlFor="showpw" className="form-check-label text-secondary">Show passwords while typing</label>
        </div>

        <button className="btn btn-accent w-100" disabled={saving}>
          {saving ? "Saving..." : "Change password"}
        </button>
        <div className="text-secondary small mt-3">
          Passwords are stored hashed, so nobody, including an admin, can read them. An admin can only reset one.
        </div>
      </form>
    </div>
  );
}