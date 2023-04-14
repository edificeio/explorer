import { useEffect } from "react";

import ActionBarContainer from "@features/Actionbar/components/ActionBarContainer";
import { EmptyScreenApp } from "@features/EmptyScreens/EmptyScreenApp";
import { EmptyScreenNoContentInFolder } from "@features/EmptyScreens/EmptyScreenNoContentInFolder";
import { EmptyScreenTrash } from "@features/EmptyScreens/EmptyScreenTrash";
import { AppHeader } from "@features/Explorer/components";
import FoldersList from "@features/Explorer/components/FoldersList/FoldersList";
import ResourcesList from "@features/Explorer/components/ResourcesList/ResourcesList";
import { TreeViewContainer } from "@features/TreeView/components/TreeViewContainer";
import {
  AppCard,
  Button,
  Grid,
  IconButton,
  useOdeClient,
  AppIcon,
  Library,
} from "@ode-react-ui/core";
import { ArrowLeft, Plus } from "@ode-react-ui/icons";
import { useInvalidateQueries } from "@queries/index";
import { OnBoardingTrash } from "@shared/components/OnBoardingModal";
import { capitalizeFirstLetter } from "@shared/utils/capitalizeFirstLetter";
import { getAppParams } from "@shared/utils/getAppParams";
import useExplorerStore from "@store/index";
import { useQueryClient } from "@tanstack/react-query";

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

const params = getAppParams();

export default function Explorer(): JSX.Element | null {
  const { i18n, app, appCode, getBootstrapTheme } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const actions = useExplorerStore((state) => state.actions);
  const searchParams = useExplorerStore((state) => state.searchParams);
  const isAppReady = useExplorerStore((state) => state.isAppReady);

  const init = useExplorerStore((state) => state.init);
  const getHasResourcesOrFolders = useExplorerStore(
    (state) => state.getHasResourcesOrFolders,
  );
  const getIsTrashSelected = useExplorerStore(
    (state) => state.getIsTrashSelected,
  );
  const getHasNoSelectedNodes = useExplorerStore(
    (state) => state.getHasNoSelectedNodes,
  );
  const gotoPreviousFolder = useExplorerStore(
    (state) => state.gotoPreviousFolder,
  );
  const getPreviousFolder = useExplorerStore(
    (state) => state.getPreviousFolder,
  );
  const getHasSelectedRoot = useExplorerStore(
    (state) => state.getHasSelectedRoot,
  );
  const createResource = useExplorerStore((state) => state.createResource);
  const isActionAvailable = useExplorerStore(
    (state) => state.isActionAvailable,
  );

  useEffect(() => {
    init(params);
  }, [params]);

  /* const { isLoading } = useCreateContext({
    searchParams,
    onSuccess: async (data: Promise<GetContextResult>) => console.log(data),
  }); */

  const trashName: string = i18n("explorer.tree.trash");
  const rootName: string = i18n("explorer.filters.mine");
  const previousName: string = getPreviousFolder()?.name || rootName;
  const canPublish = actions.find((action) => action.id === "publish");

  // TODO : mettre ça dans une conf
  const LIB_URL = `https://library.opendigitaleducation.com/search/?application%5B0%5D=${capitalizeFirstLetter(
    appCode,
  )}&page=1&sort_field=views&sort_order=desc`;

  const queryClient = useQueryClient();

  const { removeQueries } = useInvalidateQueries(
    queryClient,
    gotoPreviousFolder,
  );

  console.log({ searchParams });

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
          {canPublish?.available && (
            <Library
              src={`${getBootstrapTheme()}/images/image-library.svg`}
              alt={i18n("explorer.libray.img.alt")}
              text={i18n("explorer.libray.title")}
              url={LIB_URL}
              textButton={i18n("explorer.libray.btn")}
            />
          )}
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="9">
          {/* <SearchForm /> */}
          <div className="py-16">
            {getHasNoSelectedNodes() ? (
              <h2 className="body py-8 fw-bold">
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
                  onClick={removeQueries}
                />
                <p className="body py-8 text-truncate">
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
          ) : null}

          <EmptyScreenNoContentInFolder />
          <EmptyScreenApp />
          <EmptyScreenTrash />
        </Grid.Col>
        <ActionBarContainer />
        <OnBoardingTrash />
      </Grid>
    </>
  ) : null;
}
