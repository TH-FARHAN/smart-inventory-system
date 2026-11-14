import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import Products from "./pages/Products";
import Inventory from "./pages/Inventory";
import Layout from "./components/Layout";
import Customers from "./pages/Customers";
import Orders from "./pages/Orders";
import Users from "./pages/Users";
import Categories from "./pages/Categories";
import AuditLogs from "./pages/AuditLogs";
import Reports from "./pages/Reports";
import Profile from "./pages/Profile";
import StaffPerformance from "./pages/StaffPerformance";

function Protected({ children }) {
  return localStorage.getItem("token") ? children : <Navigate to="/login" replace />;
}

function AdminOnly({ children }) {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  return user.role === "ADMIN" ? children : <Navigate to="/" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/products" element={<Products />} />
        <Route path="/inventory" element={<Inventory />} />
        <Route path="/customers" element={<Customers />} />
        <Route path="/orders" element={<Orders />} />
        <Route path="/users" element={<AdminOnly><Users /></AdminOnly>} />
        <Route path="/categories" element={<AdminOnly><Categories /></AdminOnly>} />
        <Route path="/reports" element={<AdminOnly><Reports /></AdminOnly>} />
        <Route path="/audit-logs" element={<AdminOnly><AuditLogs /></AdminOnly>} />
        <Route path="/staff-performance" element={<AdminOnly><StaffPerformance /></AdminOnly>} />
        <Route path="/profile" element={<Profile />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}