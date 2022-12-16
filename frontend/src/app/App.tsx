import React, { useEffect, useRef } from "react";

import "../index.css";

import AppHeader from "@components/AppHeader";
import { useExplorerContext } from "@contexts/ExplorerContext";
import { useOdeContext } from "@contexts/OdeContext";
import useExplorerAdapter from "@hooks/adapters/explorer/useExplorerAdapter";
import useI18n from "@hooks/useI18n";
import { TreeView } from "@ode-react-ui/advanced";
import {
  AppCard,
  Button,
  FormControl,
  Grid,
  Header,
  Input,
  SearchButton,
} from "@ode-react-ui/core";
import { Plus, Users } from "@ode-react-ui/icons";
import { OneProfile } from "@ode-react-ui/icons/nav";

import libraryIMG from "../assets/images/library.jpg";
// import i18n from "i18n";
// import Blog from "@pages/Blog";
// import Home from "@pages/Home";
// import { Routes, Route } from "react-router-dom";

function App() {
  const { session, currentApp } = useOdeContext();
  const { i18n } = useI18n();
  const { context, onOpen, onCreate } = useExplorerContext();
  const { treeData, listData } = useExplorerAdapter();

  useEffect(() => {
    // TODO initialize search parameters. Here and/or in the dedicated React component
    context.getSearchParameters().pagination.pageSize = 1;
    // Do explore...
    context.initialize();
    // ...results (latestResources()) are observed in treeview adapter
    //
  }, []);

  // Form
  const formRef = useRef(null);

  function handleOnSubmit(e: React.FormEvent) {
    e.preventDefault();
  }

  function handleKeyDown(assetId: string) {
    window.addEventListener("keydown", () => {
      onOpen(assetId);
    });
    /* if (e.keyCode === 13) {
      onOpen(item?.assetId);
    } */
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
              {listData.map((item) => {
                return (
                  <div
                    key={item?.assetId}
                    className="card g-col-4 shadow border-0"
                    role="button"
                    tabIndex={0}
                    onClick={() => onOpen(item?.assetId)}
                    onKeyDown={() => handleKeyDown(item?.assetId)}
                  >
                    <div className="card-body p-16 d-flex align-items-center gap-12">
                      <AppCard
                        app={currentApp}
                        className="rounded-2 d-flex align-items-center justify-content-center"
                        style={{
                          width: "80px",
                          height: "80px",
                          backgroundColor: "#DDE8FD",
                        }}
                      >
                        <AppCard.Icon size="48" />
                      </AppCard>
                      <div>
                        <h3 className="card-title body">
                          <strong>
                            {item?.name} ({item?.application})
                          </strong>
                        </h3>
                        <span className="card-text small">
                          <em>{item?.updatedAt}</em>
                        </span>
                      </div>
                    </div>
                    <div className="card-footer py-8 px-16 bg-light rounded-2 m-2 border-0 d-flex align-items-center justify-content-between">
                      <div className="d-inline-flex align-items-center gap-8">
                        <OneProfile />
                        <p className="small">{item?.creatorName}</p>
                      </div>
                      <p className="d-inline-flex align-items-center gap-4 caption">
                        <Users width={16} height={16} /> <strong>23</strong>
                      </p>
                    </div>
                  </div>
                );
              })}
            </ul>
          </Grid.Col>
          <div className="row">
            <button type="button" onClick={handleViewMore}>
              Voir plus
            </button>
          </div>
        </Grid>
      </main>
    </div>
  );
}

export default App;
