import { useEffect, useState } from "react";
import api from "../api";

const money = (n) => "\u20B9" + Number(n || 0).toLocaleString("en-IN");

function sod(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
function toInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
function parseDate(s) {
  if (!s) return null;
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}
function getRange(preset, from, to) {
  const now = new Date();
  const today = sod(now);
  if (preset === "month") return { start: new Date(now.getFullYear(), now.getMonth(), 1), end: today };
  if (preset === "last") return { start: new Date(now.getFullYear(), now.getMonth() - 1, 1), end: new Date(now.getFullYear(), now.getMonth(), 0) };
  if (preset === "year") return { start: new Date(now.getFullYear(), 0, 1), end: today };
  if (preset === "all") return { start: new Date(2000, 0, 1), end: today };
  const s = parseDate(from);
  const e = parseDate(to);
  if (!s || !e) return null;
  return { start: s, end: e };
}

export default function StaffPerformance() {
  const now = new Date();
  const [orders, setOrders] = useState(null);
  const [returns, setReturns] = useState([]);
  const [names, setNames] = useState({});
  const [preset, setPreset] = useState("month");
  const [from, setFrom] = useState(toInput(new Date(now.getFullYear(), now.getMonth(), 1)));
  const [to, setTo] = useState(toInput(now));
  const [keyword, setKeyword] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([
      api.get("/orders"),
      api.get("/returns").catch(() => ({ data: [] })),
      api.get("/admin/users").catch(() => ({ data: [] })),
    ])
      .then(([o, r, u]) => {
        setOrders(o.data);
        setReturns(r.data);
        const map = {};
        u.data.forEach((x) => { map[x.email.toLowerCase()] = x.name; });
        setNames(map);
      })
      .catch((err) => setError(err.response?.data?.message || "Could not load the data."));
  }, []);

  if (error) return <div className="alert alert-danger py-2">{error}</div>;
  if (!orders) return <p className="text-secondary">Loading...</p>;

  const presets = [["month", "This month"], ["last", "Last month"], ["year", "This year"], ["all", "All time"], ["custom", "Custom"]];
  const range = getRange(preset, from, to);
  let rangeError = "";
  if (!range) rangeError = "Pick both a From and a To date.";
  else if (range.start > range.end) rangeError = "The From date must be before the To date.";

  let rows = [];
  let totalNet = 0;
  let totalRefunds = 0;
  if (!rangeError) {
    const lo = range.start.getTime();
    const hi = range.end.getTime() + 86400000;
    const owner = {};
    orders.forEach((o) => { owner[o.id] = (o.createdBy || "unknown").toLowerCase(); });
    const by = {};
    const entry = (k) => {
      if (!by[k]) by[k] = { email: k, orders: 0, sales: 0, cancelled: 0, refunds: 0 };
      return by[k];
    };
    orders.forEach((o) => {
      const t = new Date(o.createdAt).getTime();
      if (t < lo || t >= hi) return;
      const e = entry((o.createdBy || "unknown").toLowerCase());
      if (o.status === "CONFIRMED") { e.orders += 1; e.sales += Number(o.totalAmount); }
      else e.cancelled += 1;
    });
    // a refund is charged to the staff member who made the original sale
    returns.forEach((r) => {
      const t = new Date(r.createdAt).getTime();
      if (t < lo || t >= hi) return;
      entry(owner[r.orderId] || "unknown").refunds += Number(r.refundAmount);
    });
    rows = Object.values(by)
      .map((r) => ({ ...r, net: r.sales - r.refunds }))
      .sort((a, b) => b.net - a.net);
    totalNet = rows.reduce((s, r) => s + r.net, 0);
    totalRefunds = rows.reduce((s, r) => s + r.refunds, 0);
  }
  const kw = keyword.trim().toLowerCase();
  const shown = rows.filter((r) => !kw || r.email.includes(kw) || (names[r.email] || "").toLowerCase().includes(kw));
  const totalOrders = rows.reduce((s, r) => s + r.orders, 0);
  const top = rows[0];
  const nameOf = (r) => names[r.email] || r.email;
  const cancelRate = (r) => (r.orders + r.cancelled > 0 ? (r.cancelled / (r.orders + r.cancelled)) * 100 : 0);
  const flag = (r) => r.orders + r.cancelled >= 5 && cancelRate(r) >= 20;

  return (
    <div>
      <h2 className="mb-1">Staff performance</h2>
      <p className="text-secondary">Who sold what, who cancels the most, and how much was refunded. Net sales = confirmed sales minus refunds.</p>

      <div className="product-card mb-3">
        <div className="d-flex flex-wrap gap-2">
          {presets.map(([k, label]) => (
            <button key={k} className={"btn btn-sm " + (preset === k ? "btn-accent" : "btn-outline-light")} onClick={() => setPreset(k)}>{label}</button>
          ))}
        </div>
        {preset === "custom" && (
          <div className="d-flex flex-wrap gap-3 mt-3">
            <div>
              <label className="form-label mb-1">From</label>
              <input type="date" className="form-control" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </div>
            <div>
              <label className="form-label mb-1">To</label>
              <input type="date" className="form-control" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </div>
          </div>
        )}
        {rangeError && <div className="alert alert-warning py-2 mt-3 mb-0">{rangeError}</div>}
      </div>

      {!rangeError && (
        <>
          <div className="row g-3 mb-3">
            <div className="col-6 col-lg-3"><div className="stat-card accent"><div className="stat-label">Net sales</div><div className="stat-value">{money(totalNet)}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat-card"><div className="stat-label">Confirmed orders</div><div className="stat-value">{totalOrders}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat-card"><div className="stat-label">Refunds</div><div className="stat-value">{money(totalRefunds)}</div></div></div>
            <div className="col-6 col-lg-3"><div className="stat-card"><div className="stat-label">Top seller</div><div className="stat-value" style={{ fontSize: "1.1rem" }}>{top ? nameOf(top) : "-"}</div></div></div>
          </div>

          <input type="search" inputMode="search" enterKeyHint="search" className="form-control mb-3" placeholder="Search staff" autoComplete="off" value={keyword} onChange={(e) => setKeyword(e.target.value)} />

          {shown.length === 0 && <p className="text-secondary">No orders in this period.</p>}

          {shown.length > 0 && (
            <div className="table-responsive d-none d-md-block">
              <table className="table table-dark align-middle">
                <thead>
                  <tr><th>Staff</th><th className="text-end">Orders</th><th className="text-end">Sales</th><th className="text-end">Refunds</th><th className="text-end">Net sales</th><th className="text-end">Cancelled</th><th style={{ width: "20%" }}>Share of net sales</th></tr>
                </thead>
                <tbody>
                  {shown.map((r) => {
                    const pct = totalNet > 0 ? Math.max(0, (r.net / totalNet) * 100) : 0;
                    return (
                      <tr key={r.email}>
                        <td>{nameOf(r)}<div className="text-secondary small">{r.email}</div></td>
                        <td className="text-end">{r.orders}</td>
                        <td className="text-end">{money(r.sales)}</td>
                        <td className="text-end">{money(r.refunds)}</td>
                        <td className="text-end">{money(r.net)}</td>
                        <td className="text-end">{r.cancelled} {flag(r) && <span className="badge bg-danger">High</span>}</td>
                        <td>
                          <div className="progress" style={{ height: 8, background: "#2a2a2e" }}>
                            <div className="progress-bar" style={{ width: pct + "%", background: "#ff4d1c" }} />
                          </div>
                          <div className="text-secondary small">{pct.toFixed(1)}%</div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div className="d-md-none">
            {shown.map((r) => (
              <div className="product-card" key={r.email}>
                <div className="d-flex justify-content-between">
                  <strong>{nameOf(r)}</strong>
                  <strong>{money(r.net)}</strong>
                </div>
                <div className="text-secondary small">{r.email}</div>
                <div className="d-flex justify-content-between mt-2">
                  <span>{r.orders} orders · sales {money(r.sales)}</span>
                  <span className={flag(r) ? "text-danger" : ""}>{r.cancelled} cancelled</span>
                </div>
                <div className="text-secondary small">Refunds {money(r.refunds)}</div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}