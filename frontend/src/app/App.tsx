/* import { useLayoutEffect } from "react"; */

import Blog from "@pages/Blog";
import Home from "@pages/Home";
/* import { configure } from "ode-ts-client/src/ts/configure/Framework";
import { session } from "ode-ts-client/src/ts/session/Framework"; */
import { Routes, Route } from "react-router-dom";

function App() {
  // useLayoutEffect(() => {
  //   session.initialize().then(() => {
  //     console.log("test");
  //   });
  //   // configure.initialize(null, null);
  //   /* session
  //     .initialize()
  //     .then(() => configure.initialize(null, null))
  //     .then(() => {
  //       console.log("test");
  //     }); */
  // }, []);
  // console.log(handleSession);

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
