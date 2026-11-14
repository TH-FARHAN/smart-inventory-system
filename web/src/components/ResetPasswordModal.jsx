import { useState } from "react";
import api from "../api";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.values(d.errors).join(", ");
  return d?.message || "Something went wrong.";
}

export default function ResetPasswordModal({ user, onClose, onDone }) {
  const [pw, setPw] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  if (!user) return null;

  async function submit(e) {
    e.preventDefault();
    setError("");
    if (pw.length < 8) return setError("The password must be at least 8 characters.");
    setSaving(true);
    try {
      await api.patch(`/admin/users/${user.id}/reset-password`, { newPassword: pw });
      setPw("");
      onDone(`Password reset for ${user.name}. Their account is unlocked.`);
      onClose();
    } catch (err) {
      setError(errText(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <div className="modal d-block" tabIndex="-1">
        <div className="modal-dialog">
          <form className="modal-content app-modal" onSubmit={submit}>
            <div className="modal-header">
              <h5 className="modal-title">Reset password</h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose} />
            </div>
            <div className="modal-body">
              {error && <div className="alert alert-danger py-2">{error}</div>}
              <p className="text-secondary">Set a new password for <b>{user.name}</b> ({user.email}). Tell them the new password in person.</p>
              <label className="form-label">New password (min 8 characters)</label>
              <input type={show ? "text" : "password"} autoComplete="new-password" className="form-control mb-2" value={pw} onChange={(e) => setPw(e.target.value)} required />
              <div className="form-check">
                <input id="showreset" type="checkbox" className="form-check-input" checked={show} onChange={(e) => setShow(e.target.checked)} />
                <label htmlFor="showreset" className="form-check-label text-secondary">Show password</label>
              </div>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-outline-light" onClick={onClose}>Cancel</button>
              <button type="submit" className="btn btn-accent" disabled={saving}>{saving ? "Saving..." : "Reset password"}</button>
            </div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show"></div>
    </>
  );
}