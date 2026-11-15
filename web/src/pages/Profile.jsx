import { useEffect, useRef, useState } from "react";
import api from "../api";
import Avatar from "../components/Avatar";
import { resizeToDataUrl } from "../photo";

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

  const [hasPhoto, setHasPhoto] = useState(!!localStorage.getItem("photo"));
  const [photoMsg, setPhotoMsg] = useState({ text: "", error: false });
  const [photoBusy, setPhotoBusy] = useState(false);
  const galleryRef = useRef(null);
  const cameraRef = useRef(null);

  useEffect(() => {
    api.get("/auth/me").then((r) => setMe(r.data)).catch(() => {});
    api.get("/account/photo")
      .then((r) => {
        if (r.data && r.data.photo) {
          localStorage.setItem("photo", r.data.photo);
          setHasPhoto(true);
        } else {
          localStorage.removeItem("photo");
          setHasPhoto(false);
        }
        window.dispatchEvent(new Event("photo-changed"));
      })
      .catch(() => {});
  }, []);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function pick(e) {
    const file = e.target.files && e.target.files[0];
    e.target.value = "";
    if (!file) return;
    setPhotoMsg({ text: "", error: false });
    setPhotoBusy(true);
    try {
      const dataUrl = await resizeToDataUrl(file);
      await api.put("/account/photo", { photo: dataUrl });
      localStorage.setItem("photo", dataUrl);
      setHasPhoto(true);
      window.dispatchEvent(new Event("photo-changed"));
      setPhotoMsg({ text: "Profile photo updated.", error: false });
    } catch (err) {
      setPhotoMsg({ text: err.isAxiosError ? errText(err) : err.message, error: true });
    } finally {
      setPhotoBusy(false);
    }
  }

  async function removePhoto() {
    setPhotoMsg({ text: "", error: false });
    setPhotoBusy(true);
    try {
      await api.delete("/account/photo");
      localStorage.removeItem("photo");
      setHasPhoto(false);
      window.dispatchEvent(new Event("photo-changed"));
      setPhotoMsg({ text: "Profile photo removed.", error: false });
    } catch (err) {
      setPhotoMsg({ text: errText(err), error: true });
    } finally {
      setPhotoBusy(false);
    }
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
        <div className="d-flex align-items-center gap-3">
          <Avatar size={84} />
          <div>
            <div className="fw-bold">{me.name}</div>
            <div className="text-secondary small">{me.email}</div>
            <span className="app-role">{me.role}</span>
          </div>
        </div>

        <div className="d-flex flex-wrap gap-2 mt-3">
          <button type="button" className="btn btn-sm btn-outline-light" disabled={photoBusy} onClick={() => galleryRef.current.click()}>
            Choose from gallery
          </button>
          <button type="button" className="btn btn-sm btn-outline-light" disabled={photoBusy} onClick={() => cameraRef.current.click()}>
            Take a photo
          </button>
          {hasPhoto && (
            <button type="button" className="btn btn-sm btn-outline-danger" disabled={photoBusy} onClick={removePhoto}>
              Remove photo
            </button>
          )}
        </div>
        {photoBusy && <div className="text-secondary small mt-2">Saving photo...</div>}
        {photoMsg.text && (
          <div className={"alert py-2 mt-3 mb-0 " + (photoMsg.error ? "alert-danger" : "alert-success")}>{photoMsg.text}</div>
        )}
        <input ref={galleryRef} type="file" accept="image/*" hidden onChange={pick} />
        <input ref={cameraRef} type="file" accept="image/*" capture="user" hidden onChange={pick} />
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