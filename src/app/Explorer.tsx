/* eslint-disable @typescript-eslint/no-unused-vars */
import { useExplorerContext } from "@contexts/index";
import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import useTreeView from "@features/TreeView/hooks/useTreeView";
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
import { findNodeById } from "@shared/utils/findNodeById";
import { useSelectedNodesIds } from "@store/useOdeStore";

export default function Explorer({
  currentLanguage,
}: {
  currentLanguage: string;
}) {
  const selectedNodesIds = useSelectedNodesIds();

  const {
    contextRef,
    createResource,
    handleNextPage,
    state: { resources, folders, treeData },
    i18n,
    app,
    session,
    trashSelected,
  } = useExplorerContext();

  const { handleTreeItemPrevious } = useTreeView();

  const trashName = i18n("explorer.tree.trash");
  const rootName = i18n("explorer.filters.mine");
  const previousFolder = findNodeById(
    selectedNodesIds[selectedNodesIds.length - 2],
    treeData,
  );

  const previousId = previousFolder?.id;
  const previousName = previousFolder?.name || rootName;

  const hasNoSelectedNodes =
    selectedNodesIds?.length === 0 ||
    (selectedNodesIds.length === 1 && selectedNodesIds[0] === "default");
  const hasSelectedNodes = selectedNodesIds?.length === 1;

  const hasResourcesOrFolders = resources.length || folders.length;
  const hasResources = resources.length;

  const appCode = app?.address.replace("/", "");

  return contextRef.current.isInitialized() ? (
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
          <div className="py-24">
            {hasNoSelectedNodes ? (
              <h2 className="body">{trashSelected ? trashName : rootName}</h2>
            ) : (
              <div className="d-flex align-items-center gap-8">
                <IconButton
                  icon={<ArrowLeft />}
                  variant="ghost"
                  color="tertiary"
                  onClick={() => handleTreeItemPrevious(previousId as string)}
                />
                <p className="body">
                  <strong>{hasSelectedNodes ? rootName : previousName}</strong>
                </p>
              </div>
            )}
          </div>
          {hasResourcesOrFolders ? (
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
          {hasResources ? (
            <div className="d-grid">
              <Button
                type="button"
                color="secondary"
                variant="filled"
                onClick={handleNextPage}
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
