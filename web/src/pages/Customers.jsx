import { useEffect, useState } from "react";
import api from "../api";
import useDebounce from "../useDebounce";

const emptyForm = { name: "", email: "", phone: "", address: "" };

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

export default function Customers() {
  const [customers, setCustomers] = useState([]);
  const [keyword, setKeyword] = useState("");
  const query = useDebounce(keyword.trim(), 300);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [modal, setModal] = useState(null); // null | { mode: "add" } | { mode: "edit", id }
  const [form, setForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    api.get("/customers", { params: query ? { keyword: query } : {} })
      .then((res) => { if (!ignore) { setCustomers(res.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [query, refreshKey]);


  function openAdd() {
    setForm(emptyForm);
    setFormError("");
    setModal({ mode: "add" });
  }

  function openEdit(c) {
    setForm({
      name: c.name || "",
      email: c.email || "",
      phone: c.phone || "",
      address: c.address || "",
    });
    setFormError("");
    setModal({ mode: "edit", id: c.id });
  }

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function save(e) {
    e.preventDefault();
    setFormError("");
    setSaving(true);
    const body = {
      name: form.name.trim(),
      email: form.email.trim() || null,
      phone: form.phone.trim() || null,
      address: form.address.trim() || null,
    };
    try {
      if (modal.mode === "add") {
        await api.post("/customers", body);
      } else {
        await api.put(`/customers/${modal.id}`, body);
      }
      setModal(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(errText(err));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Customers</h2>
        <button className="btn btn-accent" onClick={openAdd}>+ Add customer</button>
      </div>

        <input
        className="form-control mb-3"
        placeholder="Search customers"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && customers.length === 0 && (
        <p className="text-secondary">No customers found.</p>
      )}

      {/* Table: tablets and larger */}
      {customers.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Phone</th><th>Address</th><th></th></tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.id}>
                  <td>{c.name}</td>
                  <td>{c.email || "-"}</td>
                  <td>{c.phone || "-"}</td>
                  <td>{c.address || "-"}</td>
                  <td className="text-end">
                    <button className="btn btn-sm btn-outline-light" onClick={() => openEdit(c)}>Edit</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Cards: phones */}
      <div className="d-md-none">
        {customers.map((c) => (
          <div className="product-card" key={c.id}>
            <strong>{c.name}</strong>
            <div className="text-secondary small">{c.email || "No email"}</div>
            <div className="text-secondary small">{c.phone || "No phone"}</div>
            {c.address && <div className="small mt-1">{c.address}</div>}
            <button className="btn btn-sm btn-outline-light w-100 mt-3" onClick={() => openEdit(c)}>Edit</button>
          </div>
        ))}
      </div>

      {/* Add / Edit form */}
      {modal && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={save}>
                <div className="modal-header">
                  <h5 className="modal-title">{modal.mode === "add" ? "Add customer" : "Edit customer"}</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setModal(null)} />
                </div>
                <div className="modal-body">
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}

                  <label className="form-label">Name</label>
                  <input className="form-control mb-3" name="name" maxLength={100} value={form.name} onChange={change} required />

                  <label className="form-label">Email (optional)</label>
                  <input type="email" className="form-control mb-3" name="email" maxLength={100} value={form.email} onChange={change} />

                  <label className="form-label">Phone (optional)</label>
                  <input className="form-control mb-3" name="phone" maxLength={20} value={form.phone} onChange={change} />

                  <label className="form-label">Address (optional)</label>
                  <input className="form-control mb-3" name="address" maxLength={255} value={form.address} onChange={change} />
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-light" onClick={() => setModal(null)}>Cancel</button>
                  <button type="submit" className="btn btn-accent" disabled={saving}>
                    {saving ? "Saving..." : "Save"}
                  </button>
                </div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}
    </div>
  );
}