import { useEffect, useState } from "react";
import api from "../api";

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const REASONS = ["Damaged item", "Wrong item", "Expired item", "Customer changed mind", "Other"];

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.values(d.errors).join(", ");
  return d?.message || "Something went wrong.";
}

export default function ReturnModal({ order, onClose, onDone }) {
  const [lines, setLines] = useState(null);
  const [qty, setQty] = useState({});
  const [reason, setReason] = useState(REASONS[0]);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let ignore = false;
    api.get(`/returns/order/${order.id}/returnable`)
      .then((r) => { if (!ignore) setLines(r.data); })
      .catch((err) => { if (!ignore) setError(errText(err)); });
    return () => { ignore = true; };
  }, [order.id]);

  const refund = (lines || []).reduce((s, l) => s + Number(l.unitPrice) * Number(qty[l.productId] || 0), 0);

  async function submit(e) {
    e.preventDefault();
    setError("");
    const items = [];
    for (const l of lines) {
      const q = Number(qty[l.productId] || 0);
      if (q < 0 || q > l.remaining) return setError(`${l.productName}: you can return at most ${l.remaining}.`);
      if (q > 0) items.push({ productId: l.productId, quantity: q });
    }
    if (items.length === 0) return setError("Enter a quantity for at least one item.");
    setSaving(true);
    try {
      const res = await api.post("/returns", {
        orderId: order.id,
        reason: note.trim() ? `${reason}: ${note.trim()}` : reason,
        items,
      });
      onDone(`Return recorded for order #${order.id}. Refund ${money(res.data.refundAmount)}. Stock restored.`);
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
        <div className="modal-dialog modal-dialog-scrollable">
          <form className="modal-content app-modal" onSubmit={submit}>
            <div className="modal-header">
              <h5 className="modal-title">Return items · Order #{order.id}</h5>
              <button type="button" className="btn-close btn-close-white" onClick={onClose} />
            </div>
            <div className="modal-body">
              {error && <div className="alert alert-danger py-2">{error}</div>}
              {!lines && !error && <p className="text-secondary">Loading...</p>}
              {lines && lines.length === 0 && <p className="text-secondary">Nothing can be returned on this order.</p>}
              {lines && lines.map((l) => (
                <div className="d-flex justify-content-between align-items-center gap-2 border-top py-2" style={{ borderColor: "var(--border)" }} key={l.productId}>
                  <div>
                    {l.productName}
                    <div className="text-secondary small">
                      {money(l.unitPrice)} each · bought {l.bought} · returned {l.returned} · can return {l.remaining}
                    </div>
                  </div>
                  <input
                    type="number" min="0" max={l.remaining} disabled={l.remaining === 0}
                    className="form-control" style={{ width: 80 }}
                    value={qty[l.productId] ?? ""} placeholder="0"
                    onChange={(e) => setQty({ ...qty, [l.productId]: e.target.value })}
                  />
                </div>
              ))}
              {lines && lines.length > 0 && (
                <>
                  <label className="form-label mt-3">Reason</label>
                  <select className="form-select form-control mb-2" value={reason} onChange={(e) => setReason(e.target.value)}>
                    {REASONS.map((r) => <option key={r}>{r}</option>)}
                  </select>
                  <input className="form-control mb-3" placeholder="Note (optional)" maxLength={150} value={note} onChange={(e) => setNote(e.target.value)} />
                  <div className="d-flex justify-content-between">
                    <span className="text-secondary">Refund amount</span>
                    <strong>{money(refund)}</strong>
                  </div>
                </>
              )}
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-outline-light" onClick={onClose}>Cancel</button>
              <button type="submit" className="btn btn-accent" disabled={saving || !lines || lines.length === 0}>
                {saving ? "Saving..." : "Confirm return"}
              </button>
            </div>
          </form>
        </div>
      </div>
      <div className="modal-backdrop show"></div>
    </>
  );
}