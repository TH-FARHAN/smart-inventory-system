import { useEffect, useState } from "react";
import api from "../api";
import { salesPdf, inventoryPdf } from "../pdfReports";

const money = (n) => "₹" + Number(n || 0).toLocaleString("en-IN");

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

function SalesReport() {
  const now = new Date();
  const [from, setFrom] = useState(toInput(new Date(now.getFullYear(), now.getMonth(), 1)));
  const [to, setTo] = useState(toInput(now));
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [keyword, setKeyword] = useState("");

  const badRange = from && to && from > to;

  useEffect(() => {
    if (badRange) return;
    let ignore = false;
    api.get("/reports/sales", { params: { ...(from && { from }), ...(to && { to }) } })
      .then((r) => { if (!ignore) { setData(r.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); });
    return () => { ignore = true; };
  }, [from, to, badRange]);

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

  const kw = keyword.trim().toLowerCase();
  const rows = (data?.byProduct || []).filter(
    (p) => !kw || p.productName.toLowerCase().includes(kw) || (p.sku || "").toLowerCase().includes(kw)
  );

  return (
    <>
      <div className="product-card mb-3">
        <div className="d-flex flex-wrap gap-2 mb-3">
          <button className="btn btn-sm btn-outline-light" onClick={thisMonth}>This month</button>
          <button className="btn btn-sm btn-outline-light" onClick={lastMonth}>Last month</button>
          <button className="btn btn-sm btn-outline-light" onClick={thisYear}>This year</button>
          <button
            className="btn btn-sm btn-accent ms-auto"
            disabled={!data || badRange}
            onClick={() => salesPdf(data, from, to)}
          >
            Download PDF
          </button>
        </div>
        <div className="d-flex flex-wrap gap-3">
          <div>
            <label className="form-label mb-1">From</label>
            <input type="date" className="form-control" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div>
            <label className="form-label mb-1">To</label>
            <input type="date" className="form-control" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        {badRange && <div className="alert alert-warning py-2 mt-3 mb-0">The From date must be before the To date.</div>}
      </div>

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {!data && !error && <p className="text-secondary">Loading...</p>}

      {data && (
        <>
          <div className="row g-3 mb-3">
            <Card label="Total sales" value={money(data.totalSales)} accent />
            <Card label="Orders" value={data.totalOrders} />
            <Card label="Average order" value={money(data.averageOrderValue)} />
          </div>

          <input
            type="search"
            inputMode="search"
            enterKeyHint="search"
            className="form-control mb-3"
            placeholder="Search products in this report"
            autoComplete="off"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />

          {rows.length === 0 && <p className="text-secondary">No sales in this period.</p>}

          {rows.length > 0 && (
            <div className="table-responsive d-none d-md-block">
              <table className="table table-dark align-middle">
                <thead>
                  <tr><th>Product</th><th>SKU</th><th className="text-end">Units sold</th><th className="text-end">Revenue</th></tr>
                </thead>
                <tbody>
                  {rows.map((p) => (
                    <tr key={p.productId}>
                      <td>{p.productName}</td>
                      <td>{p.sku}</td>
                      <td className="text-end">{p.quantitySold}</td>
                      <td className="text-end">{money(p.revenue)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="d-md-none">
            {rows.map((p) => (
              <div className="product-card" key={p.productId}>
                <strong>{p.productName}</strong>
                <div className="text-secondary small">{p.sku}</div>
                <div className="d-flex justify-content-between mt-2">
                  <span>{p.quantitySold} sold</span>
                  <strong>{money(p.revenue)}</strong>
                </div>
              </div>
            ))}
          </div>
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
      <div className="d-flex justify-content-end mb-3">
        <button className="btn btn-sm btn-accent" onClick={() => inventoryPdf(data)}>
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
        type="search"
        inputMode="search"
        enterKeyHint="search"
        className="form-control mb-3"
        placeholder="Search by name, SKU or category"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
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
          <div className="product-card" key={p.id}>
            <div className="d-flex justify-content-between">
              <strong>{p.name}</strong>
              {p.lowStock && <span className="badge bg-danger">Low</span>}
            </div>
            <div className="text-secondary small">{p.sku} · {p.categoryName || "No category"}</div>
            <div className="d-flex justify-content-between mt-2">
              <span>Stock: {p.currentStock} (min {p.minimumStock})</span>
              <span>{money(p.sellingPrice)}</span>
            </div>
            <div className="text-secondary small">Cost {money(p.purchasePrice)}</div>
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
      <div className="d-flex gap-2 mb-3">
        <button className={"btn btn-sm " + (tab === "sales" ? "btn-accent" : "btn-outline-light")} onClick={() => setTab("sales")}>Sales report</button>
        <button className={"btn btn-sm " + (tab === "inventory" ? "btn-accent" : "btn-outline-light")} onClick={() => setTab("inventory")}>Inventory report</button>
      </div>
      {tab === "sales" ? <SalesReport /> : <InventoryReport />}
    </div>
  );
}