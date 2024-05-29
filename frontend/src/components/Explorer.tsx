import { Suspense, lazy, useEffect } from "react";

import {
  DndContext,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
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
import { AppParams } from "~/config/getExplorerConfig";
import { useDisableModal } from "~/features/ActionBar/Disable/useDisableModal";
import { useTrashModal } from "~/features/ActionBar/Trash/useTrashModal";
import { List } from "~/features/List/List";
import { SearchForm } from "~/features/SearchForm/SearchForm";
import { TreeViewContainer } from "~/features/SideBar/TreeViewContainer";
import { useActions, useMoveItem } from "~/services/queries";
import { useSearchParams, useStoreActions } from "~/store";

const AppAction = lazy(
  async () => await import("~/components/AppAction/AppAction"),
);

const Library = lazy(
  async () => await import("~/features/SideBar/Library/Library"),
);

const ActionBar = lazy(
  async () => await import("~/features/ActionBar/ActionBarContainer"),
);
const DisableModal = lazy(
  async () => await import("~/features/ActionBar/Disable/DisableModal"),
);

const TrashModal = lazy(
  async () => await import("~/features/ActionBar/Trash/TrashModal"),
);

const OnboardingModal = lazy(async () => await import("./OnboardingModal"));

const Explorer = ({ config }: { config: AppParams }) => {
  const searchParams = useSearchParams();
  const moveItem = useMoveItem();
  const {
    setConfig,
    setSearchParams,
    setResourceOrFolderIsDraggable,
    setElementDragOver,
    setResourceIds,
    setFolderIds,
    foldTreeItem,
  } = useStoreActions();

  useEffect(() => {
    setConfig(config || {});
    setSearchParams({
      ...searchParams,
      application: config.app,
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

  const activationConstraint = {
    delay: 200,
    tolerance: 5,
  };

  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint,
  });
  const touchSensor = useSensor(TouchSensor, {
    activationConstraint,
  });
  const keyboardSensor = useSensor(KeyboardSensor);
  const sensors = useSensors(mouseSensor, touchSensor, keyboardSensor);

  const handleDragEnd = async (event: any) => {
    const { over, active } = event;
    if (over && active.id !== over.id) {
      try {
        await moveItem.mutate(over.id);
      } catch (e) {
        console.error(e);
      }
    }
    setResourceOrFolderIsDraggable({ isDrag: false, elementDrag: undefined });
  };

  const handleDragStart = (event: any) => {
    const { active } = event;
    setResourceOrFolderIsDraggable({ isDrag: true, elementDrag: active.id });
    if (active.data.current.type === "resource") {
      setResourceIds([active.id]);
    } else if (active.data.current.type === "folder") {
      setFolderIds([active.id]);
    }
  };

  const handleDragOver = (event: any) => {
    const { over } = event;
    if (over) {
      foldTreeItem(over.id);
      setElementDragOver({ isOver: true, overId: over.id });
    }
  };

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
          <DndContext
            sensors={sensors}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
            onDragOver={handleDragOver}
          >
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
              {config.enableOnboarding && (
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
              )}
              {isTrashedModalOpen && (
                <TrashModal
                  isOpen={isTrashedModalOpen}
                  onCancel={onTrashedCancel}
                />
              )}
              {isActionDisableModalOpen && (
                <DisableModal
                  isOpen={isActionDisableModalOpen}
                  onCancel={onActionDisableCancel}
                />
              )}
            </Suspense>
          </DndContext>
        </Grid>
      </>
    )
  );
};

export default Explorer;
