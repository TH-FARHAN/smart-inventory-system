import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "bootstrap/dist/css/bootstrap.min.css";
import "./index.css";
import App from "./App.jsx";
import ConfirmProvider from "./components/ConfirmProvider";
import OnlineStatus from "./components/OnlineStatus";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <ConfirmProvider>
        <OnlineStatus />
        <App />
      </ConfirmProvider>
    </BrowserRouter>
  </StrictMode>
);