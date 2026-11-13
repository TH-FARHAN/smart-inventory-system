import { useEffect, useState } from "react";
import {
  ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid,
} from "recharts";
import api from "../api";

const ACCENT = "#ff4d1c";
const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const short = (n) =>
  n >= 100000 ? (n / 100000).toFixed(1) + "L" : n >= 1000 ? (n / 1000).toFixed(1) + "k" : String(n);

const tooltipStyle = {
  contentStyle: { background: "#161618", border: "1px solid #2a2a2e", borderRadius: 8, color: "#f2f2f2" },
  labelStyle: { color: "#9a9aa3" },
  itemStyle: { color: "#f2f2f2" },
  cursor: { fill: "rgba(255,255,255,0.06)" },
};

const MAX_BUCKETS = 366;

function sod(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return x; }
function startOfWeek(d) { const x = sod(d); return addDays(x, -((x.getDay() + 6) % 7)); } // Monday
function parseDate(s) {
  if (!s) return null;
  const [y, m, d] = s.split("-").map(Number);
  return new Date(y, m - 1, d);
}
function toInput(d) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function bucketStart(d, g) {
  if (g === "day") return sod(d);
  if (g === "week") return startOfWeek(d);
  if (g === "month") return new Date(d.getFullYear(), d.getMonth(), 1);
  return new Date(d.getFullYear(), 0, 1);
}
function nextBucket(d, g) {
  if (g === "day") return addDays(d, 1);
  if (g === "week") return addDays(d, 7);
  if (g === "month") return new Date(d.getFullYear(), d.getMonth() + 1, 1);
  return new Date(d.getFullYear() + 1, 0, 1);
}
function labelOf(d, g) {
  if (g === "month") return d.toLocaleDateString("en-IN", { month: "short", year: "2-digit" });
  if (g === "year") return String(d.getFullYear());
  return d.toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

function getRange(preset, from, to) {
  const now = new Date();
  const today = sod(now);
  if (preset === "8w") return { start: addDays(startOfWeek(now), -49), end: today };
  if (preset === "6m") return { start: new Date(now.getFullYear(), now.getMonth() - 5, 1), end: today };
  if (preset === "year") return { start: new Date(now.getFullYear(), 0, 1), end: today };
  if (preset === "lastyear")
    return { start: new Date(now.getFullYear() - 1, 0, 1), end: new Date(now.getFullYear() - 1, 11, 31) };
  const s = parseDate(from);
  const e = parseDate(to);
  if (!s || !e) return null;
  return { start: s, end: e };
}

function sumRange(orders, start, end) {
  const lo = start.getTime();
  const hi = addDays(end, 1).getTime();
  let sales = 0;
  let count = 0;
  orders.forEach((o) => {
    const t = new Date(o.createdAt).getTime();
    if (t >= lo && t < hi) { sales += Number(o.totalAmount); count += 1; }
  });
  return { sales, count };
}

export default function SalesCharts() {
  const now = new Date();
  const [orders, setOrders] = useState(null);
  const [preset, setPreset] = useState("6m");
  const [group, setGroup] = useState("month");
  const [from, setFrom] = useState(toInput(new Date(now.getFullYear(), now.getMonth(), 1)));
  const [to, setTo] = useState(toInput(now));
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/orders")
      .then((r) => setOrders(r.data.filter((o) => o.status === "CONFIRMED")))
      .catch(() => setError("Could not load sales data."));
  }, []);

  function choose(p) {
    setPreset(p);
    if (p === "8w") setGroup("week");
    else if (p === "6m" || p === "year" || p === "lastyear") setGroup("month");
  }

  if (error) return <div className="alert alert-danger py-2">{error}</div>;
  if (!orders) return <p className="text-secondary">Loading sales charts...</p>;

  const presets = [
    ["8w", "Last 8 weeks"], ["6m", "Last 6 months"], ["year", "This year"],
    ["lastyear", "Last year"], ["custom", "Custom"],
  ];
  const groups = [["day", "Day"], ["week", "Week"], ["month", "Month"], ["year", "Year"]];

  const range = getRange(preset, from, to);
  let rangeError = "";
  if (!range) rangeError = "Pick both a From and a To date.";
  else if (range.start > range.end) rangeError = "The From date must be before the To date.";

  let data = [];
  let top = [];
  let cur = { sales: 0, count: 0 };
  let prev = { sales: 0, count: 0 };
  let change = null;
  let days = 0;

  if (!rangeError) {
    const { start, end } = range;
    days = Math.round((end - start) / 86400000) + 1;

    // build the empty bars
    const index = {};
    let c = bucketStart(start, group);
    while (c <= end && data.length <= MAX_BUCKETS) {
      const b = { key: c.getTime(), label: labelOf(c, group), sales: 0, orders: 0 };
      data.push(b);
      index[b.key] = b;
      c = nextBucket(c, group);
    }
    if (data.length > MAX_BUCKETS) {
      rangeError = "Too many bars for this range. Group by Week, Month or Year instead.";
      data = [];
    } else {
      const lo = start.getTime();
      const hi = addDays(end, 1).getTime();
      const byProduct = {};
      orders.forEach((o) => {
        const d = new Date(o.createdAt);
        const t = d.getTime();
        if (t < lo || t >= hi) return;
        const b = index[bucketStart(d, group).getTime()];
        if (b) { b.sales += Number(o.totalAmount); b.orders += 1; }
        o.items.forEach((l) => {
          byProduct[l.productName] = (byProduct[l.productName] || 0) + Number(l.subtotal);
        });
      });
      top = Object.entries(byProduct)
        .map(([name, sales]) => ({ name, sales }))
        .sort((a, b) => b.sales - a.sales)
        .slice(0, 5);

      cur = sumRange(orders, start, end);
      const prevEnd = addDays(start, -1);
      const prevStart = addDays(prevEnd, -(days - 1));
      prev = sumRange(orders, prevStart, prevEnd);
      change = prev.sales > 0 ? ((cur.sales - prev.sales) / prev.sales) * 100 : null;
    }
  }

  const avg = cur.count > 0 ? cur.sales / cur.count : 0;

  return (
    <div className="mb-4">
      <div className="product-card mb-3">
        <div className="d-flex flex-wrap gap-2 mb-3">
          {presets.map(([key, label]) => (
            <button
              key={key}
              className={"btn btn-sm " + (preset === key ? "btn-accent" : "btn-outline-light")}
              onClick={() => choose(key)}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="d-flex flex-wrap gap-3 align-items-end">
          {preset === "custom" && (
            <>
              <div>
                <label className="form-label mb-1">From</label>
                <input type="date" className="form-control" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
              </div>
              <div>
                <label className="form-label mb-1">To</label>
                <input type="date" className="form-control" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
              </div>
            </>
          )}
          <div>
            <label className="form-label mb-1">Group bars by</label>
            <div className="d-flex gap-1">
              {groups.map(([key, label]) => (
                <button
                  key={key}
                  className={"btn btn-sm " + (group === key ? "btn-accent" : "btn-outline-light")}
                  onClick={() => setGroup(key)}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {range && !rangeError && (
          <div className="text-secondary small mt-3">
            {range.start.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
            {" to "}
            {range.end.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}
            {" · "}{days} day{days === 1 ? "" : "s"}
          </div>
        )}
        {rangeError && <div className="alert alert-warning py-2 mt-3 mb-0">{rangeError}</div>}
      </div>

      {!rangeError && (
        <>
          <div className="row g-3 mb-3">
            <div className="col-6 col-lg-3">
              <div className="stat-card accent">
                <div className="stat-label">Sales in this period</div>
                <div className="stat-value">{money(cur.sales)}</div>
              </div>
            </div>
            <div className="col-6 col-lg-3">
              <div className="stat-card">
                <div className="stat-label">Previous period</div>
                <div className="stat-value">{money(prev.sales)}</div>
              </div>
            </div>
            <div className="col-6 col-lg-3">
              <div className="stat-card">
                <div className="stat-label">Change</div>
                <div className={"stat-value " + (change === null ? "" : change >= 0 ? "text-success" : "text-danger")}>
                  {change === null ? "-" : (change >= 0 ? "+" : "") + change.toFixed(1) + "%"}
                </div>
              </div>
            </div>
            <div className="col-6 col-lg-3">
              <div className="stat-card">
                <div className="stat-label">Orders · Avg order</div>
                <div className="stat-value">{cur.count} · {money(Math.round(avg))}</div>
              </div>
            </div>
          </div>

          <div className="product-card mb-3">
            <strong>Sales by {group}</strong>
            <div className="mt-3">
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={data} margin={{ top: 5, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid stroke="#2a2a2e" vertical={false} />
                  <XAxis dataKey="label" stroke="#9a9aa3" fontSize={12} minTickGap={16} />
                  <YAxis stroke="#9a9aa3" fontSize={12} width={42} tickFormatter={short} />
                  <Tooltip {...tooltipStyle} formatter={(v) => [money(v), "Sales"]} />
                  <Bar dataKey="sales" fill={ACCENT} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="product-card">
            <strong>Top products in this period</strong>
            {top.length === 0 ? (
              <p className="text-secondary m-0 mt-2">No sales in this period.</p>
            ) : (
              <ResponsiveContainer width="100%" height={40 + top.length * 42}>
                <BarChart data={top} layout="vertical" margin={{ top: 10, right: 8, left: 0, bottom: 0 }}>
                  <XAxis type="number" stroke="#9a9aa3" fontSize={12} tickFormatter={short} />
                  <YAxis type="category" dataKey="name" stroke="#9a9aa3" fontSize={12} width={90} />
                  <Tooltip {...tooltipStyle} formatter={(v) => [money(v), "Sales"]} />
                  <Bar dataKey="sales" fill={ACCENT} radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </>
      )}
    </div>
  );
}