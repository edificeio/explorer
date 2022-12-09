import { useExplorerContext } from "@contexts/ExplorerContext";
import { useOdeContext } from "@contexts/OdeContext";
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

  const { context } = useExplorerContext();
  // TODO initialize search parameters. Here and/or in the dedicated React component
  // context.getSearchParameters().pagination.pageSize = 50;

  // Do explore...
  context.initialize();
  // ...results are observable in latestResources
  /* TODO : il faudrait que le composant tree (ou son parent) s'abonne aux résultats de recherche,
            et adapte les données en conséquence.
    const { fakeData, setFakeData } = useState<IFolder & {section:boolean;}>({
      id: "root",
      name: "Section Element",
      section: true,
      children: [
        {
          id: "1",
          name: "level 1 arborescence tree",
          <div className="
  */

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
      <h1>{i18n("alttext.help")}</h1>
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
