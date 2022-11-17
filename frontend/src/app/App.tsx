import useOdeBackend from "@hooks/useOdeBackend";
import Blog from "@pages/Blog";
import Home from "@pages/Home";
import { Routes, Route } from "react-router-dom";

function App() {
  // TODO useContext
  const { session } = useOdeBackend(null, null);

  if (session === null) {
    // TODO améliorer cette gestion d'erreur
    return <div>Impossible de se connecter au backend.</div>;
  }

  return (
    <div className="App">
      <Routes>
        <Route index element={<Home />} />
        <Route path="blog" element={<Blog />} />
      </Routes>
    </div>
  );
}

export default App;
