import { useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import api from "../api";
import Brand from "./Brand";

// groups shown as dropdowns; adminOnly items are hidden from STAFF
const GROUPS = [
  {
    key: "sales",
    label: "Sales",
    items: [
      { to: "/orders", label: "Orders" },
      { to: "/returns", label: "Returns" },
      { to: "/customers", label: "Customers" },
    ],
  },
  {
    key: "inventory",
    label: "Inventory",
    items: [
      { to: "/products", label: "Products" },
      { to: "/inventory", label: "Stock in / out" },
      { to: "/categories", label: "Categories", adminOnly: true },
      { to: "/suppliers", label: "Suppliers", adminOnly: true },
    ],
  },
  {
    key: "admin",
    label: "Admin",
    items: [
      { to: "/users", label: "Users", adminOnly: true },
      { to: "/reports", label: "Reports", adminOnly: true },
      { to: "/audit-logs", label: "Audit logs", adminOnly: true },
      { to: "/staff-performance", label: "Staff performance", adminOnly: true },
    ],
  },
];

export default function Layout() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  const [expanded, setExpanded] = useState({});
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const isAdmin = user.role === "ADMIN";

  const groups = GROUPS
    .map((g) => ({ ...g, items: g.items.filter((i) => !i.adminOnly || isAdmin) }))
    .filter((g) => g.items.length > 0);

  // a group is open if the user chose so, otherwise when it holds the current page
  function isOpen(g) {
    if (expanded[g.key] !== undefined) return expanded[g.key];
    return g.items.some((i) => pathname === i.to || pathname.startsWith(i.to + "/"));
  }

  function toggle(g) {
    setExpanded({ ...expanded, [g.key]: !isOpen(g) });
  }

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
          <NavLink to="/" end className="side-link" onClick={() => setOpen(false)}>
            Dashboard
          </NavLink>

          {groups.map((g) => {
            const o = isOpen(g);
            return (
              <div className="side-group" key={g.key}>
                <button
                  type="button"
                  className={"side-group-btn" + (o ? " open" : "")}
                  onClick={() => toggle(g)}
                  aria-expanded={o}
                >
                  <span>{g.label}</span>
                  <span className="chev" aria-hidden="true">▾</span>
                </button>
                 <div className={"side-sub-wrap" + (o ? " open" : "")}>
                  <div className="side-sub">
                    {g.items.map((l) => (
                      <NavLink
                        key={l.to}
                        to={l.to}
                        className="side-link sub"
                        onClick={() => setOpen(false)}
                      >
                        {l.label}
                      </NavLink>
                    ))}
                  </div>
                </div>
              </div>
            );
          })}

          <NavLink to="/profile" className="side-link" onClick={() => setOpen(false)}>
            Profile
          </NavLink>
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