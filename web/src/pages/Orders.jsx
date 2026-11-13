import { useEffect, useState } from "react";
import api from "../api";
import SearchSelect from "../components/SearchSelect";
import { useConfirm } from "../confirmContext";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");
const emptyRow = { productId: "", quantity: "1" };

function StatusBadge({ status }) {
  return (
    <span className={"badge " + (status === "CONFIRMED" ? "bg-success" : "bg-secondary")}>
      {status}
    </span>
  );
}

export default function Orders() {
      const confirm = useConfirm();
  const [orders, setOrders] = useState([]);
  const [customers, setCustomers] = useState([]);
  const [products, setProducts] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [toast, setToast] = useState(null);

  const [view, setView] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [customerId, setCustomerId] = useState("");
  const [rows, setRows] = useState([emptyRow]);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    api.get("/orders")
      .then((r) => { if (!ignore) { setOrders(r.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    api.get("/products")
      .then((r) => { if (!ignore) setProducts(r.data.filter((p) => p.active !== false)); })
      .catch(() => {});
    return () => { ignore = true; };
  }, [refreshKey]);

  useEffect(() => {
    api.get("/customers").then((r) => setCustomers(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 6000);
    return () => clearTimeout(t);
  }, [toast]);

  async function undo() {
    const id = toast.undoId;
    setToast(null);
    try {
      await api.post(`/orders/${id}/cancel`);
      setToast({ text: `Order #${id} undone. Stock restored.` });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(errText(err));
    }
  }

  // live search + status filter (newest first)
  const kw = keyword.trim().toLowerCase();
  const shown = [...orders]
    .sort((a, b) => b.id - a.id)
    .filter((o) => !statusFilter || o.status === statusFilter)
    .filter(
      (o) =>
        !kw ||
        String(o.id).includes(kw) ||
        (o.customerName || "").toLowerCase().includes(kw) ||
        (o.createdBy || "").toLowerCase().includes(kw)
    );

  function openCreate() {
    setCustomerId("");
    setRows([emptyRow]);
    setFormError("");
    setCreateOpen(true);
  }

  function setRow(i, field, value) {
    setRows(rows.map((r, idx) => (idx === i ? { ...r, [field]: value } : r)));
  }

  const productById = (id) => products.find((p) => String(p.id) === String(id));
  const estimate = rows.reduce((sum, r) => {
    const p = productById(r.productId);
    return sum + (p ? Number(p.sellingPrice) * Number(r.quantity || 0) : 0);
  }, 0);

  async function create(e) {
    e.preventDefault();
    setFormError("");
    if (!customerId) {
      setFormError("Select a customer.");
      return;
    }
    const items = rows
      .filter((r) => r.productId !== "")
      .map((r) => ({ productId: Number(r.productId), quantity: Number(r.quantity) }));
    if (items.length === 0) {
      setFormError("Add at least one product.");
      return;
    }
    setSaving(true);
    try {
      const res = await api.post("/orders", { customerId: Number(customerId), items });
      setCreateOpen(false);
      setToast({ text: `Order #${res.data.id} placed · ${money(res.data.totalAmount)}`, undoId: res.data.id });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(errText(err));
    } finally {
      setSaving(false);
    }
  }

    async function cancel(o) {
    const ok = await confirm({
      title: "Cancel order",
      message: `Cancel order #${o.id}? The stock will be restored.`,
      confirmText: "Cancel order",
      cancelText: "Keep order",
      danger: true,
    });
    if (!ok) return;
    setError("");
    try {
      const res = await api.post(`/orders/${o.id}/cancel`);
      setToast({ text: `Order #${o.id} cancelled. Stock restored.` });
      if (view && view.id === o.id) setView(res.data);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(err.response?.status === 409 ? "This order is already cancelled." : errText(err));
    }
  }

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
        <h2 className="m-0">Orders</h2>
        <button className="btn btn-accent" onClick={openCreate}>+ New order</button>
      </div>

      <div className="d-flex flex-wrap gap-2 mb-3">
        <input
          type="search"
          inputMode="search"
          enterKeyHint="search"
          className="form-control flex-grow-1"
          style={{ minWidth: 200, flexBasis: 240 }}
          placeholder="Search by order no, customer or staff"
          autoComplete="off"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <select
          className="form-select form-control"
          style={{ width: "auto" }}
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">All statuses</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && shown.length === 0 && <p className="text-secondary">No orders found.</p>}

      {/* Table: tablets and larger */}
      {shown.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr>
                <th>Order</th><th>Date</th><th>Customer</th><th>By</th>
                <th>Status</th><th className="text-end">Total</th><th></th>
              </tr>
            </thead>
            <tbody>
              {shown.map((o) => (
                <tr key={o.id}>
                  <td>#{o.id}</td>
                  <td className="text-nowrap">{when(o.createdAt)}</td>
                  <td>{o.customerName}</td>
                  <td>{o.createdBy}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td className="text-end">{money(o.totalAmount)}</td>
                  <td className="text-end text-nowrap">
                    <button className="btn btn-sm btn-outline-light me-2" onClick={() => setView(o)}>View</button>
                    {o.status === "CONFIRMED" && (
                      <button className="btn btn-sm btn-outline-danger" onClick={() => cancel(o)}>Cancel</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Cards: phones */}
      <div className="d-md-none">
        {shown.map((o) => (
          <div className="product-card" key={o.id}>
            <div className="d-flex justify-content-between">
              <strong>Order #{o.id}</strong>
              <StatusBadge status={o.status} />
            </div>
            <div className="text-secondary small">{when(o.createdAt)}</div>
            <div className="mt-2">{o.customerName}</div>
            <div className="d-flex justify-content-between mt-1">
              <span className="text-secondary small">By {o.createdBy}</span>
              <strong>{money(o.totalAmount)}</strong>
            </div>
            <div className="d-flex gap-2 mt-3">
              <button className="btn btn-sm btn-outline-light flex-fill" onClick={() => setView(o)}>View</button>
              {o.status === "CONFIRMED" && (
                <button className="btn btn-sm btn-outline-danger flex-fill" onClick={() => cancel(o)}>Cancel</button>
              )}
            </div>
          </div>
        ))}
      </div>

      {/* View order */}
      {view && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <div className="modal-content app-modal">
                <div className="modal-header">
                  <h5 className="modal-title">Order #{view.id}</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setView(null)} />
                </div>
                <div className="modal-body">
                  <div className="d-flex justify-content-between mb-2">
                    <span>{view.customerName}</span>
                    <StatusBadge status={view.status} />
                  </div>
                  <div className="text-secondary small mb-3">{when(view.createdAt)} · By {view.createdBy}</div>
                  {view.items.map((l, i) => (
                    <div className="d-flex justify-content-between border-top py-2" style={{ borderColor: "var(--border)" }} key={i}>
                      <div>
                        {l.productName}
                        <div className="text-secondary small">{l.sku} · {l.quantity} × {money(l.unitPrice)}</div>
                      </div>
                      <strong>{money(l.subtotal)}</strong>
                    </div>
                  ))}
                  <div className="d-flex justify-content-between border-top pt-2 mt-1" style={{ borderColor: "var(--border)" }}>
                    <strong>Total</strong>
                    <strong>{money(view.totalAmount)}</strong>
                  </div>
                </div>
                <div className="modal-footer">
                  {view.status === "CONFIRMED" && (
                    <button className="btn btn-outline-danger" onClick={() => cancel(view)}>Cancel order</button>
                  )}
                  <button className="btn btn-outline-light" onClick={() => setView(null)}>Close</button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {/* New order */}
      {createOpen && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-lg modal-dialog-scrollable">
              <form className="modal-content app-modal" onSubmit={create}>
                <div className="modal-header">
                  <h5 className="modal-title">New order</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setCreateOpen(false)} />
                </div>
                <div className="modal-body" style={{ minHeight: 320 }}>
                  {formError && <div className="alert alert-danger py-2">{formError}</div>}

                  <label className="form-label">Customer</label>
                  <div className="mb-3">
                    <SearchSelect
                      placeholder="Type to find a customer"
                      value={customerId}
                      onChange={setCustomerId}
                      options={customers.map((c) => ({
                        value: c.id,
                        label: c.name + (c.phone ? ` (${c.phone})` : ""),
                      }))}
                    />
                  </div>

                  <label className="form-label">Products</label>
                  {rows.map((r, i) => (
                    <div className="d-flex flex-wrap gap-2 mb-2" key={i}>
                      <div className="flex-grow-1" style={{ flexBasis: 220 }}>
                        <SearchSelect
                          placeholder="Type to find a product"
                          value={r.productId}
                          onChange={(v) => setRow(i, "productId", v)}
                          options={products.map((p) => ({
                            value: p.id,
                            label: `${p.name} (${p.sku}) · ${money(p.sellingPrice)} · stock ${p.currentStock}`,
                            disabled: rows.some((x, idx) => idx !== i && String(x.productId) === String(p.id)),
                          }))}
                        />
                      </div>
                      <input
                        type="number"
                        min="1"
                        className="form-control"
                        style={{ width: 90 }}
                        value={r.quantity}
                        onChange={(e) => setRow(i, "quantity", e.target.value)}
                      />
                      {rows.length > 1 && (
                        <button type="button" className="btn btn-outline-danger" onClick={() => setRows(rows.filter((_, idx) => idx !== i))}>
                          ✕
                        </button>
                      )}
                    </div>
                  ))}
                  <button type="button" className="btn btn-sm btn-outline-light mt-1" onClick={() => setRows([...rows, emptyRow])}>
                    + Add another product
                  </button>

                  <div className="d-flex justify-content-between mt-4">
                    <span className="text-secondary">Estimated total</span>
                    <strong>{money(estimate)}</strong>
                  </div>
                  <div className="text-secondary small">The final price is set by the server.</div>
                </div>
                <div className="modal-footer">
                  <button type="button" className="btn btn-outline-light" onClick={() => setCreateOpen(false)}>Cancel</button>
                  <button type="submit" className="btn btn-accent" disabled={saving}>
                    {saving ? "Placing order..." : "Place order"}
                  </button>
                </div>
              </form>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}

      {/* Popup with Undo */}
      {toast && (
        <div className="toast-box">
          <span>{toast.text}</span>
          {toast.undoId && (
            <button className="btn btn-sm btn-accent" onClick={undo}>Undo</button>
          )}
          <button className="btn btn-sm btn-outline-light" onClick={() => setToast(null)}>✕</button>
        </div>
      )}
    </div>
  );
}