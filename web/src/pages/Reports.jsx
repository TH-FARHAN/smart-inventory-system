import { useEffect, useState } from "react";
import api from "../api";
import { salesPdf, inventoryPdf } from "../pdfReports";

const money = (n) => "\u20B9" + Number(n || 0).toLocaleString("en-IN");

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  return err.response.data?.message || "Something went wrong.";
}

function toInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function Card({ label, value, accent }) {
  return (
    <div className="col-6 col-lg-3">
      <div className={"stat-card" + (accent ? " accent" : "")}>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div className="stat-card">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
    </div>
  );
}

function SalesReport() {
  const now = new Date();
  const [from, setFrom] = useState(toInput(new Date(now.getFullYear(), now.getMonth(), 1)));
  const [to, setTo] = useState(toInput(now));
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [keyword, setKeyword] = useState("");
  const [returns, setReturns] = useState([]);
  const [orders, setOrders] = useState([]);
  const [names, setNames] = useState({});

  const badRange = from && to && from > to;

  useEffect(() => {
    if (badRange) return;
    let ignore = false;
    api.get("/reports/sales", { params: { ...(from && { from }), ...(to && { to }) } })
      .then((r) => { if (!ignore) { setData(r.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); });
    return () => { ignore = true; };
  }, [from, to, badRange]);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      api.get("/returns").catch(() => ({ data: [] })),
      api.get("/orders").catch(() => ({ data: [] })),
      api.get("/admin/users").catch(() => ({ data: [] })),
    ]).then(([r, o, u]) => {
      if (ignore) return;
      setReturns(r.data);
      setOrders(o.data);
      const map = {};
      u.data.forEach((x) => { map[x.email.toLowerCase()] = x.name; });
      setNames(map);
    });
    return () => { ignore = true; };
  }, []);

  function thisMonth() {
    setFrom(toInput(new Date(now.getFullYear(), now.getMonth(), 1)));
    setTo(toInput(now));
  }
  function lastMonth() {
    setFrom(toInput(new Date(now.getFullYear(), now.getMonth() - 1, 1)));
    setTo(toInput(new Date(now.getFullYear(), now.getMonth(), 0)));
  }
  function thisYear() {
    setFrom(toInput(new Date(now.getFullYear(), 0, 1)));
    setTo(toInput(now));
  }

  // ---- figures built from returns and orders inside the chosen dates ----
  const inRange = (iso) => {
    const d = String(iso || "").slice(0, 10);
    return (!from || d >= from) && (!to || d <= to);
  };
  const periodReturns = returns.filter((r) => inRange(r.createdAt));
  const refunds = periodReturns.reduce((s, r) => s + Number(r.refundAmount), 0);

  const returnLines = [];
  periodReturns.forEach((r) =>
    (r.items || []).forEach((l) =>
      returnLines.push({
        id: r.id, date: r.createdAt, orderId: r.orderId, productId: l.productId,
        name: l.productName, sku: l.sku, qty: l.quantity, price: l.unitPrice,
        refund: l.subtotal, reason: r.reason,
      })
    )
  );

  const retQty = {};
  const retAmt = {};
  returnLines.forEach((l) => {
    retQty[l.productId] = (retQty[l.productId] || 0) + Number(l.qty);
    retAmt[l.productId] = (retAmt[l.productId] || 0) + Number(l.refund);
  });
  const products = (data?.byProduct || []).map((p) => ({
    id: p.productId, name: p.productName, sku: p.sku, sold: p.quantitySold,
    returned: retQty[p.productId] || 0,
    net: Number(p.revenue) - (retAmt[p.productId] || 0),
  }));

  const owner = {};
  orders.forEach((o) => { owner[o.id] = (o.createdBy || "unknown").toLowerCase(); });
  const by = {};
  const entry = (k) => {
    if (!by[k]) by[k] = { email: k, orders: 0, sales: 0, cancelled: 0, refunds: 0 };
    return by[k];
  };
  orders.forEach((o) => {
    if (!inRange(o.createdAt)) return;
    const e = entry((o.createdBy || "unknown").toLowerCase());
    if (o.status === "CONFIRMED") { e.orders += 1; e.sales += Number(o.totalAmount); }
    else e.cancelled += 1;
  });
  periodReturns.forEach((r) => { entry(owner[r.orderId] || "unknown").refunds += Number(r.refundAmount); });
  const staff = Object.values(by)
    .map((r) => ({ ...r, name: names[r.email] || r.email, net: r.sales - r.refunds }))
    .sort((a, b) => b.net - a.net);

  const gross = data ? Number(data.totalSales) + refunds : 0;
  const extra = { gross, refunds, products, returnLines, staff };

  const kw = keyword.trim().toLowerCase();
  const rows = products.filter((p) => !kw || p.name.toLowerCase().includes(kw) || (p.sku || "").toLowerCase().includes(kw));
  const day = (s) => (s ? new Date(s).toLocaleDateString("en-IN") : "-");

  return (
    <>
      <div className="rpt-card">
        <div className="rpt-label">Quick range</div>
        <div className="rpt-chips">
          <button className="btn btn-sm btn-outline-light" onClick={thisMonth}>This month</button>
          <button className="btn btn-sm btn-outline-light" onClick={lastMonth}>Last month</button>
          <button className="btn btn-sm btn-outline-light" onClick={thisYear}>This year</button>
        </div>

        <div className="rpt-label">Custom dates</div>
        <div className="rpt-dates">
          <div>
            <label className="form-label">From</label>
            <input type="date" className="form-control" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div>
            <label className="form-label">To</label>
            <input type="date" className="form-control" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        {badRange && <div className="alert alert-warning py-2 mb-3">The From date must be before the To date.</div>}

        <button className="btn btn-accent rpt-download" disabled={!data || badRange} onClick={() => salesPdf(data, from, to, extra)}>
          Download PDF
        </button>
      </div>

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {!data && !error && <p className="text-secondary">Loading...</p>}

      {data && (
        <>
          <div className="rpt-hero">
            <div className="stat-label">Net sales</div>
            <div className="rpt-hero-value">{money(data.totalSales)}</div>
            <div className="rpt-hero-sub">Gross sales minus refunds</div>
          </div>
          <div className="rpt-stats">
            <Stat label="Gross sales" value={money(gross)} />
            <Stat label="Refunds (returns)" value={money(refunds)} />
            <Stat label="Orders" value={data.totalOrders} />
            <Stat label="Average order" value={money(data.averageOrderValue)} />
          </div>

          <div className="rpt-h"><span>Sales by product</span><span className="rpt-count">{rows.length} items</span></div>
          <input
            type="search" inputMode="search" enterKeyHint="search" className="form-control mb-3"
            placeholder="Search products in this report" autoComplete="off"
            value={keyword} onChange={(e) => setKeyword(e.target.value)}
          />
          {rows.length === 0 && <p className="text-secondary">No sales in this period.</p>}
          {rows.length > 0 && (
            <>
              <div className="table-responsive d-none d-md-block">
                <table className="table table-dark align-middle">
                  <thead>
                    <tr><th>Product</th><th>SKU</th><th className="text-end">Sold</th><th className="text-end">Returned</th><th className="text-end">Net revenue</th></tr>
                  </thead>
                  <tbody>
                    {rows.map((p) => (
                      <tr key={p.id}>
                        <td>{p.name}</td>
                        <td>{p.sku}</td>
                        <td className="text-end">{p.sold}</td>
                        <td className="text-end">{p.returned > 0 ? <span className="text-danger">{p.returned}</span> : 0}</td>
                        <td className="text-end">{money(p.net)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="d-md-none">
                {rows.map((p) => (
                  <div className="rpt-list-card" key={p.id}>
                    <div className="rpt-row">
                      <div><strong>{p.name}</strong><div className="rpt-sub">{p.sku}</div></div>
                      <div className="rpt-amt">{money(p.net)}</div>
                    </div>
                    <div className="rpt-sub mt-2">Sold {p.sold} · Returned {p.returned}</div>
                  </div>
                ))}
              </div>
            </>
          )}

          <div className="rpt-h"><span>Returned products</span><span className="rpt-count">{returnLines.length} items</span></div>
          {returnLines.length === 0 && <p className="text-secondary">No returns in this period.</p>}
          {returnLines.length > 0 && (
            <>
              <div className="table-responsive d-none d-md-block">
                <table className="table table-dark align-middle">
                  <thead>
                    <tr><th>Return</th><th>Date</th><th>Order</th><th>Product</th><th className="text-end">Qty</th><th className="text-end">Price</th><th className="text-end">Refund</th><th>Reason</th></tr>
                  </thead>
                  <tbody>
                    {returnLines.map((r, i) => (
                      <tr key={i}>
                        <td>#{r.id}</td>
                        <td className="text-nowrap">{day(r.date)}</td>
                        <td>#{r.orderId}</td>
                        <td>{r.name}</td>
                        <td className="text-end">{r.qty}</td>
                        <td className="text-end">{money(r.price)}</td>
                        <td className="text-end">{money(r.refund)}</td>
                        <td>{r.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="d-md-none">
                {returnLines.map((r, i) => (
                  <div className="rpt-list-card" key={i}>
                    <div className="rpt-row">
                      <div>
                        <strong>{r.name}</strong>
                        <div className="rpt-sub">Return #{r.id} · Order #{r.orderId} · {day(r.date)}</div>
                      </div>
                      <div className="rpt-amt">{money(r.refund)}</div>
                    </div>
                    <div className="rpt-sub mt-2">{r.qty} × {money(r.price)}{r.reason ? " · " + r.reason : ""}</div>
                  </div>
                ))}
              </div>
            </>
          )}

          <div className="rpt-h"><span>Staff performance</span><span className="rpt-count">{staff.length} staff</span></div>
          {staff.length === 0 && <p className="text-secondary">No staff activity in this period.</p>}
          {staff.length > 0 && (
            <>
              <div className="table-responsive d-none d-md-block">
                <table className="table table-dark align-middle">
                  <thead>
                    <tr><th>Staff</th><th className="text-end">Orders</th><th className="text-end">Sales</th><th className="text-end">Refunds</th><th className="text-end">Net sales</th><th className="text-end">Cancelled</th></tr>
                  </thead>
                  <tbody>
                    {staff.map((r) => (
                      <tr key={r.email}>
                        <td>{r.name}<div className="text-secondary small">{r.email}</div></td>
                        <td className="text-end">{r.orders}</td>
                        <td className="text-end">{money(r.sales)}</td>
                        <td className="text-end">{money(r.refunds)}</td>
                        <td className="text-end">{money(r.net)}</td>
                        <td className="text-end">{r.cancelled}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <div className="d-md-none">
                {staff.map((r) => (
                  <div className="rpt-list-card" key={r.email}>
                    <div className="rpt-row">
                      <div><strong>{r.name}</strong><div className="rpt-sub">{r.email}</div></div>
                      <div className="rpt-amt">{money(r.net)}</div>
                    </div>
                    <div className="rpt-sub mt-2">{r.orders} orders · Sales {money(r.sales)} · Refunds {money(r.refunds)} · {r.cancelled} cancelled</div>
                  </div>
                ))}
              </div>
            </>
          )}
        </>
      )}
    </>
  );
}

function InventoryReport() {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [keyword, setKeyword] = useState("");

  useEffect(() => {
    let ignore = false;
    api.get("/reports/inventory")
      .then((r) => { if (!ignore) setData(r.data); })
      .catch((err) => { if (!ignore) setError(errText(err)); });
    return () => { ignore = true; };
  }, []);

  if (error) return <div className="alert alert-danger py-2">{error}</div>;
  if (!data) return <p className="text-secondary">Loading...</p>;

  const kw = keyword.trim().toLowerCase();
  const rows = data.products.filter(
    (p) => !kw || p.name.toLowerCase().includes(kw) || p.sku.toLowerCase().includes(kw) || (p.categoryName || "").toLowerCase().includes(kw)
  );

  return (
    <>
      <div className="rpt-card">
        <button className="btn btn-accent rpt-download" onClick={() => inventoryPdf(data)}>
          Download PDF
        </button>
      </div>
      <div className="row g-3 mb-3">
        <Card label="Products" value={data.totalProducts} />
        <Card label="Units in stock" value={data.totalUnits} />
        <Card label="Stock value (cost)" value={money(data.stockValueAtCost)} />
        <Card label="Stock value (selling)" value={money(data.stockValueAtSelling)} accent />
        <Card label="Low stock items" value={data.lowStockCount} accent={data.lowStockCount > 0} />
      </div>

      <input
        type="search" inputMode="search" enterKeyHint="search" className="form-control mb-3"
        placeholder="Search by name, SKU or category" autoComplete="off"
        value={keyword} onChange={(e) => setKeyword(e.target.value)}
      />

      {rows.length === 0 && <p className="text-secondary">No products found.</p>}

      {rows.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr><th>Name</th><th>SKU</th><th>Category</th><th className="text-end">Stock</th><th className="text-end">Min</th><th className="text-end">Cost</th><th className="text-end">Price</th></tr>
            </thead>
            <tbody>
              {rows.map((p) => (
                <tr key={p.id}>
                  <td>{p.name}</td>
                  <td>{p.sku}</td>
                  <td>{p.categoryName || "-"}</td>
                  <td className="text-end">{p.currentStock} {p.lowStock && <span className="badge bg-danger">Low</span>}</td>
                  <td className="text-end">{p.minimumStock}</td>
                  <td className="text-end">{money(p.purchasePrice)}</td>
                  <td className="text-end">{money(p.sellingPrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="d-md-none">
        {rows.map((p) => (
          <div className="rpt-list-card" key={p.id}>
            <div className="rpt-row">
              <div><strong>{p.name}</strong><div className="rpt-sub">{p.sku} · {p.categoryName || "No category"}</div></div>
              <div className="rpt-amt">{money(p.sellingPrice)}</div>
            </div>
            <div className="rpt-row mt-2">
              <span className="rpt-sub">Stock {p.currentStock} (min {p.minimumStock}) · Cost {money(p.purchasePrice)}</span>
              {p.lowStock && <span className="badge bg-danger">Low</span>}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}

export default function Reports() {
  const [tab, setTab] = useState("sales");
  return (
    <div>
      <h2 className="mb-3">Reports</h2>
      <div className="rpt-tabs">
        <button className={"rpt-tab" + (tab === "sales" ? " active" : "")} onClick={() => setTab("sales")}>Sales report</button>
        <button className={"rpt-tab" + (tab === "inventory" ? " active" : "")} onClick={() => setTab("inventory")}>Inventory report</button>
      </div>
      {tab === "sales" ? <SalesReport /> : <InventoryReport />}
    </div>
  );
}