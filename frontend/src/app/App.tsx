import useOdeBackend from "@hooks/useOdeBackend";
import Blog from "@pages/Blog";
import Home from "@pages/Home";
import { Routes, Route } from "react-router-dom";

function App() {
  // TODO useContext
  const { session, login, logout } = useOdeBackend(null, null);

  if (session === null) {
    return <div>Impossible de se connecter au backend.</div>;
  }

  const loginForm = session.notLoggedIn ? (
    <button type="button" onClick={() => login(/* login, mdp */)}>
      Login
    </button>
  ) : (
    <button type="button" onClick={() => logout()}>
      Logout
    </button>
  );

  return (
    <div className="App">
      <div>{loginForm}</div>
      <Routes>
        <Route index element={<Home />} />
        <Route path="blog" element={<Blog />} />
      </Routes>
    </div>
  );
}

export default App;
