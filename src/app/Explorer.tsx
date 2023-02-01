/* eslint-disable @typescript-eslint/no-unused-vars */
import { useExplorerContext } from "@contexts/useExplorerContext";
import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import {
  AppCard,
  Button,
  Grid,
  FormControl,
  Input,
  IconButton,
  SearchButton,
} from "@ode-react-ui/core";
import { ArrowLeft, Plus } from "@ode-react-ui/icons";
import { AppHeader, EPub } from "@shared/components";
import FoldersList from "@shared/components/FoldersList/FoldersList";
import ResourcesList from "@shared/components/ResourcesList/ResourcesList";

export default function Explorer({
  currentLanguage,
}: {
  currentLanguage: string;
}) {
  const {
    app,
    getMoreResources,
    getHasResourcesOrFolders,
    session,
    ready,
    getIsTrashSelected,
    getHasNoSelectedNodes,
    getPreviousFolder,
    getHasSelectedRoot,
    getHasResources,
    i18n,
    gotoPreviousFolder,
    createResource,
  } = useExplorerContext();
  // must be before all
  if (!ready) {
    return <></>;
  }
  const trashName: string = i18n("explorer.tree.trash");
  const rootName: string = i18n("explorer.filters.mine");

  const previousName: string = getPreviousFolder()?.name || rootName;
  const appCode: string | undefined = app?.address.replace("/", "");
  // console.count("Explorer.tsx");
  return (
    <>
      <AppHeader>
        <AppCard app={app} isHeading headingStyle="h3" level="h1">
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
          <EPub
            src="/assets/themes/ode-bootstrap/images/image-library.png"
            alt="library"
            text="Découvrez plein d'activités à réutiliser dans la bibliothèque !"
            url=""
            linkText="Découvrir"
          />
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="9">
          <form
            noValidate
            className="bg-light p-16 ps-24 ms-n16 ms-lg-n24 me-n16"
          >
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
                  onClick={() => gotoPreviousFolder()}
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
              <ResourcesList
                app={app}
                session={session}
                currentLanguage={currentLanguage}
              />
            </>
          ) : (
            <img
              src={`/assets/themes/ode-bootstrap/images/emptyscreen/illu-${appCode}.svg`}
              alt="application emptyscreen"
              className="mx-auto"
              style={{ maxWidth: "50%" }}
            />
          )}
          {getHasResources() ? (
            <div className="d-grid">
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
