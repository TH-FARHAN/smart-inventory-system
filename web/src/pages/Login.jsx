import { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../api";
import Brand from "../components/Brand";

const ROLES = {
  ADMIN: { label: "Admin", title: "Admin sign in", hint: "Owner or manager access" },
  STAFF: { label: "Staff", title: "Staff sign in", hint: "Cashier or store team access" },
};

export default function Login() {
  const navigate = useNavigate();
  const [role, setRole] = useState("ADMIN");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [captcha, setCaptcha] = useState(null);
  const [answer, setAnswer] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  function pickRole(r) {
    setRole(r);
    setError("");
    setCaptcha(null);
    setAnswer("");
  }

  async function loadCaptcha() {
    try {
      const res = await api.get("/auth/captcha");
      setCaptcha(res.data);
      setAnswer("");
    } catch {
      setError("Could not load the captcha. Please try again.");
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const body = { email, password };
      if (captcha) {
        body.captchaId = captcha.captchaId;
        body.captchaAnswer = answer;
      }
      const res = await api.post("/auth/login", body);
      const { token, name, role: realRole } = res.data;

      if (realRole !== role) {
        setError(
          `This is ${realRole === "ADMIN" ? "an Admin" : "a Staff"} account. Choose the ${ROLES[realRole].label} tab.`
        );
        setPassword("");
        return;
      }

      localStorage.setItem("token", token);
      localStorage.setItem("user", JSON.stringify({ name, email: res.data.email, role: realRole }));
      navigate("/");
    } catch (err) {
      if (!err.response) {
        setError("Cannot reach the server. It may be waking up, so wait a minute and try again.");
      } else {
        const status = err.response.status;
        const msg = err.response.data?.message;
        if (status === 428) {
          setError(
            msg === "Captcha incorrect"
              ? "Wrong captcha answer. Try the new one."
              : "Please solve the captcha to continue."
          );
          await loadCaptcha();
        } else if (status === 429) {
          setError(msg || "Too many attempts. Please wait and try again.");
        } else if (status === 400) {
          setError("Please enter a valid email and password.");
        } else {
          setError(msg || "Login failed.");
          if (captcha) await loadCaptcha();
        }
      }
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-shell">
      <div className="login-hero">
        <div className="login-hero-inner">
            <Brand width={300} />
          <p>Stock, customers and orders for your supermarket, all in one place.</p>
          <ul className="login-points">
            <li>Live stock and low-stock alerts</li>
            <li>Fast billing and order tracking</li>
            <li>Sales reports and activity logs</li>
          </ul>
        </div>
      </div>

      <div className="login-panel">
        <form className="login-form" onSubmit={handleSubmit}>
            <div className="login-mobile-brand"><Brand width={170} /></div>

          <div className="role-toggle" role="tablist">
            {Object.entries(ROLES).map(([key, r]) => (
              <button
                key={key}
                type="button"
                role="tab"
                aria-selected={role === key}
                className={"role-btn" + (role === key ? " active" : "")}
                onClick={() => pickRole(key)}
              >
                {r.label}
              </button>
            ))}
          </div>

          <h2 className="login-title">{ROLES[role].title}</h2>
          <div className="sub">{ROLES[role].hint}</div>

          {error && <div className="alert alert-danger py-2">{error}</div>}

          <div className="mb-3">
            <label className="form-label">Email</label>
            <input
              type="email"
              className="form-control"
              autoComplete="username"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>

          <div className="mb-3">
            <label className="form-label">Password</label>
            <div className="pw-wrap">
              <input
                type={showPw ? "text" : "password"}
                className="form-control"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
              <button type="button" className="pw-toggle" onClick={() => setShowPw(!showPw)}>
                {showPw ? "Hide" : "Show"}
              </button>
            </div>
          </div>

          {captcha && (
            <div className="captcha-box mb-3">
              <label className="form-label">Captcha: {captcha.question}</label>
              <input
                type="text"
                inputMode="numeric"
                className="form-control"
                value={answer}
                onChange={(e) => setAnswer(e.target.value)}
                required
              />
            </div>
          )}

          <button type="submit" className="btn btn-accent w-100 py-2" disabled={loading}>
            {loading ? "Signing in..." : `Sign in as ${ROLES[role].label}`}
          </button>

          <div className="login-note">Accounts are created by an Admin. There is no public sign-up.</div>
        </form>
      </div>
    </div>
  );
}