export default function Brand({ variant = "full", width }) {
  const src = variant === "icon" ? "/logo-icon.png" : "/logo-full.png";
  return (
    <img
      src={src}
      alt="BxoStock"
      className="brand-img"
      style={width ? { width } : undefined}
    />
  );
}