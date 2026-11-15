import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "../api";
import Brand from "../components/Brand";

function errText(err) {
  if (!err.response) return "Cannot reach the server. It may be waking up, so wait a minute and try again.";
  const d = err.response.data;
  if (d?.errors) return Object.values(d.errors).join(", ");
  return d?.message || "Something went wrong.";
}

export default function ForgotPassword() {
  const [step, setStep] = useState(1);
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [pw, setPw] = useState("");
  const [confirm, setConfirm] = useState("");
  const [show, setShow] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");
  const [loading, setLoading] = useState(false);
  const [left, setLeft] = useState(0);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (left <= 0) return;
    const t = setTimeout(() => setLeft((s) => s - 1), 1000);
    return () => clearTimeout(t);
  }, [left]);

  async function sendCode(e) {
    if (e) e.preventDefault();
    setError("");
    setInfo("");
    setLoading(true);
    try {
      await api.post("/auth/forgot-password", { email: email.trim() });
      setStep(2);
      setLeft(60);
      setInfo("If this is an administrator account, a 6-digit code was sent to its email. Check the spam folder too.");
    } catch (err) {
      setError(errText(err));
    } finally {
      setLoading(false);
    }
  }

  async function reset(e) {
    e.preventDefault();
    setError("");
    if (!/^\d{6}$/.test(otp)) return setError("Enter the 6-digit code.");
    if (pw.length < 8) return setError("The new password must be at least 8 characters.");
    if (pw !== confirm) return setError("The passwords do not match.");
    setLoading(true);
    try {
      await api.post("/auth/reset-password", { email: email.trim(), otp, newPassword: pw });
      setDone(true);
    } catch (err) {
      setError(errText(err));
    } finally {
      setLoading(false);
    }
  }

  const type = show ? "text" : "password";

  return (
    <div className="login-shell">
      <div className="login-panel">
        <div className="login-form">
          <div className="login-mobile-brand" style={{ display: "block" }}><Brand width={170} /></div>
          <h2 className="login-title text-center">Forgot password</h2>

          {done ? (
            <>
              <div className="alert alert-success py-2 mt-3">Password changed. You can sign in now.</div>
              <Link to="/login" className="btn btn-accent w-100 py-2">Back to sign in</Link>
            </>
          ) : (
            <>
              <div className="sub text-center">Administrator accounts only. We email a 6-digit code.</div>
              {error && <div className="alert alert-danger py-2">{error}</div>}
              {info && <div className="alert alert-success py-2">{info}</div>}

              {step === 1 && (
                <form onSubmit={sendCode}>
                  <label className="form-label">Admin email</label>
                  <input type="email" className="form-control mb-3" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="username" />
                  <button className="btn btn-accent w-100 py-2" disabled={loading}>{loading ? "Sending..." : "Send code"}</button>
                  <div className="login-note">Staff: ask your admin to reset your password.</div>
                </form>
              )}

              {step === 2 && (
                <form onSubmit={reset}>
                  <label className="form-label">6-digit code</label>
                  <input
                    className="form-control otp-input mb-3" inputMode="numeric" maxLength={6} autoComplete="one-time-code"
                    value={otp} onChange={(e) => setOtp(e.target.value.replace(/\D/g, ""))} required
                  />
                  <label className="form-label">New password (min 8 characters)</label>
                  <input type={type} className="form-control mb-3" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} required />
                  <label className="form-label">Confirm new password</label>
                  <input type={type} className="form-control mb-3" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
                  <div className="form-check mb-3">
                    <input id="showpw2" type="checkbox" className="form-check-input" checked={show} onChange={(e) => setShow(e.target.checked)} />
                    <label htmlFor="showpw2" className="form-check-label text-secondary">Show passwords</label>
                  </div>
                  <button className="btn btn-accent w-100 py-2" disabled={loading}>{loading ? "Saving..." : "Reset password"}</button>
                  <button type="button" className="btn btn-outline-light w-100 py-2 mt-2" disabled={left > 0 || loading} onClick={() => sendCode()}>
                    {left > 0 ? `Resend code (${left}s)` : "Resend code"}
                  </button>
                </form>
              )}

              <div className="text-center mt-3"><Link to="/login" className="forgot-link">Back to sign in</Link></div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}