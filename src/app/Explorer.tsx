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
  EmptyScreen,
} from "@ode-react-ui/core";
import { ArrowLeft, Plus } from "@ode-react-ui/icons";
import { imageBootstrap } from "@shared/constants";
import { capitalizeFirstLetter } from "@shared/utils/capitalizeFirstLetter";
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

export default function Explorer(): JSX.Element | null {
  const { i18n, params, app, appCode, getBootstrapTheme, is1d } =
    useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const {
    actions,
    init,
    isAppReady,
    getHasResourcesOrFolders, // Return number folder or ressources
    getIsTrashSelected, // Return boolean : true if trash is selected, false other
    getHasNoSelectedNodes, // Return Boolean : true if we are NOT in a folder, false if we are in a folder
    gotoPreviousFolder, // Go to previous folder (onClick)
    hasMoreResources,
    getMoreResources,
    getPreviousFolder, // Return object informations previous folder (id, name, childNumber...) or return undefined if none previous folder
    getHasSelectedRoot, // Return Boolean : true if trash or folder default selected, false other
    createResource, // Create ressource (onClick)
    isActionAvailable,
  } = useExplorerStore((state) => state);

  useEffect(() => {
    init(params);
  }, []);

  const trashName: string = i18n("explorer.tree.trash");
  const rootName: string = i18n("explorer.filters.mine");
  const previousName: string = getPreviousFolder()?.name || rootName;
  const canPuslish = actions.find((action) => action.id === "publish");
  const canCreate = actions.find((action) => action.id === "create");

  const labelEmptyScreenApp = () => {
    if (canCreate?.available && is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.create");
    } else if (canCreate?.available && !is1d) {
      return i18n("explorer.emptyScreen.blog.txt2d.create");
    } else if (!canCreate?.available && is1d) {
      return i18n("explorer.emptyScreen.blog.txt1d.consultation");
    } else {
      return i18n("explorer.emptyScreen.blog.txt2d.consultation");
    }
  };

  console.count("Explorer render");

  return isAppReady ? (
    <>
      <AppHeader>
        <AppCard app={app} isHeading headingStyle="h3" level="h1">
          <AppIcon app={app} size="40" />
          <AppCard.Name />
        </AppCard>
        {isActionAvailable("create") && (
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
        )}
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
              url={`https://library.opendigitaleducation.com/search/?application%5B0%5D=${capitalizeFirstLetter(
                appCode,
              )}&page=1&sort_field=views&sort_order=desc`} // TODO : mettre ça dans une conf
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
          {getHasResourcesOrFolders() !== 0 ? (
            <>
              <FoldersList />
              <ResourcesList />
            </>
          ) : (
            <>
              {getHasResourcesOrFolders() === 0 &&
              !getHasSelectedRoot() &&
              !getIsTrashSelected() ? (
                <>
                  <EmptyScreen
                    imageSrc={`${imageBootstrap}/emptyscreen/illu-noContentInFolder.svg`}
                    imageAlt={i18n("explorer.emptyScreen.folder.empty.alt")}
                    text={i18n("explorer.emptyScreen.label")}
                  />
                </>
              ) : (
                <>
                  {getHasResourcesOrFolders() === 0 &&
                  getHasSelectedRoot() &&
                  !getIsTrashSelected() ? (
                    <>
                      <EmptyScreen
                        imageSrc={`${imageBootstrap}/emptyscreen/illu-${appCode}.svg`}
                        imageAlt={i18n("explorer.emptyScreen.app.alt")}
                        title={`${
                          canCreate?.available
                            ? i18n("explorer.emptyScreen.blog.title.create")
                            : i18n(
                                "explorer.emptyScreen.blog.title.consultation",
                              )
                        }`}
                        text={labelEmptyScreenApp()}
                      />
                    </>
                  ) : null}
                </>
              )}
            </>
          )}
          {getHasResourcesOrFolders() === 0 && getIsTrashSelected() ? (
            <>
              <EmptyScreen
                imageSrc={`${imageBootstrap}/emptyscreen/illu-trash.svg`}
                imageAlt={i18n("explorer.emptyScreen.trash.alt")}
                title={i18n("explorer.emptyScreen.trash.title")}
                text={i18n("explorer.emptyScreen.trash.empty")}
              />
            </>
          ) : null}

          {!hasMoreResources && getHasResourcesOrFolders() !== 0 ? (
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
  ) : null;
}
