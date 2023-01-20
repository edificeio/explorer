import { useExplorerContext } from "@contexts/index";
import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import { useI18n } from "@hooks/useI18n";
import {
  AppCard,
  Button,
  Grid,
  FormControl,
  Input,
  SearchButton,
} from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { AppHeader, EPub } from "@shared/components";
import FoldersList from "@shared/components/FoldersList/FoldersList";
import ResourcesList from "@shared/components/ResourcesList/ResourcesList";
import { useCurrentApp } from "@store/useOdeStore";

export default function Explorer() {
  const { i18n } = useI18n();
  const { context, createResource, handleNextPage } = useExplorerContext();

  const currentApp = useCurrentApp();

  console.log("explorer");

  return context.isInitialized() ? (
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
          {/* {resources.length && folders.length > 1 ? ( */}
          <form
            // ref={formRef}
            noValidate
            className="bg-light p-16 ps-24 ms-n16 ms-lg-n24 me-n16"
            // onSubmit={handleOnSubmit}
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
          {/* ) : null} */}
          <div className="py-24">
            <h2 className="body">{i18n("explorer.filters.mine")}</h2>
            {/* {previousId === "default" ? (
              <h2 className="body">{i18n("explorer.filters.mine")}</h2>
            ) : (
              <div className="d-flex align-items-center gap-8">
                <IconButton
                  icon={<ArrowLeft />}
                  variant="ghost"
                  color="tertiary"
                  onClick={() => handleTreeItemSelect(previousId)}
                />
                <p className="body">Retour</p>
              </div>
            )} */}
          </div>
          <FoldersList />
          <ResourcesList />
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
