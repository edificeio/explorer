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
import { usePreviousFolder } from "@store/useOdeStore";
import { ISession, IWebApp } from "ode-ts-client";

export default function Explorer({
  session,
  app,
}: {
  session: ISession;
  app: IWebApp | undefined;
}) {
  const previousFolder = usePreviousFolder();

  const { contextRef, createResource, handleNextPage, i18n } =
    useExplorerContext();

  const { handleNavigationBack } = useTreeView();

  console.count("Explorer");

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
            <h2 className="body">{i18n("explorer.filters.mine")}</h2>
          </div>
          {/* <div className="py-24">
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
          </div> */}
          <FoldersList />
          <ResourcesList app={app} session={session} />
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
