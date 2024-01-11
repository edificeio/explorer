import { Suspense, lazy, useEffect } from "react";

import {
  useOdeClient,
  useXitiTrackPageLoad,
  Grid,
  LoadingScreen,
  Breadcrumb,
  AppHeader,
  isActionAvailable,
} from "@edifice-ui/react";
import { IWebApp } from "edifice-ts-client";

import { ExplorerBreadcrumb } from "./ExplorerBreadcrumb";
import { AppParams } from "../utils/getAppParams";
import { useDisableModal } from "~/features/ActionBar/Disable/useDisableModal";
import { useTrashModal } from "~/features/ActionBar/Trash/useTrashModal";
import { List } from "~/features/List/List";
import { SearchForm } from "~/features/SearchForm/SearchForm";
import { TreeViewContainer } from "~/features/SideBar/TreeViewContainer";
import { useActions } from "~/services/queries";
import { useSearchParams, useStoreActions } from "~/store";

import "@edifice-ui/react/style.css";

const OnboardingModal = lazy(async () => {
  const module = await import("@edifice-ui/react");
  return { default: module.OnboardingModal };
});

const AppAction = lazy(
  async () => await import("~/components/AppAction/AppAction"),
);

const Library = lazy(
  async () => await import("~/features/SideBar/Library/Library"),
);

const ActionBar = lazy(
  async () => await import("~/features/ActionBar/ActionBarContainer"),
);
const ActionResourceDisableModal = lazy(
  async () => await import("~/features/ActionBar/Disable/DisableModal"),
);

const TrashedResourceModal = lazy(
  async () => await import("~/features/ActionBar/Trash/TrashModal"),
);

const Explorer = ({ config }: { config: AppParams }) => {
  const searchParams = useSearchParams();
  const { setConfig, setSearchParams } = useStoreActions();

  useEffect(() => {
    setConfig(config || {});
    setSearchParams({
      ...searchParams,
      app: config.app,
      types: config.types,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config]);

  const { currentApp } = useOdeClient();
  const { data: actions } = useActions();
  const { isTrashedModalOpen, onTrashedCancel } = useTrashModal();
  const { isActionDisableModalOpen, onActionDisableCancel } = useDisableModal();

  useXitiTrackPageLoad();

  const canPublish = isActionAvailable("publish", actions);
  const canCreate = isActionAvailable("create", actions);

  return (
    config && (
      <>
        <AppHeader
          render={() =>
            canCreate ? (
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
            <Suspense fallback={<LoadingScreen />}>
              <TreeViewContainer />
              {canPublish && <Library />}
            </Suspense>
          </Grid.Col>
          <Grid.Col sm="4" md="8" lg="6" xl="9">
            <SearchForm />
            <ExplorerBreadcrumb />
            <List />
          </Grid.Col>
          <Suspense fallback={<LoadingScreen />}>
            <ActionBar />
            <OnboardingModal
              id="showOnboardingTrash"
              items={[
                {
                  src: "onboarding/illu-trash-menu.svg",
                  alt: "explorer.modal.onboarding.trash.screen1.alt",
                  text: "explorer.modal.onboarding.trash.screen1.title",
                },
                {
                  src: "onboarding/illu-trash-notif.svg",
                  alt: "explorer.modal.onboarding.trash.screen2.alt",
                  text: "explorer.modal.onboarding.trash.screen2.alt",
                },
                {
                  src: "onboarding/illu-trash-delete.svg",
                  alt: "explorer.modal.onboarding.trash.screen3.alt",
                  text: "explorer.modal.onboarding.trash.screen3.title",
                },
              ]}
              modalOptions={{
                title: "explorer.modal.onboarding.trash.title",
                prevText: "explorer.modal.onboarding.trash.prev",
                nextText: "explorer.modal.onboarding.trash.next",
                closeText: "explorer.modal.onboarding.trash.close",
              }}
            />
            {isTrashedModalOpen && (
              <TrashedResourceModal
                isOpen={isTrashedModalOpen}
                onCancel={onTrashedCancel}
              />
            )}
            {isActionDisableModalOpen && (
              <ActionResourceDisableModal
                isOpen={isActionDisableModalOpen}
                onCancel={onActionDisableCancel}
              />
            )}
          </Suspense>
        </Grid>
      </>
    )
  );
};

export default Explorer;
