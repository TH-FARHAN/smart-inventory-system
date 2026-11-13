import { useEffect, useState } from "react";
import api from "../api";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  const d = err.response.data;
  if (d?.errors) return Object.entries(d.errors).map(([f, m]) => `${f}: ${m}`).join(", ");
  return d?.message || "Something went wrong.";
}

const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");

export default function Inventory() {
  const [tab, setTab] = useState("move");
  const [refreshKey, setRefreshKey] = useState(0);

  const [products, setProducts] = useState([]);
  const [low, setLow] = useState([]);
  const [moves, setMoves] = useState([]);
  const [filterProduct, setFilterProduct] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [type, setType] = useState("in");
  const [form, setForm] = useState({ productId: "", quantity: "", reason: "" });
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState("");
  const [success, setSuccess] = useState("");

  // product list (for the dropdowns)
  useEffect(() => {
    api.get("/products")
      .then((r) => setProducts(r.data.filter((p) => p.active !== false)))
      .catch((err) => setError(errText(err)))
      .finally(() => setLoading(false));
  }, [refreshKey]);

  // low stock list
  useEffect(() => {
    if (tab !== "low") return;
    api.get("/inventory/low-stock")
      .then((r) => setLow(r.data))
      .catch((err) => setError(errText(err)));
  }, [tab, refreshKey]);

  // movement history
  useEffect(() => {
    if (tab !== "history") return;
    api.get("/inventory/movements", { params: filterProduct ? { productId: filterProduct } : {} })
      .then((r) => setMoves(r.data))
      .catch((err) => setError(errText(err)));
  }, [tab, filterProduct, refreshKey]);

  function change(e) {
    setForm({ ...form, [e.target.name]: e.target.value });
  }

  async function submit(e) {
    e.preventDefault();
    setFormError("");
    setSuccess("");
    setSaving(true);
    try {
      const res = await api.post(type === "in" ? "/inventory/stock-in" : "/inventory/stock-out", {
        productId: Number(form.productId),
        quantity: Number(form.quantity),
        reason: form.reason.trim() || null,
      });
      setSuccess(`Done. ${res.data.name} now has ${res.data.currentStock} in stock.`);
      setForm({ productId: form.productId, quantity: "", reason: "" });
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(errText(err));
    } finally {
      setSaving(false);
    }
  }

  function goTab(t) {
    setError("");
    setTab(t);
  }

  const tabs = [
    ["move", "Stock In/Out"],
    ["low", "Low stock"],
    ["history", "History"],
  ];

  return (
    <div>
      <h2 className="mb-3 text-center">Inventory</h2>

      <div className="d-flex flex-wrap gap-2 mb-3 justify-content-center">
        {tabs.map(([key, label]) => (
          <button
            key={key}
            className={"btn btn-sm " + (tab === key ? "btn-accent" : "btn-outline-light")}
            onClick={() => goTab(key)}
          >
            {label}
          </button>
        ))}
      </div>

      {error && <div className="alert alert-danger py-2">{error}</div>}

      {/* TAB 1: stock in / out */}
      {tab === "move" && (
        <form className="product-card" style={{ maxWidth: 480, margin: "0 auto" }} onSubmit={submit}>
          <div className="d-flex gap-2 mb-3">
            <button type="button" className={"btn flex-fill " + (type === "in" ? "btn-accent" : "btn-outline-light")} onClick={() => setType("in")}>
              Stock In
            </button>
            <button type="button" className={"btn flex-fill " + (type === "out" ? "btn-accent" : "btn-outline-light")} onClick={() => setType("out")}>
              Stock Out
            </button>
          </div>

          {formError && <div className="alert alert-danger py-2">{formError}</div>}
          {success && <div className="alert alert-success py-2">{success}</div>}

          <label className="form-label">Product</label>
          <select className="form-select form-control mb-3" name="productId" value={form.productId} onChange={change} required>
            <option value="">Select a product</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name} ({p.sku}) - stock {p.currentStock}
              </option>
            ))}
          </select>

          <label className="form-label">Quantity</label>
          <input type="number" min="1" className="form-control mb-3" name="quantity" value={form.quantity} onChange={change} required />

          <label className="form-label">Reason (optional)</label>
          <input className="form-control mb-3" name="reason" maxLength={255} value={form.reason} onChange={change} />

          <button type="submit" className="btn btn-accent w-100" disabled={saving}>
            {saving ? "Saving..." : type === "in" ? "Add stock" : "Remove stock"}
          </button>
        </form>
      )}

      {/* TAB 2: low stock */}
      {tab === "low" && (
        <>
          {low.length === 0 && <p className="text-secondary">No low-stock products. 🎉</p>}
          {low.length > 0 && (
            <div className="table-responsive d-none d-md-block">
              <table className="table table-dark align-middle">
                <thead>
                  <tr><th>Name</th><th>SKU</th><th>Category</th><th className="text-end">Stock</th><th className="text-end">Minimum</th></tr>
                </thead>
                <tbody>
                  {low.map((p) => (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>{p.sku}</td>
                      <td>{p.categoryName || "-"}</td>
                      <td className="text-end text-danger fw-bold">{p.currentStock}</td>
                      <td className="text-end">{p.minimumStock}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="d-md-none">
            {low.map((p) => (
              <div className="product-card" key={p.id}>
                <strong>{p.name}</strong>
                <div className="text-secondary small">{p.sku} · {p.categoryName || "No category"}</div>
                <div className="d-flex justify-content-between mt-2">
                  <span className="text-danger fw-bold">Stock: {p.currentStock}</span>
                  <span>Minimum: {p.minimumStock}</span>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {/* TAB 3: history */}
      {tab === "history" && (
        <>
          <select className="form-select form-control mb-3" style={{ maxWidth: 360 }} value={filterProduct} onChange={(e) => setFilterProduct(e.target.value)}>
            <option value="">All products</option>
            {products.map((p) => (
              <option key={p.id} value={p.id}>{p.name} ({p.sku})</option>
            ))}
          </select>

          {moves.length === 0 && <p className="text-secondary">No movements yet.</p>}
          {moves.length > 0 && (
            <div className="table-responsive d-none d-md-block">
              <table className="table table-dark align-middle">
                <thead>
                  <tr><th>Date</th><th>Product</th><th>Type</th><th className="text-end">Qty</th><th className="text-end">Before → After</th><th>By</th><th>Reason</th></tr>
                </thead>
                <tbody>
                  {moves.map((m) => (
                    <tr key={m.id}>
                      <td className="text-nowrap">{when(m.createdAt)}</td>
                      <td>{m.productName}<div className="text-secondary small">{m.sku}</div></td>
                      <td><span className={"badge " + (m.type === "STOCK_IN" ? "bg-success" : "bg-danger")}>{m.type === "STOCK_IN" ? "IN" : "OUT"}</span></td>
                      <td className="text-end">{m.quantity}</td>
                      <td className="text-end text-nowrap">{m.previousStock} → {m.updatedStock}</td>
                      <td>{m.userName}</td>
                      <td>{m.reason || "-"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <div className="d-md-none">
            {moves.map((m) => (
              <div className="product-card" key={m.id}>
                <div className="d-flex justify-content-between">
                  <strong>{m.productName}</strong>
                  <span className={"badge " + (m.type === "STOCK_IN" ? "bg-success" : "bg-danger")}>{m.type === "STOCK_IN" ? "IN" : "OUT"}</span>
                </div>
                <div className="text-secondary small">{m.sku} · {when(m.createdAt)}</div>
                <div className="mt-2">Qty {m.quantity}: {m.previousStock} → {m.updatedStock}</div>
                <div className="text-secondary small">By {m.userName}{m.reason ? ` · ${m.reason}` : ""}</div>
              </div>
            ))}
          </div>
        </>
      )}

      {loading && tab === "move" && <p className="text-secondary mt-3">Loading products...</p>}
    </div>
  );
}