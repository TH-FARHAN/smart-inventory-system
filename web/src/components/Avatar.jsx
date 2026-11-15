import { useEffect, useState } from "react";
import api from "../api";

export default function Avatar({ size = 40 }) {
  const [photo, setPhoto] = useState(localStorage.getItem("photo") || "");
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const initial = (user.name || "?").trim().charAt(0).toUpperCase();

  useEffect(() => {
    const sync = () => setPhoto(localStorage.getItem("photo") || "");
    window.addEventListener("photo-changed", sync);
    if (!localStorage.getItem("photo")) {
      api.get("/account/photo")
        .then((r) => {
          if (r.data && r.data.photo) {
            localStorage.setItem("photo", r.data.photo);
            setPhoto(r.data.photo);
          }
        })
        .catch(() => {});
    }
    return () => window.removeEventListener("photo-changed", sync);
  }, []);

  if (photo) {
    return <img src={photo} alt="" className="avatar" style={{ width: size, height: size }} />;
  }
  return (
    <div className="avatar avatar-fallback" style={{ width: size, height: size, fontSize: size * 0.42 }}>
      {initial}
    </div>
  );
}