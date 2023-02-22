import { useEffect } from "react";

import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { AppHeader } from "@features/Explorer/components";
import FoldersList from "@features/Explorer/components/FoldersList/FoldersList";
import ResourcesList from "@features/Explorer/components/ResourcesList/ResourcesList";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import {
  AppCard,
  Button,
  Grid,
  // FormControl,
  // Input,
  IconButton,
  // SearchButton,
  useOdeClient,
  AppIcon,
  Library,
} from "@ode-react-ui/core";
import { ArrowLeft, Plus } from "@ode-react-ui/icons";
import { imageBootstrap } from "@shared/constants";
import useExplorerStore from "@store/index";

/* const SearchForm = () => {
  const { i18n } = useOdeClient();

  return (
    <form noValidate className="bg-light p-16 ps-24 ms-n16 ms-lg-n24 me-n16">
      <FormControl id="search" className="input-group">
        <Input
          type="search"
          placeholder={i18n("explorer.label.search")}
          size="lg"
          noValidationIcon
        />
        <SearchButton
          type="submit"
          aria-label={i18n("explorer.label.search")}
        />
      </FormControl>
    </form>
  );
}; */

export default function Explorer() {
  const { i18n, params, app, appCode, session, getBootstrapTheme } =
    useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const {
    actions,
    init,
    isReady,
    getHasResourcesOrFolders,
    getIsTrashSelected,
    getHasNoSelectedNodes,
    gotoPreviousFolder,
    hasMoreResources,
    getMoreResources,
    getPreviousFolder,
    getHasSelectedRoot,
    createResource,
  } = useExplorerStore((state) => state);

  const trashName: string = i18n("explorer.tree.trash");
  const rootName: string = i18n("explorer.filters.mine");
  const previousName: string = getPreviousFolder()?.name || rootName;
  const canPuslish = actions.find((action) => action.id === "publish");

  useEffect(() => {
    init(params);
  }, []);

  if (!isReady) {
    return <></>;
  }

  const profile = session?.profile;

  console.log(profile);

  return (
    <>
      <AppHeader>
        <AppCard app={app} isHeading headingStyle="h3" level="h1">
          <AppIcon app={app} size="40" />
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
          {i18n("explorer.create.title")}
        </Button>
      </AppHeader>
      <Grid>
        <Grid.Col
          sm="3"
          className="border-end pt-16 pe-16 d-none d-lg-block"
          as="aside"
        >
          <TreeViewContainer />
          {canPuslish?.available && (
            <Library
              src={`${getBootstrapTheme()}/images/image-library.svg`}
              alt={i18n("explorer.libray.img.alt")}
              text={i18n("explorer.libray.title")}
              url="#"
              textButton={i18n("explorer.libray.btn")}
            />
          )}
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="9">
          {/* <SearchForm /> */}
          <div className="py-16">
            {getHasNoSelectedNodes() ? (
              <h2 className="body py-8">
                {getIsTrashSelected() ? trashName : rootName}
              </h2>
            ) : (
              <div className="d-flex align-items-center gap-8">
                <IconButton
                  icon={<ArrowLeft />}
                  variant="ghost"
                  color="tertiary"
                  aria-label={i18n("back")}
                  className="ms-n16"
                  onClick={gotoPreviousFolder}
                />
                <p className="body py-8">
                  <strong>
                    {getHasSelectedRoot() ? rootName : previousName}
                  </strong>
                </p>
              </div>
            )}
          </div>
          {getHasResourcesOrFolders() ? (
            <>
              <FoldersList />
              <ResourcesList />
            </>
          ) : (
            <img
              src={`${imageBootstrap}/emptyscreen/illu-${appCode}.svg`}
              alt="application emptyscreen"
              className="mx-auto"
              style={{ maxWidth: "50%" }}
            />
          )}
          {!hasMoreResources ? (
            <div className="d-grid gap-2 col-4 mx-auto">
              <Button
                type="button"
                color="secondary"
                variant="filled"
                onClick={getMoreResources}
              >
                {i18n("explorer.see.more")}
              </Button>
            </div>
          ) : null}
        </Grid.Col>
        <ActionBarContainer />
      </Grid>
    </>
  );
}
