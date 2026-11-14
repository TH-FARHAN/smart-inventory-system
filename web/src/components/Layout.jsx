import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import api from "../api";
import Brand from "./Brand";

export default function Layout() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const isAdmin = user.role === "ADMIN";

  // add new pages here later; adminOnly pages are hidden from STAFF
  const links = [
    { to: "/", label: "Dashboard", end: true },
    { to: "/products", label: "Products" },
    { to: "/inventory", label: "Inventory" },
    { to: "/customers", label: "Customers" },
    { to: "/orders", label: "Orders" },
    { to: "/returns", label: "Returns" },
    { to: "/profile", label: "Profile" },
    { to: "/users", label: "Users", adminOnly: true },
    { to: "/categories", label: "Categories", adminOnly: true },
    { to: "/reports", label: "Reports", adminOnly: true },
    { to: "/audit-logs", label: "Audit logs", adminOnly: true },
    { to: "/suppliers", label: "Suppliers", adminOnly: true },
    { to: "/staff-performance", label: "Staff performance", adminOnly: true },
  ].filter((l) => !l.adminOnly || isAdmin);

  async function logout() {
    try { await api.post("/auth/logout"); } catch { /* ignore */ }
    localStorage.clear();
    navigate("/login");
  }

  return (
    <div className="shell">
      {/* top bar: phones and tablets only */}
      <header className="topbar">
        <button className="menu-btn" onClick={() => setOpen(true)} aria-label="Open menu">
          ☰
        </button>
        <Brand variant="icon" width={36} />
      </header>

      {open && <div className="overlay" onClick={() => setOpen(false)} />}

      <aside className={"sidebar" + (open ? " open" : "")}>
        <div className="sidebar-head">
          <Brand width={150} />
          <button className="close-btn" onClick={() => setOpen(false)} aria-label="Close menu">
            ✕
          </button>
        </div>

        <nav className="side-links">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.end}
              className="side-link"
              onClick={() => setOpen(false)}
            >
              {l.label}
            </NavLink>
          ))}
        </nav>

        <div className="side-user">
          <div>
            <span className="app-role">{user.role}</span>
          </div>
          <div className="app-name">{user.name}</div>
          <button className="btn btn-sm btn-outline-light" onClick={logout}>
            Log out
          </button>
        </div>
      </aside>

      <main className="content">
        <div className="content-inner">
          <Outlet />
        </div>
      </main>
    </div>
  );
}