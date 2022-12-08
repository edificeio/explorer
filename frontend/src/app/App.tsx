import { useOdeContext } from "@contexts/OdeContext";
import { TreeView } from "@ode-react-ui/advanced";
import { AppCard, Button, Header } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
// import Blog from "@pages/Blog";
// import Home from "@pages/Home";
// import { Routes, Route } from "react-router-dom";

function App() {
  const { session } = useOdeContext();

  if (!session || session.notLoggedIn) {
    return (
      <div>
        <a href="http://localhost:8090/" target="_blank" rel="noreferrer">
          S'identifier
        </a>
        sur le backend...
        <button type="button">OK</button>
      </div>
    );
  }

  const fakeData = {
    id: "root",
    name: "Section Element",
    section: true,
    children: [
      {
        id: "1",
        name: "level 1 arborescence tree",
        children: [
          {
            id: "4",
            name: "level 2 arborescence tree",
            children: [
              {
                id: "8",
                name: "level 3 arborescence tree",
                children: [
                  {
                    id: "12",
                    name: "level 4 arborescence tree",
                  },
                  {
                    id: "13",
                    name: "level 4 arborescence tree",
                  },
                ],
              },
              {
                id: "9",
                name: "level 3 arborescence tree",
              },
            ],
          },
          {
            id: "5",
            name: "level 2 arborescence tree",
            children: [
              {
                id: "10",
                name: "level 3 arborescence tree",
              },
              {
                id: "11",
                name: "level 3 arborescence tree",
              },
            ],
          },
        ],
      },
      {
        id: "2",
        name: "level 1 arborescence tree",
        children: [
          {
            id: "6",
            name: "level 2 arborescence tree",
          },
          {
            id: "7",
            name: "level 2 arborescence tree",
          },
        ],
      },
      {
        id: "3",
        name: "level 1 arborescence tree",
      },
    ],
  };

  return (
    <div className="App">
      <Header />
      <main className="bg-white container">
        <div className="container-fluid d-flex justify-content-between py-16 border-bottom">
          <AppCard
            app={{
              address: "/blog",
              icon: "blog-large",
              name: "Blog",
              scope: [],
              display: false,
              displayName: "",
              isExternal: false,
            }}
            isHeading
            headingStyle="h3"
            level="h1"
          >
            <AppCard.Icon size="40" />
          </AppCard>
          <Button
            type="button"
            color="primary"
            variant="filled"
            leftIcon={<Plus />}
          >
            Nouveau Blog
          </Button>
        </div>
        <div className="container">
          <div className="row">
            <div className="col-4">
              <TreeView data={fakeData} />
            </div>
            <div className="col-8">NOTHING FOUND</div>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
