import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api";
import SalesCharts from "../components/SalesCharts";

const money = (n) => "\u20B9" + Number(n || 0).toLocaleString("en-IN");
const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");
const todayUtc = () => new Date().toISOString().slice(0, 10);

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  return err.response.data?.message || "Something went wrong.";
}

function Stat({ label, value, accent, to }) {
  const card = (
    <div className={"stat-card" + (accent ? " accent" : "") + (to ? " clickable" : "")}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
    </div>
  );
  return (
    <div className="col-6 col-lg-3">
      {to ? <Link to={to} className="stat-link">{card}</Link> : card}
    </div>
  );
}

export default function Dashboard() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const isAdmin = user.role === "ADMIN";
  const [data, setData] = useState(null);
  const [returns, setReturns] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/dashboard")
      .then((r) => setData(r.data))
      .catch((err) => setError(errText(err)));
    api.get("/returns")
      .then((r) => setReturns(r.data))
      .catch(() => setReturns([]));
  }, []);

  const returnsToday = returns.filter((r) => (r.createdAt || "").slice(0, 10) === todayUtc()).length;
  const totalRefunded = returns.reduce((s, r) => s + Number(r.refundAmount || 0), 0);
  const itemsText = (r) => (r.items || []).map((l) => `${l.quantity} \u00D7 ${l.productName}`).join(", ");

  return (
    <div>
      <h2 className="mb-1">Welcome, {user.name}</h2>
      <p className="text-secondary mb-4">Here is how the shop looks today. Tap a card to see the details.</p>

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {!data && !error && <p className="text-secondary">Loading...</p>}

      {data && (
        <>
          <div className="row g-3 mb-4">
            <Stat label="Total sales (after returns)" value={money(data.totalSales)} accent to={isAdmin ? "/reports" : "/orders"} />
            <Stat label="Today's orders" value={data.todaysOrders} to="/orders?filter=today&status=CONFIRMED" />
            <Stat label="Confirmed orders" value={data.totalOrders} to="/orders?status=CONFIRMED" />
            <Stat label="Customers" value={data.totalCustomers} to="/customers" />
            <Stat label="Products" value={data.totalProducts} to="/products" />
            <Stat label="Units in stock" value={data.totalStock} to="/products" />
            <Stat label="Low stock items" value={data.lowStockCount} accent={data.lowStockCount > 0} to="/inventory?tab=low" />
            <Stat label="Returns today" value={returnsToday} accent={returnsToday > 0} to="/returns" />
            <Stat label="Total refunded" value={money(totalRefunded)} to="/returns" />
          </div>

          {isAdmin && <SalesCharts />}

          <div className="row g-3">
            <div className="col-12 col-lg-7">
              <div className="product-card h-100">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <strong>Recent orders</strong>
                  <Link to="/orders" className="small">View all</Link>
                </div>
                {data.recentOrders.length === 0 && <p className="text-secondary m-0">No orders yet.</p>}
                {data.recentOrders.map((o) => (
                  <Link to="/orders" className="dash-row row-link" key={o.id}>
                    <div>
                      <div>#{o.id} &middot; {o.customerName}</div>
                      <div className="text-secondary small">{when(o.createdAt)}</div>
                    </div>
                    <div className="text-end">
                      <div>{money(o.totalAmount)}</div>
                      <span className={"badge " + (o.status === "CONFIRMED" ? "bg-success" : "bg-secondary")}>
                        {o.status}
                      </span>
                    </div>
                  </Link>
                ))}
              </div>
            </div>

            <div className="col-12 col-lg-5">
              <div className="product-card h-100">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <strong>Low stock</strong>
                  <Link to="/inventory?tab=low" className="small">Manage</Link>
                </div>
                {data.lowStockProducts.length === 0 && <p className="text-secondary m-0">All stock levels are fine.</p>}
                {data.lowStockProducts.map((p) => (
                  <Link to="/inventory?tab=low" className="dash-row row-link" key={p.id}>
                    <div>
                      <div>{p.name}</div>
                      <div className="text-secondary small">{p.sku}</div>
                    </div>
                    <div className="text-end">
                      <span className="text-danger fw-bold">{p.currentStock}</span>
                      <span className="text-secondary small"> / min {p.minimumStock}</span>
                    </div>
                  </Link>
                ))}
              </div>
            </div>

            <div className="col-12">
              <div className="product-card">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <strong>Recent returns</strong>
                  <Link to="/returns" className="small">View all</Link>
                </div>
                {returns.length === 0 && <p className="text-secondary m-0">No returns yet.</p>}
                {returns.slice(0, 5).map((r) => (
                  <Link to="/returns" className="dash-row row-link" key={r.id}>
                    <div>
                      <div>Return #{r.id} &middot; Order #{r.orderId} &middot; {r.customerName}</div>
                      <div className="small">{itemsText(r)}</div>
                      <div className="text-secondary small">{r.reason} &middot; {when(r.createdAt)}</div>
                    </div>
                    <div className="text-end">
                      <div className="text-danger fw-bold">-{money(r.refundAmount)}</div>
                    </div>
                  </Link>
                ))}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}