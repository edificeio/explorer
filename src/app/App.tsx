import React, { useEffect, useRef } from "react";

import "../index.css";

import AppHeader from "@components/AppHeader";
import FakeCard from "@components/FakeCard";
import FakeToaster from "@components/FakeToaster";
import { useExplorerContext } from "@contexts/ExplorerContext";
import { useOdeContext } from "@contexts/OdeContext";
import useExplorerAdapter from "@hooks/adapters/explorer/useExplorerAdapter";
import useI18n from "@hooks/useI18n";
import {
  AppCard,
  Button,
  FormControl,
  Grid,
  Header,
  Input,
  SearchButton,
  TreeView,
} from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { IResource } from "ode-ts-client";

import libraryIMG from "../assets/images/library.jpg";

function App() {
  const { i18n } = useI18n();
  const { context, selectResource, onOpen, onCreate, isResourceSelected } =
    useExplorerContext();
  const { session, currentApp } = useOdeContext();
  const { treeData, listData } = useExplorerAdapter();

  useEffect(() => {
    // TODO initialize search parameters. Here and/or in the dedicated React component
    context.getSearchParameters().pagination.pageSize = 2;
    // Do explore...
    context.initialize();
    // ...results (latestResources()) are observed in treeview adapter
  }, []);

  // Form
  const formRef = useRef(null);

  function handleOnSubmit(e: React.FormEvent) {
    e.preventDefault();
  }

  function handleSelect(item: IResource) {
    selectResource(item);
    onOpen();
  }

  function handleKeyDown(item: IResource) {
    window.addEventListener("keydown", () => {
      handleSelect(item);
    });
  }

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

  function handleViewMore() {
    context.getResources();
  }

  return (
    <div className="App">
      <Header />
      <main className="container-fluid bg-white">
        <AppHeader>
          <AppCard app={currentApp} isHeading headingStyle="h3" level="h1">
            <AppCard.Icon size="40" />
            <AppCard.Name />
          </AppCard>
          <Button
            type="button"
            color="primary"
            variant="filled"
            leftIcon={<Plus />}
            className="ms-auto"
            onClick={onCreate}
          >
            {i18n("blog.create.title")}
          </Button>
        </AppHeader>
        <Grid>
          <Grid.Col
            sm="3"
            className="border-end pt-16 pe-16 d-none d-lg-block"
            as="aside"
          >
            <TreeView data={treeData} />
            <div className="d-grid my-16">
              <Button
                type="button"
                color="primary"
                variant="outline"
                leftIcon={<Plus />}
              >
                {i18n("blog.folder.new")}
              </Button>
            </div>
            <div>
              <img src={libraryIMG} alt="ODE Library" />
            </div>
          </Grid.Col>
          <Grid.Col sm="4" md="8" lg="9">
            <form
              ref={formRef}
              noValidate
              className="bg-light p-16 ps-24 ms-n24 me-n16"
              onSubmit={handleOnSubmit}
            >
              <FormControl id="search" className="input-group">
                <Input
                  type="search"
                  placeholder={i18n("label.search")}
                  size="lg"
                  noValidationIcon
                />
                <SearchButton type="submit" aria-label={i18n("label.search")} />
              </FormControl>
            </form>
            <h2 className="py-24 body">{i18n("filters.mine")}</h2>
            <ul className="grid ps-0">
              {listData.map((item: any) => {
                return (
                  <FakeCard
                    key={item.assetId}
                    // eslint-disable-next-line react/jsx-props-no-spreading
                    {...item}
                    currentApp={currentApp}
                    className={isResourceSelected(item) ? "selected" : ""}
                    onClick={() => handleSelect(item)}
                    onKeyDown={() => handleKeyDown(item)}
                  />
                );
              })}
            </ul>
            <div className="d-grid">
              <Button
                type="button"
                color="secondary"
                variant="filled"
                // eslint-disable-next-line react/jsx-no-bind
                onClick={handleViewMore}
              >
                Voir plus
              </Button>
            </div>
          </Grid.Col>
          <br />
          Toaster below
          <hr />
          <FakeToaster />
        </Grid>
      </main>
    </div>
  );
}

export default App;
