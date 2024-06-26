import { lazy, Suspense } from "react";

import { Plus } from "@edifice-ui/icons";
import {
  Button,
  LoadingScreen,
  useOdeClient,
  useToggle,
} from "@edifice-ui/react";
import { useQueryClient } from "@tanstack/react-query";
import { FOLDER, type ID } from "edifice-ts-client";
import { useTranslation } from "react-i18next";

import TreeView from "~/components/TreeView/TreeView";
import TrashButton from "~/features/SideBar/TrashButton";
import {
  useElementDragOver,
  useIsTrash,
  useSelectedNodeId,
  useStoreActions,
  useTreeData,
} from "~/store";

const CreateFolderModal = lazy(
  async () => await import("../ActionBar/Folder/FolderModal"),
);

export const TreeViewContainer = () => {
  const [isModalOpen, toggle] = useToggle();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const queryClient = useQueryClient();
  const treeData = useTreeData();
  const isTrashFolder = useIsTrash();
  const selectedNodeId = useSelectedNodeId();
  const elementDragOver = useElementDragOver();

  const currentSelectedNodeId = !isTrashFolder ? selectedNodeId : "bin";

  const { appCode } = useOdeClient();
  const { t } = useTranslation(["common", appCode]);

  const data = {
    ...treeData,
    name: t("explorer.filters.mine", { ns: appCode }),
  };

  const {
    goToTrash,
    selectTreeItem,
    unfoldTreeItem,
    clearSelectedItems,
    clearSelectedIds,
  } = useStoreActions();

  const handleTreeItemUnfold = async (folderId: ID) =>
    await unfoldTreeItem(folderId, queryClient);

  const handleTreeItemClick = (folderId: ID) => selectTreeItem(folderId);

  const handleOnFolderCreate = () => {
    clearSelectedItems();
    clearSelectedIds();
    toggle();
  };

  return (
    <>
      <TreeView
        data={data}
        selectedNodeId={currentSelectedNodeId}
        onTreeItemClick={handleTreeItemClick}
        onTreeItemUnfold={handleTreeItemUnfold}
        elementDragOver={
          elementDragOver?.isTreeview ? elementDragOver : undefined
        }
      />
      <TrashButton
        id={FOLDER.BIN}
        selected={isTrashFolder}
        onSelect={goToTrash}
      />
      <div className="d-grid my-16">
        <Button
          disabled={isTrashFolder}
          type="button"
          color="primary"
          variant="outline"
          leftIcon={<Plus />}
          onClick={handleOnFolderCreate}
        >
          {t("explorer.folder.new")}
        </Button>
      </div>
      <Suspense fallback={<LoadingScreen />}>
        {isModalOpen && (
          <CreateFolderModal
            edit={false}
            isOpen={isModalOpen}
            onSuccess={toggle}
            onCancel={toggle}
          />
        )}
      </Suspense>
    </>
  );
};
