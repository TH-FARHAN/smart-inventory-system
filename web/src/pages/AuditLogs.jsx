import { useEffect, useState } from "react";
import api from "../api";

const when = (s) => (s ? new Date(s).toLocaleString("en-IN") : "-");

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so try again in a minute.";
  return err.response.data?.message || "Something went wrong.";
}

function badgeClass(action) {
  if (action.includes("SUCCESS") || action.includes("CREATED") || action.includes("ACTIVATED")) return "bg-success";
  if (action.includes("FAILED") || action.includes("LOCKED") || action.includes("BLOCKED") || action.includes("LIMITED") || action.includes("DEACTIVATED")) return "bg-danger";
  return "bg-secondary";
}

export default function AuditLogs() {
  const [logs, setLogs] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    let ignore = false;
    api.get("/admin/audit-logs")
      .then((r) => { if (!ignore) { setLogs(r.data); setError(""); } })
      .catch((err) => { if (!ignore) setError(errText(err)); })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [refreshKey]);

  const kw = keyword.trim().toLowerCase();
  const shown = logs.filter(
    (l) =>
      !kw ||
      (l.actor || "").toLowerCase().includes(kw) ||
      (l.action || "").toLowerCase().includes(kw) ||
      (l.details || "").toLowerCase().includes(kw) ||
      (l.ipAddress || "").toLowerCase().includes(kw)
  );

  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-1">
        <h2 className="m-0">Audit logs</h2>
        <button className="btn btn-outline-light btn-sm" onClick={() => setRefreshKey((k) => k + 1)}>Refresh</button>
      </div>
      <p className="text-secondary">The latest 100 security and activity events.</p>

      <input
        type="search"
        inputMode="search"
        enterKeyHint="search"
        className="form-control mb-3"
        placeholder="Search by user, action, details or IP"
        autoComplete="off"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
      />

      {error && <div className="alert alert-danger py-2">{error}</div>}
      {loading && <p className="text-secondary">Loading...</p>}
      {!loading && !error && shown.length === 0 && <p className="text-secondary">No log entries found.</p>}

      {shown.length > 0 && (
        <div className="table-responsive d-none d-md-block">
          <table className="table table-dark align-middle">
            <thead>
              <tr><th>Time</th><th>User</th><th>Action</th><th>Details</th><th>IP</th></tr>
            </thead>
            <tbody>
              {shown.map((l) => (
                <tr key={l.id}>
                  <td className="text-nowrap">{when(l.createdAt)}</td>
                  <td>{l.actor}</td>
                  <td><span className={"badge " + badgeClass(l.action)}>{l.action}</span></td>
                  <td>{l.details || "-"}</td>
                  <td className="text-nowrap">{l.ipAddress || "-"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="d-md-none">
        {shown.map((l) => (
          <div className="product-card" key={l.id}>
            <div className="d-flex justify-content-between align-items-start gap-2">
              <span className={"badge " + badgeClass(l.action)}>{l.action}</span>
              <span className="text-secondary small text-end">{when(l.createdAt)}</span>
            </div>
            <div className="mt-2" style={{ wordBreak: "break-word" }}>{l.actor}</div>
            {l.details && <div className="text-secondary small">{l.details}</div>}
            <div className="text-secondary small">IP: {l.ipAddress || "-"}</div>
          </div>
        ))}
      </div>
    </div>
  );
}