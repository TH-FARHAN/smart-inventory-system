import { useEffect, useState } from "react";

export default function OnlineStatus() {
  const [online, setOnline] = useState(navigator.onLine);
  const [showBack, setShowBack] = useState(false);

  useEffect(() => {
    let timer;
    const goOffline = () => {
      clearTimeout(timer);
      setShowBack(false);
      setOnline(false);
    };
    const goOnline = () => {
      setOnline(true);
      setShowBack(true);
      timer = setTimeout(() => setShowBack(false), 3000);
    };
    window.addEventListener("offline", goOffline);
    window.addEventListener("online", goOnline);
    return () => {
      clearTimeout(timer);
      window.removeEventListener("offline", goOffline);
      window.removeEventListener("online", goOnline);
    };
  }, []);

  if (online && !showBack) return null;
  return (
    <div className={"net-toast " + (online ? "ok" : "off")} role="status">
      {online ? "Back online" : "No internet connection. Please check your network."}
    </div>
  );
}