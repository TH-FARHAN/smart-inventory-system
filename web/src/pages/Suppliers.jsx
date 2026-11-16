import { useEffect, useState } from "react";
import api from "../api";
import SearchSelect from "../components/SearchSelect";
import { useConfirm } from "../confirmContext";

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.values(d.errors).join(", ");
  return d?.message || "Something went wrong.";
}

function Badge({ status }) {
  const cls = status === "RECEIVED" ? "bg-success" : status === "PENDING" ? "bg-warning text-dark" : "bg-secondary";
  return <span className={"badge " + cls}>{status}</span>;
}

const emptySupplier = { name: "", email: "", phone: "", address: "" };
const emptyRow = { productId: "", quantity: "1", cost: "" };

export default function Suppliers() {
  const confirm = useConfirm();
  const [tab, setTab] = useState("po");
  const [suppliers, setSuppliers] = useState([]);
  const [purchases, setPurchases] = useState([]);
  const [products, setProducts] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  const [view, setView] = useState(null);
  const [supModal, setSupModal] = useState(null);
  const [supForm, setSupForm] = useState(emptySupplier);
  const [poOpen, setPoOpen] = useState(false);
  const [supplierId, setSupplierId] = useState("");
  const [rows, setRows] = useState([emptyRow]);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    Promise.all([api.get("/suppliers"), api.get("/purchases"), api.get("/products")])
      .then(([s, p, pr]) => {
        if (ignore) return;
        setSuppliers(s.data); setPurchases(p.data);
        setProducts(pr.data.filter((x) => x.active !== false)); setError("");
      })
      .catch((err) => { if (!ignore) setError(errText(err)); });
    return () => { ignore = true; };
  }, [refresh]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(t);
  }, [toast]);

  const kw = keyword.trim().toLowerCase();
  const shownSup = suppliers.filter((s) => !kw || s.name.toLowerCase().includes(kw) || (s.phone || "").includes(kw) || (s.email || "").toLowerCase().includes(kw));
  const shownPo = purchases.filter((p) => !kw || String(p.id).includes(kw) || p.supplierName.toLowerCase().includes(kw) || p.status.toLowerCase().includes(kw));

  async function receive(p) {
    const ok = await confirm({ title: "Receive stock", message: `Mark purchase #${p.id} as received? The stock of every item will increase.`, confirmText: "Receive" });
    if (!ok) return;
    try {
      const res = await api.post(`/purchases/${p.id}/receive`);
      setToast({ text: `Purchase #${p.id} received. Stock updated.` });
      if (view && view.id === p.id) setView(res.data);
      setRefresh((k) => k + 1);
    } catch (err) { setError(errText(err)); }
  }

  async function cancelPo(p) {
    const ok = await confirm({ title: "Cancel purchase order", message: `Cancel purchase #${p.id}?`, confirmText: "Cancel order", cancelText: "Keep", danger: true });
    if (!ok) return;
    try {
      const res = await api.post(`/purchases/${p.id}/cancel`);
      setToast({ text: `Purchase #${p.id} cancelled.` });
      if (view && view.id === p.id) setView(res.data);
      setRefresh((k) => k + 1);
    } catch (err) { setError(errText(err)); }
  }

  async function saveSupplier(e) {
    e.preventDefault();
    setFormError(""); setSaving(true);
    const body = { name: supForm.name.trim(), email: supForm.email.trim() || null, phone: supForm.phone.trim() || null, address: supForm.address.trim() || null };
    try {
      if (supModal.id) await api.put(`/suppliers/${supModal.id}`, body);
      else await api.post("/suppliers", body);
      setSupModal(null); setToast({ text: "Supplier saved." }); setRefresh((k) => k + 1);
    } catch (err) {
      setFormError(err.response?.status === 409 ? "This supplier already exists." : errText(err));
    } finally { setSaving(false); }
  }

  function setRow(i, patch) { setRows(rows.map((r, idx) => (idx === i ? { ...r, ...patch } : r))); }

  function pickProduct(i, id) {
    const p = products.find((x) => String(x.id) === String(id));
    setRow(i, { productId: id, cost: rows[i].cost === "" && p ? String(p.purchasePrice) : rows[i].cost });
  }

  // only the chosen supplier's products can be ordered
  const supplierProducts = products.filter((p) => supplierId !== "" && String(p.supplierId) === String(supplierId));

  function pickSupplier(id) { setSupplierId(id); setRows([emptyRow]); }

  const total = rows.reduce((s, r) => s + Number(r.quantity || 0) * Number(r.cost || 0), 0);

  async function createPo(e) {
    e.preventDefault();
    setFormError("");
    if (!supplierId) return setFormError("Select a supplier.");
    const items = rows.filter((r) => r.productId !== "").map((r) => ({ productId: Number(r.productId), quantity: Number(r.quantity), unitCost: Number(r.cost) }));
    if (items.length === 0) return setFormError("Add at least one product.");
    if (items.some((i) => !(i.quantity >= 1) || !(i.unitCost >= 0))) return setFormError("Check the quantity and unit cost.");
    setSaving(true);
    try {
      const res = await api.post("/purchases", { supplierId: Number(supplierId), items });
      setPoOpen(false); setToast({ text: `Purchase #${res.data.id} created. Receive it when the goods arrive.` }); setRefresh((k) => k + 1);
    } catch (err) { setFormError(errText(err)); } finally { setSaving(false); }
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Suppliers</h2>
        {tab === "po"
          ? <button className="btn btn-accent" onClick={() => { setSupplierId(""); setRows([emptyRow]); setFormError(""); setPoOpen(true); }}>+ New purchase order</button>
          : <button className="btn btn-accent" onClick={() => { setSupForm(emptySupplier); setFormError(""); setSupModal({}); }}>+ Add supplier</button>}
      </div>

      <div className="d-flex gap-2 mb-3">
        <button className={"btn btn-sm " + (tab === "po" ? "btn-accent" : "btn-outline-light")} onClick={() => setTab("po")}>Purchase orders</button>
        <button className={"btn btn-sm " + (tab === "sup" ? "btn-accent" : "btn-outline-light")} onClick={() => setTab("sup")}>Suppliers</button>
      </div>

      <input type="search" inputMode="search" enterKeyHint="search" className="form-control mb-3" placeholder={tab === "po" ? "Search by number, supplier or status" : "Search suppliers"} autoComplete="off" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
      {error && <div className="alert alert-danger py-2">{error}</div>}

      {tab === "po" && (
        <>
          {shownPo.length === 0 && <p className="text-secondary">No purchase orders yet.</p>}
          {shownPo.map((p) => (
            <div className="product-card" key={p.id}>
              <div className="d-flex justify-content-between align-items-start">
                <div><strong>#{p.id} · {p.supplierName}</strong><div className="text-secondary small">{when(p.createdAt)} · {p.items.length} item(s)</div></div>
                <div className="text-end"><Badge status={p.status} /><div className="mt-1"><strong>{money(p.totalCost)}</strong></div></div>
              </div>
              <div className="d-flex gap-2 mt-3">
                <button className="btn btn-sm btn-outline-light" onClick={() => setView(p)}>View</button>
                {p.status === "PENDING" && <button className="btn btn-sm btn-accent" onClick={() => receive(p)}>Receive</button>}
                {p.status === "PENDING" && <button className="btn btn-sm btn-outline-danger" onClick={() => cancelPo(p)}>Cancel</button>}
              </div>
            </div>
          ))}
        </>
      )}

      {tab === "sup" && (
        <>
          {shownSup.length === 0 && <p className="text-secondary">No suppliers yet.</p>}
          {shownSup.map((s) => (
            <div className="product-card" key={s.id}>
              <div className="d-flex justify-content-between">
                <div><strong>{s.name}</strong><div className="text-secondary small">{s.phone || "No phone"} · {s.email || "No email"}</div>{s.address && <div className="small">{s.address}</div>}</div>
                <div><button className="btn btn-sm btn-outline-light" onClick={() => { setSupForm({ name: s.name, email: s.email || "", phone: s.phone || "", address: s.address || "" }); setFormError(""); setSupModal(s); }}>Edit</button></div>
              </div>
            </div>
          ))}
        </>
      )}

      {view && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <div className="modal-content app-modal">
                <div className="modal-header"><h5 className="modal-title">Purchase #{view.id}</h5><button type="button" className="btn-close btn-close-white" onClick={() => setView(null)} /></div>
                <div className="modal-body">
                  <div className="d-flex justify-content-between mb-1"><span>{view.supplierName}</span><Badge status={view.status} /></div>
                  <div className="text-secondary small mb-3">Created {when(view.createdAt)} by {view.createdBy}{view.receivedAt ? ` · Received ${when(view.receivedAt)}` : ""}</div>
                  {view.items.map((l, i) => (
                    <div className="d-flex justify-content-between border-top py-2" style={{ borderColor: "var(--border)" }} key={i}>
                      <div>{l.productName}<div className="text-secondary small">{l.sku} · {l.quantity} × {money(l.unitCost)}</div></div>
                      <strong>{money(l.subtotal)}</strong>
                    </div>
                  ))}
                  <div className="d-flex justify-content-between border-top pt-2" style={{ borderColor: "var(--border)" }}><strong>Total cost</strong><strong>{money(view.totalCost)}</strong></div>
                </div>
                <div className="modal-footer">
                  {view.status === "PENDING" && <button className="btn btn-accent" onClick={() => receive(view)}>Receive stock</button>}
                  <button className="btn btn-outline-light" onClick={() => setView(null)}>Close</button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {supModal && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog">
              <form className="modal-content app-modal" onSubmit={saveSupplier}>
                <div className="modal-header"><h5 className="modal-title">{supModal.id ? "Edit supplier" : "Add supplier"}</h5><button type="button" className="btn-close btn-close-white" onClick={() => setSupModal(null)} /></div>
                <div className="modal-body">
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}
                  <label className="form-label">Name</label>
                  <input className="form-control mb-3" maxLength={100} value={supForm.name} onChange={(e) => setSupForm({ ...supForm, name: e.target.value })} required />
                  <label className="form-label">Phone (optional)</label>
                  <input className="form-control mb-3" maxLength={20} value={supForm.phone} onChange={(e) => setSupForm({ ...supForm, phone: e.target.value })} />
                  <label className="form-label">Email (optional)</label>
                  <input type="email" className="form-control mb-3" maxLength={100} value={supForm.email} onChange={(e) => setSupForm({ ...supForm, email: e.target.value })} />
                  <label className="form-label">Address (optional)</label>
                  <input className="form-control mb-2" maxLength={255} value={supForm.address} onChange={(e) => setSupForm({ ...supForm, address: e.target.value })} />
                </div>
                <div className="modal-footer"><button type="button" className="btn btn-outline-light" onClick={() => setSupModal(null)}>Cancel</button><button className="btn btn-accent" disabled={saving}>{saving ? "Saving..." : "Save"}</button></div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {poOpen && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-lg modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={createPo}>
                <div className="modal-header"><h5 className="modal-title">New purchase order</h5><button type="button" className="btn-close btn-close-white" onClick={() => setPoOpen(false)} /></div>
                <div className="modal-body" style={{ minHeight: 320 }}>
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}
                  <label className="form-label">Supplier</label>
                  <div className="mb-3">
                    <SearchSelect placeholder="Type to find a supplier" value={supplierId} onChange={pickSupplier} options={suppliers.map((s) => ({ value: s.id, label: s.name }))} />
                  </div>
                  <label className="form-label">Products (quantity and unit cost)</label>
                  {supplierId === "" && <p className="text-secondary small">Select a supplier first. Only that supplier's products will be listed.</p>}
                  {supplierId !== "" && supplierProducts.length === 0 && <div className="alert alert-warning py-2">No products are linked to this supplier yet. Open Products, edit a product and choose this supplier.</div>}
                  {rows.map((r, i) => (
                    <div className="d-flex flex-wrap gap-2 mb-2" key={i}>
                      <div className="flex-grow-1" style={{ flexBasis: 220 }}>
                        <SearchSelect placeholder={supplierId === "" ? "Select a supplier first" : "Type to find a product"} value={r.productId} onChange={(v) => pickProduct(i, v)}
                          options={supplierProducts.map((p) => ({ value: p.id, label: `${p.name} (${p.sku}) · stock ${p.currentStock}`, disabled: rows.some((x, idx) => idx !== i && String(x.productId) === String(p.id)) }))} />
                      </div>
                      <input type="number" min="1" className="form-control" style={{ width: 80 }} placeholder="Qty" value={r.quantity} onChange={(e) => setRow(i, { quantity: e.target.value })} />
                      <input type="number" min="0" step="0.01" className="form-control" style={{ width: 110 }} placeholder="Unit cost" value={r.cost} onChange={(e) => setRow(i, { cost: e.target.value })} />
                      {rows.length > 1 && <button type="button" className="btn btn-outline-danger" onClick={() => setRows(rows.filter((_, idx) => idx !== i))}>✕</button>}
                    </div>
                  ))}
                  <button type="button" className="btn btn-sm btn-outline-light mt-1" onClick={() => setRows([...rows, emptyRow])}>+ Add another product</button>
                  <div className="d-flex justify-content-between mt-4"><span className="text-secondary">Total cost</span><strong>{money(total)}</strong></div>
                </div>
                <div className="modal-footer"><button type="button" className="btn btn-outline-light" onClick={() => setPoOpen(false)}>Cancel</button><button className="btn btn-accent" disabled={saving}>{saving ? "Saving..." : "Create purchase order"}</button></div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {toast && (
        <div className="toast-box"><span>{toast.text}</span><button className="btn btn-sm btn-outline-light" onClick={() => setToast(null)}>✕</button></div>
      )}
    </div>
  );
}