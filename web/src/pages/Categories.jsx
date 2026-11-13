import { useEffect, useState } from "react";
import api from "../api";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

export default function Categories() {
  const [cats, setCats] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ name: "", description: "" });
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    api.get("/categories")
      .then((r) => { if (!ignore) { setCats(r.data); setError(""); } })
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
  const shown = cats.filter(
    (c) => !kw || c.name.toLowerCase().includes(kw) || (c.description || "").toLowerCase().includes(kw)
  );

  async function create(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    try {
      await api.post("/categories", {
        name: form.name.trim(),
        description: form.description.trim() || null,
      });
      setOpen(false);
      setToast({ text: "Category created." });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(err.response?.status === 409 ? "This category already exists." : errText(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Categories</h2>
        <button className="btn btn-accent" onClick={() => { setForm({ name: "", description: "" }); setFormError(""); setOpen(true); }}>
          + Add category
        </button>
      </div>

      <input
        type="search"
        inputMode="search"
        enterKeyHint="search"
        className="form-control mb-3"
        placeholder="Search categories"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && shown.length === 0 && <p className="text-secondary">No categories found.</p>}

      {shown.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead><tr><th>Name</th><th>Description</th></tr></thead>
            <tbody>
              {shown.map((c) => (
                <tr key={c.id}><td>{c.name}</td><td>{c.description || "-"}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="d-md-none">
        {shown.map((c) => (
          <div className="product-card" key={c.id}>
            <strong>{c.name}</strong>
            <div className="text-secondary small">{c.description || "No description"}</div>
          </div>
        ))}
      </div>

      {open && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={create}>
                <div className="modal-header">
                  <h5 className="modal-title">Add category</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setOpen(false)} />
                </div>
                <div className="modal-body">
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}
                  <label className="form-label">Name</label>
                  <input className="form-control mb-3" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
                  <label className="form-label">Description (optional)</label>
                  <input className="form-control mb-2" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-light" onClick={() => setOpen(false)}>Cancel</button>
                  <button type="submit" className="btn btn-accent" disabled={saving}>{saving ? "Saving..." : "Create"}</button>
                </div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {toast && (
        <div className="toast-box">
          <span>{toast.text}</span>
          <button className="btn btn-sm btn-outline-light" onClick={() => setToast(null)}>✕</button>
        </div>
      )}
    </div>
  );
}