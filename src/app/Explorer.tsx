import { useExplorerContext } from "@contexts/index";
import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { useI18n } from "@hooks/useI18n";
import { IconButton } from "@ode-react-ui/core";
import {
  AppCard,
  Button,
  Grid,
  FormControl,
  Input,
  SearchButton,
} from "@ode-react-ui/core";
import { ArrowLeft, Plus } from "@ode-react-ui/icons";
import { AppHeader, EPub } from "@shared/components";
import FoldersList from "@shared/components/FoldersList/FoldersList";
import ResourcesList from "@shared/components/ResourcesList/ResourcesList";
import {
  useCurrentApp,
  usePreviousFolder,
  useSession,
} from "@store/useOdeStore";

let explorerRendered = 0;
export default function Explorer() {
  explorerRendered++;
  const previousFolder = usePreviousFolder();
  const session = useSession();

  const { i18n } = useI18n();
  const { contextRef, createResource, handleNextPage } = useExplorerContext();

  const currentApp = useCurrentApp();

  const { handleNavigationBack } = useTreeView();

  return contextRef.current.isInitialized() ? (
    <>
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
          {i18n("explorer.create.title")}
        </Button>
      </AppHeader>
      {explorerRendered}
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
            {previousFolder.length === 0 ? (
              <h2 className="body">{i18n("explorer.filters.mine")}</h2>
            ) : (
              <div className="d-flex align-items-center gap-8">
                <IconButton
                  icon={<ArrowLeft />}
                  variant="ghost"
                  color="tertiary"
                  onClick={() => handleNavigationBack()}
                />
                <p className="body">
                  <strong>
                    {previousFolder.length === 1
                      ? i18n("explorer.filters.mine")
                      : previousFolder[previousFolder.length - 2].name}
                  </strong>
                </p>
              </div>
            )}
          </div>
          <FoldersList />
          <ResourcesList session={session} />
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
        </Grid.Col>
        <ActionBarContainer />
      </Grid>
    </>
  ) : null;
}
