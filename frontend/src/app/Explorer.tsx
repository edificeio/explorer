import { Suspense, lazy } from "react";

import {
  AppCard,
  Grid,
  AppIcon,
  LoadingScreen,
} from "@ode-react-ui/components";
import { useOdeClient, useXitiTrackPageLoad } from "@ode-react-ui/core";
import { type IWebApp, type IAction } from "ode-ts-client";

import ActionBarContainer from "~/features/Actionbar/components/ActionBarContainer";
import { AppHeader } from "~/features/Explorer/components";
import { useLibraryUrl } from "~/features/Explorer/components/Library/useLibraryUrl";
import { List } from "~/features/Explorer/components/List/List";
import ActionResourceDisableModal from "~/features/Explorer/components/ResourcesList/ActionResourceDisableModal";
import { TreeViewContainer } from "~/features/TreeView/components/TreeViewContainer";
import { useActions } from "~/services/queries";
import { Breadcrumb } from "~/shared/components/Breadcrumb";
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

  return (
    <>
      <AppHeader>
        <AppCard
          app={currentApp as IWebApp}
          isHeading
          headingStyle="h3"
          level="h1"
        >
          <AppIcon app={currentApp} size="40" />
          <AppCard.Name />
        </AppCard>
        {isActionAvailable({ workflow: "create", actions }) && (
          <Suspense fallback={<LoadingScreen />}>
            <AppAction />
          </Suspense>
        )}
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
          <Breadcrumb />
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
