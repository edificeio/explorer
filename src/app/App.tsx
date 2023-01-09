import React, { useEffect, useRef } from "react";

import { AppHeader, FakeCard } from "@components/index";
import { clsx } from "@config/index";
import { useExplorerContext, useOdeContext } from "@contexts/index";
import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import useActionBar from "@features/Actionbar/hooks/useActionBar";
import useExplorerAdapter from "@features/Explorer/hooks/useExplorerAdapter";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import { useI18n } from "@hooks/index";
import {
  AppCard,
  Button,
  FormControl,
  Grid,
  Header,
  Input,
  Modal,
  SearchButton,
  TreeView,
} from "@ode-react-ui/core";
import { useModal } from "@ode-react-ui/hooks";
import { Plus } from "@ode-react-ui/icons";
import { IResource } from "ode-ts-client";

import libraryIMG from "../assets/images/library.jpg";

function App() {
  /* i18n @hook */
  const { i18n } = useI18n();
  /* explorer @hook */
  const {
    context,
    selectResource,
    deselectResource,
    createResource,
    isResourceSelected,
  } = useExplorerContext();
  /* actionbar @hook */
  const { isActionBarOpen } = useActionBar();
  /* ode context @hook */
  const { session, currentApp, is1D, themeBasePath } = useOdeContext();
  /* feature explorer @hook */
  const { treeData, listData } = useExplorerAdapter();

  const { isOpen, toggle: toggleModal } = useModal(false);

  useEffect(() => {
    // TODO initialize search parameters. Here and/or in the dedicated React component
    context.getSearchParameters().pagination.pageSize = 4;
    context.getSearchParameters().filters.folder = "default";
    // Do explore...
    context.initialize();

    // ...results (latestResources()) are observed in treeview adapter
  }, []);

  // Form
  const formRef = useRef(null);

  function handleOnSubmit(e: React.FormEvent): void {
    e.preventDefault();
  }

  function toggleSelect(item: IResource) {
    if (isResourceSelected(item)) {
      deselectResource(item);
    } else {
      selectResource(item);
    }
  }

  function handleViewMore() {
    context.getResources();
  }

  function handleCloseModal() {
    toggleModal(false);
  }

  const mainClasses = clsx("container-fluid bg-white", {
    "rounded-4 border": is1D,
    "mt-24": is1D,
  });

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

  return (
    <div className="App">
      <Header is1d={is1D} src={`${themeBasePath}/img/illustrations/logo.png`} />
      <main className={mainClasses}>
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
            onClick={createResource}
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
            <TreeViewContainer />
            <div>
              <img src={libraryIMG} alt="ODE Library" />
            </div>
          </Grid.Col>
          <Grid.Col sm="4" md="8" lg="9">
            <form
              ref={formRef}
              noValidate
              className="bg-light p-16 ps-24 ms-n16 ms-lg-n24 me-n16"
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
                    {...item}
                    currentApp={currentApp}
                    selected={isResourceSelected(item)}
                    onClick={() => toggleSelect(item)}
                    onKeyDown={() => toggleSelect(item)}
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
          <ActionBarContainer isOpen={isActionBarOpen} />
        </Grid>
      </main>
      <Modal id="move" isOpen={isOpen} onModalClose={handleCloseModal}>
        <Modal.Header onModalClose={handleCloseModal}>Déplacer</Modal.Header>
        <Modal.Subtitle>
          Sélectionner le dossier vers lequel déplacer les éléments
        </Modal.Subtitle>
        <Modal.Body>
          <TreeView data={treeData} />

          <div className="mt-48">
            <FormControl id="folder-name" className="d-flex gap-8">
              <Input type="text" placeholder="Saisir un nom" size="md" />
              <Button
                color="primary"
                onClick={() => {}}
                type="button"
                leftIcon={<Plus />}
                variant="ghost"
                className="text-nowrap"
              >
                Créer dossier
              </Button>
            </FormControl>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button
            type="button"
            color="tertiary"
            variant="ghost"
            onClick={handleCloseModal}
          >
            Annuler
          </Button>
          <Button
            type="button"
            color="primary"
            variant="filled"
            onClick={handleCloseModal}
          >
            Déplacer
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
}

export default App;
