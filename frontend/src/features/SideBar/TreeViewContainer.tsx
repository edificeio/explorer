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
  useCurrentFolder,
  useElementDragOver,
  useIsTrash,
  useSelectedNodesIds,
  useStoreActions,
  useTreeData,
} from "~/store";

const CreateFolderModal = lazy(
  async () => await import("../ActionBar/Folder/FolderModal"),
);

export const TreeViewContainer = () => {
  const queryClient = useQueryClient();

  const [isModalOpen, toggle] = useToggle();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const treeData = useTreeData();
  const isTrashFolder = useIsTrash();
  // const selectedNodesIds = useSelectedNodesIds();
  const elementDragOver = useElementDragOver();

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
    foldTreeItem,
    clearSelectedItems,
    clearSelectedIds,
  } = useStoreActions();

  const selectedNodesIds = useSelectedNodesIds();
  const currentFolder = useCurrentFolder();

  const handleTreeItemUnfold = async (folderId: ID) => {
    await unfoldTreeItem(folderId, queryClient);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  };

  const handleTreeItemSelect = (folderId: ID) => {
    selectTreeItem(folderId);
  };
  const handleTreeItemFold = (folderId: ID) => {
    foldTreeItem(folderId);
  };

  const handleOnFolderCreate = () => {
    clearSelectedItems();
    clearSelectedIds();
    toggle();
  };

  return (
    <>
      <TreeView
        data={data}
        selectedNodesIds={selectedNodesIds}
        onTreeItemSelect={handleTreeItemSelect}
        onTreeItemFold={handleTreeItemFold}
        onTreeItemUnfold={handleTreeItemUnfold}
        elementDragOver={elementDragOver}
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
