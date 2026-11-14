import { useEffect, useState } from "react";
import api from "../api";

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");

export default function Returns() {
  const [list, setList] = useState(null);
  const [keyword, setKeyword] = useState("");
  const [view, setView] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/returns")
      .then((r) => setList(r.data))
      .catch((err) => setError(err.response?.data?.message || "Could not load returns."));
  }, []);

  const kw = keyword.trim().toLowerCase();
  const shown = (list || []).filter((r) =>
    !kw || String(r.id).includes(kw) || String(r.orderId).includes(kw) ||
    (r.customerName || "").toLowerCase().includes(kw) || (r.reason || "").toLowerCase().includes(kw) ||
    (r.createdBy || "").toLowerCase().includes(kw));
  const total = shown.reduce((s, r) => s + Number(r.refundAmount), 0);

  return (
    <div>
      <h2 className="mb-1">Returns</h2>
      <p className="text-secondary">Items customers brought back. To record a return, open an order and tap Return.</p>
      <div className="row g-3 mb-3">
        <div className="col-6 col-lg-3"><div className="stat-card"><div className="stat-label">Returns</div><div className="stat-value">{shown.length}</div></div></div>
        <div className="col-6 col-lg-3"><div className="stat-card accent"><div className="stat-label">Total refunded</div><div className="stat-value">{money(total)}</div></div></div>
      </div>
      <input type="search" inputMode="search" enterKeyHint="search" className="form-control mb-3" placeholder="Search by return no, order no, customer or reason" autoComplete="off" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
      {error && <div className="alert alert-danger py-2">{error}</div>}
      {!list && !error && <p className="text-secondary">Loading...</p>}
      {list && shown.length === 0 && <p className="text-secondary">No returns found.</p>}

      {shown.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead><tr><th>Return</th><th>Date</th><th>Order</th><th>Customer</th><th>Reason</th><th>By</th><th className="text-end">Refund</th><th></th></tr></thead>
            <tbody>
              {shown.map((r) => (
                <tr key={r.id}>
                  <td>#{r.id}</td><td className="text-nowrap">{when(r.createdAt)}</td><td>#{r.orderId}</td>
                  <td>{r.customerName}</td><td>{r.reason}</td><td>{r.createdBy}</td>
                  <td className="text-end">{money(r.refundAmount)}</td>
                  <td className="text-end"><button className="btn btn-sm btn-outline-light" onClick={() => setView(r)}>View</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="d-md-none">
        {shown.map((r) => (
          <div className="product-card" key={r.id} onClick={() => setView(r)}>
            <div className="d-flex justify-content-between"><strong>Return #{r.id} · Order #{r.orderId}</strong><strong>{money(r.refundAmount)}</strong></div>
            <div className="text-secondary small">{when(r.createdAt)}</div>
            <div className="mt-1">{r.customerName}</div>
            <div className="text-secondary small">{r.reason}</div>
          </div>
        ))}
      </div>

      {view && (
        <>
          <div className="modal d-block" tabIndex="-1">
            <div className="modal-dialog modal-dialog-scrollable">
              <div className="modal-content app-modal">
                <div className="modal-header">
                  <h5 className="modal-title">Return #{view.id}</h5>
                  <button type="button" className="btn-close btn-close-white" onClick={() => setView(null)} />
                </div>
                <div className="modal-body">
                  <div>{view.customerName} · Order #{view.orderId}</div>
                  <div className="text-secondary small mb-2">{when(view.createdAt)} · By {view.createdBy}</div>
                  <div className="mb-3">Reason: {view.reason}</div>
                  {view.items.map((l, i) => (
                    <div className="d-flex justify-content-between border-top py-2" style={{ borderColor: "var(--border)" }} key={i}>
                      <div>{l.productName}<div className="text-secondary small">{l.quantity} × {money(l.unitPrice)}</div></div>
                      <strong>{money(l.subtotal)}</strong>
                    </div>
                  ))}
                  <div className="d-flex justify-content-between border-top pt-2" style={{ borderColor: "var(--border)" }}><strong>Refund</strong><strong>{money(view.refundAmount)}</strong></div>
                </div>
                <div className="modal-footer"><button className="btn btn-outline-light" onClick={() => setView(null)}>Close</button></div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop show"></div>
        </>
      )}
    </div>
  );
}