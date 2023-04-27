import { lazy, Suspense } from "react";

import TrashButton from "@features/TreeView/components/TrashButton";
import { TreeView } from "@ode-react-ui/advanced";
import { Button, LoadingScreen, useOdeClient } from "@ode-react-ui/core";
import { useModal } from "@ode-react-ui/hooks";
import { Plus } from "@ode-react-ui/icons";
import {
  useStoreActions,
  useIsTrash,
  useSelectedNodesIds,
  useTreeData,
} from "@store/store";
import { useQueryClient } from "@tanstack/react-query";
import { FOLDER, type ID } from "ode-ts-client";

const CreateModal = lazy(
  async () => await import("../../Actionbar/components/EditFolderModal"),
);

export const TreeViewContainer = () => {
  const queryclient = useQueryClient();
  const { i18n } = useOdeClient();
  const [isCreateFolderModalOpen, toggle] = useModal();
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const treeData = useTreeData();
  const isTrashFolder = useIsTrash();
  const selectedNodesIds = useSelectedNodesIds();
  const { goToTrash, selectTreeItem, unfoldTreeItem, foldTreeItem } =
    useStoreActions();

  const handleTreeItemUnfold = async (folderId: ID) => {
    await unfoldTreeItem(folderId, queryclient);
  };

  return treeData ? (
    <>
      <TreeView
        data={treeData}
        selectedNodesIds={selectedNodesIds}
        onTreeItemSelect={selectTreeItem}
        onTreeItemFold={foldTreeItem}
        onTreeItemUnfold={handleTreeItemUnfold}
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
          onClick={toggle}
        >
          {i18n("explorer.folder.new")}
        </Button>
      </div>
      <Suspense fallback={<LoadingScreen />}>
        {isCreateFolderModalOpen && (
          <CreateModal
            edit={false}
            isOpen={isCreateFolderModalOpen}
            onSuccess={toggle}
            onCancel={toggle}
          />
        )}
      </Suspense>
    </>
  ) : null;
};
