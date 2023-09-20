import { Suspense, lazy } from "react";

import {
  useOdeClient,
  useXitiTrackPageLoad,
  Grid,
  LoadingScreen,
  Breadcrumb,
  AppHeader,
} from "@edifice-ui/react";
import { APP, type IAction } from "edifice-ts-client";
import { IWebApp } from "edifice-ts-client";

import ActionBarContainer from "~/features/Actionbar/components/ActionBarContainer";
import { useLibraryUrl } from "~/features/Explorer/components/Library/useLibraryUrl";
import { List } from "~/features/Explorer/components/List/List";
import ActionResourceDisableModal from "~/features/Explorer/components/ResourcesList/ActionResourceDisableModal";
import { SearchForm } from "~/features/Explorer/components/SearchForm/SearchForm";
import { TreeViewContainer } from "~/features/TreeView/components/TreeViewContainer";
import { useActions } from "~/services/queries";
import { ExplorerBreadcrumb } from "~/shared/components/ExplorerBreadcrumb";
import { useActionDisableModal } from "~/shared/hooks/useActionDisableModal";
import { useOnboardingModal } from "~/shared/hooks/useOnboardingModal";
import { useTrashModal } from "~/shared/hooks/useTrashedModal";
import { isActionAvailable } from "~/shared/utils/isActionAvailable";

const OnboardingTrash = lazy(
  async () => await import("~/shared/components/OnboardingTrash"),
);

const AppAction = lazy(
  async () =>
    await import("~/features/Explorer/components/AppAction/AppAction"),
);

const Library = lazy(
  async () => await import("~/features/Explorer/components/Library/Library"),
);

const TrashedResourceModal = lazy(
  async () =>
    await import(
      "~/features/Explorer/components/ResourcesList/TrashedResourceModal"
    ),
);

export default function Explorer(): JSX.Element | null {
  const { currentApp } = useOdeClient();

  const { isOnboardingTrash, isOpen, setIsOpen, handleSavePreference } =
    useOnboardingModal();
  const { data: actions } = useActions();
  const { isTrashedModalOpen, onTrashedCancel } = useTrashModal();
  const { isActionDisableModalOpen, onActionDisableCancel } =
    useActionDisableModal();

  useXitiTrackPageLoad();

  const canPublish = actions?.find(
    (action: IAction) => action.id === "publish",
  );

  const { libraryUrl } = useLibraryUrl();

  const searchFormOptions = [
    { label: "Mes ressources", value: 1 },
    { label: "Ressources partagées avec moi", value: 2 },
    ...(currentApp?.displayName == APP.EXERCIZER
      ? [{ label: "Exercices interactifs", value: 3 }]
      : []),
    ...(currentApp?.displayName == APP.EXERCIZER
      ? [{ label: "Exercices à rendre", value: 4 }]
      : []),
    ...(currentApp?.displayName == "pages"
      ? [{ label: "Projets publics", value: 5 }]
      : []),
    ...(currentApp?.displayName == "pages"
      ? [{ label: "Projets internes", value: 6 }]
      : []),
    ...(currentApp?.displayName == APP.BLOG
      ? [{ label: "Blogs publics", value: 7 }]
      : []),
  ];

  return (
    <>
      <AppHeader
        render={() =>
          isActionAvailable({ workflow: "create", actions }) ? (
            <Suspense fallback={<LoadingScreen />}>
              <AppAction />
            </Suspense>
          ) : null
        }
      >
        <Breadcrumb app={currentApp as IWebApp} />
      </AppHeader>

      <Grid className="flex-grow-1">
        <Grid.Col
          sm="3"
          lg="2"
          xl="3"
          className="border-end pt-16 pe-16 d-none d-lg-block"
          as="aside"
        >
          <TreeViewContainer />
          {canPublish?.available && libraryUrl && (
            <Suspense fallback={<LoadingScreen />}>
              <Library url={libraryUrl} />
            </Suspense>
          )}
        </Grid.Col>
        <Grid.Col sm="4" md="8" lg="6" xl="9">
          <SearchForm options={searchFormOptions} />
          <ExplorerBreadcrumb />
          <List />
        </Grid.Col>
        <ActionBarContainer />
        {isOnboardingTrash && (
          <Suspense fallback={<LoadingScreen />}>
            <OnboardingTrash
              isOpen={isOpen}
              setIsOpen={setIsOpen}
              handleSavePreference={handleSavePreference}
            />
          </Suspense>
        )}

        {isTrashedModalOpen && (
          <Suspense fallback={<LoadingScreen />}>
            <TrashedResourceModal
              isOpen={isTrashedModalOpen}
              onCancel={onTrashedCancel}
            />
          </Suspense>
        )}
        {isActionDisableModalOpen && (
          <Suspense fallback={<LoadingScreen />}>
            <ActionResourceDisableModal
              isOpen={isActionDisableModalOpen}
              onCancel={onActionDisableCancel}
            />
          </Suspense>
        )}
      </Grid>
    </>
  );
}
