import { useCallback, useEffect, useRef, useState } from "react";
import { ConfirmContext } from "../confirmContext";

export default function ConfirmProvider({ children }) {
  const [opts, setOpts] = useState(null);
  const resolver = useRef(null);

  const confirm = useCallback(
    (options) =>
      new Promise((resolve) => {
        resolver.current = resolve;
        setOpts(typeof options === "string" ? { message: options } : options);
      }),
    []
  );

  function close(result) {
    if (resolver.current) resolver.current(result);
    resolver.current = null;
    setOpts(null);
  }

  useEffect(() => {
    if (!opts) return;
    const onKey = (e) => {
      if (e.key === "Escape") close(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [opts]);

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {opts && (
        <div className="cd-overlay" onClick={() => close(false)}>
          <div className="cd-box" role="dialog" aria-modal="true" onClick={(e) => e.stopPropagation()}>
            <h5 className="cd-title">{opts.title || "Are you sure?"}</h5>
            <p className="cd-msg">{opts.message}</p>
            <div className="cd-actions">
              <button className="btn btn-outline-light" onClick={() => close(false)}>
                {opts.cancelText || "Cancel"}
              </button>
              <button
                autoFocus
                className={"btn " + (opts.danger ? "btn-danger" : "btn-accent")}
                onClick={() => close(true)}
              >
                {opts.confirmText || "Confirm"}
              </button>
            </div>
          </div>
        </div>
      )}
    </ConfirmContext.Provider>
  );
}