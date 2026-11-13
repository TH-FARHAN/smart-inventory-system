import { useState } from "react";

export default function SearchSelect({ options, value, onChange, placeholder }) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);

  const selected = options.find((o) => String(o.value) === String(value));
  const q = query.trim().toLowerCase();
  const filtered = options.filter((o) => !q || o.label.toLowerCase().includes(q));

  function pick(o) {
    onChange(String(o.value));
    setQuery("");
    setOpen(false);
  }

  return (
    <div className="ss-wrap">
      <input
        type="search"
        inputMode="search"
        enterKeyHint="search"
        autoComplete="off"
        className="form-control"
        placeholder={selected ? selected.label : placeholder}
        value={open ? query : selected ? selected.label : ""}
        onFocus={() => { setOpen(true); setQuery(""); }}
        onChange={(e) => setQuery(e.target.value)}
        onBlur={() => setOpen(false)}
      />
      {open && (
        <div className="ss-list">
          {filtered.length === 0 && <div className="ss-empty">No matches</div>}
          {filtered.map((o) => (
            <div
              key={o.value}
              className={"ss-item" + (o.disabled ? " disabled" : "")}
              onMouseDown={(e) => {
                e.preventDefault();
                if (!o.disabled) pick(o);
              }}
            >
              {o.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}