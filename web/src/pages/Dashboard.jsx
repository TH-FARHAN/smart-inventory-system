import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api";
import SalesCharts from "../components/SalesCharts";

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");
const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  return err.response.data?.message || "Something went wrong.";
}

function Stat({ label, value, accent }) {
  return (
    <div className="col-6 col-lg-3">
      <div className={"stat-card" + (accent ? " accent" : "")}>
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const isAdmin = user.role === "ADMIN";
  const [data, setData] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.get("/dashboard")
      .then((r) => setData(r.data))
      .catch((err) => setError(errText(err)));
  }, []);

  return (
    <div>
      <h2 className="mb-1">Welcome, {user.name}</h2>
      <p className="text-secondary mb-4">Here is how the shop looks today.</p>

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {!data && !error && <p className="text-secondary">Loading...</p>}

      {data && (
        <>
          <div className="row g-3 mb-4">
            <Stat label="Total sales" value={money(data.totalSales)} accent />
            <Stat label="Today's orders" value={data.todaysOrders} />
            <Stat label="Confirmed orders" value={data.totalOrders} />
            <Stat label="Customers" value={data.totalCustomers} />
            <Stat label="Products" value={data.totalProducts} />
            <Stat label="Units in stock" value={data.totalStock} />
            <Stat label="Low stock items" value={data.lowStockCount} accent={data.lowStockCount > 0} />
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
                  <div className="dash-row" key={o.id}>
                    <div>
                      <div>#{o.id} · {o.customerName}</div>
                      <div className="text-secondary small">{when(o.createdAt)}</div>
                    </div>
                    <div className="text-end">
                      <div>{money(o.totalAmount)}</div>
                      <span className={"badge " + (o.status === "CONFIRMED" ? "bg-success" : "bg-secondary")}>
                        {o.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className="col-12 col-lg-5">
              <div className="product-card h-100">
                <div className="d-flex justify-content-between align-items-center mb-2">
                  <strong>Low stock</strong>
                  <Link to="/inventory" className="small">Manage</Link>
                </div>
                {data.lowStockProducts.length === 0 && <p className="text-secondary m-0">All stock levels are fine.</p>}
                {data.lowStockProducts.map((p) => (
                  <div className="dash-row" key={p.id}>
                    <div>
                      <div>{p.name}</div>
                      <div className="text-secondary small">{p.sku}</div>
                    </div>
                    <div className="text-end">
                      <span className="text-danger fw-bold">{p.currentStock}</span>
                      <span className="text-secondary small"> / min {p.minimumStock}</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}