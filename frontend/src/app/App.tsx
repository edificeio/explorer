import { useExplorerContext } from "@contexts/ExplorerContext";
import { useOdeContext } from "@contexts/OdeContext";
import useExplorerAdapter from "@hooks/adapters/explorer/useExplorerAdapter";
import useI18n from "@hooks/useI18n";
import { TreeView } from "@ode-react-ui/advanced";
import { AppCard, Button, Header } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
// import i18n from "i18n";
// import Blog from "@pages/Blog";
// import Home from "@pages/Home";
// import { Routes, Route } from "react-router-dom";

function App() {
  const { session } = useOdeContext();
  const { i18n } = useI18n();
  const { context } = useExplorerContext();
  const { treeData, listData } = useExplorerAdapter(context);

  if (!session || session.notLoggedIn) {
    return (
      <div>
        <a href="http://localhost:8090/" target="_blank" rel="noreferrer">
          S'identifier
        </a>
        sur le backend...
      </div>
    );
  }

  // TODO initialize search parameters. Here and/or in the dedicated React component
  context.getSearchParameters().pagination.pageSize = 1;
  // Do explore...
  context.initialize();
  // ...results (latestResources()) are observed in treeview adapter
  //

  function searchMore() {
    context.getResources();
  }

  return (
    <div className="App">
      <Header />
      <main className="bg-white container">
        <div className="container-fluid d-flex justify-content-between py-16 border-bottom">
          <AppCard
            app={{
              address: "/blog",
              icon: "blog-large",
              name: i18n("blog"),
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
            {i18n("blog.create.title")}
          </Button>
        </div>
        <div className="container">
          <div className="row">
            <button type="button" onClick={() => searchMore()}>
              Voir plus
            </button>
          </div>
          <div className="row">
            <div className="col-4">
              <TreeView data={treeData} />
            </div>
            <div className="col-8">
              <ul>
                {listData
                  .map((r) => (
                    <li>
                      Nom={r.name}, id={r.id}, thumbnail={r.thumbnail}
                    </li>
                  ))
                  .join("\r")}
              </ul>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;
