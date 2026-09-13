import { useState } from "react";
import { Dashboard } from "./Dashboard";
import { LoginScreen } from "./LoginScreen";
import "./app.css";

const TOKEN_KEY = "localhand_admin_token";

function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(TOKEN_KEY));

  function handleLoggedIn(newToken: string) {
    localStorage.setItem(TOKEN_KEY, newToken);
    setToken(newToken);
  }

  function handleLogOut() {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
  }

  return token ? (
    <Dashboard token={token} onLogOut={handleLogOut} />
  ) : (
    <LoginScreen onLoggedIn={handleLoggedIn} />
  );
}

export default App;
