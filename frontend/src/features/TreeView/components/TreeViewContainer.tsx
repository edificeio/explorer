import { lazy, Suspense } from "react";

import { TreeView } from "@ode-react-ui/advanced";
import { Button, LoadingScreen, useOdeClient } from "@ode-react-ui/core";
import { useModal } from "@ode-react-ui/hooks";
import { Plus } from "@ode-react-ui/icons";
import { useInvalidateQueries } from "@queries/index";
import useExplorerStore from "@store/index";
import { useQueryClient } from "@tanstack/react-query";
import { FOLDER } from "ode-ts-client";

import TrashButton from "./TrashButton";

const CreateModal = lazy(
  async () => await import("@features/Actionbar/components/EditFolderModal"),
);

export const TreeViewContainer = () => {
  const [isCreateFolderModalOpen, toggle] = useModal();

  const { i18n } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const selectedNodeIds = useExplorerStore((state) => state.selectedNodeIds);
  const treeData = useExplorerStore((state) => state.treeData);

  const foldTreeItem = useExplorerStore((state) => state.foldTreeItem);
  const getIsTrashSelected = useExplorerStore(
    (state) => state.getIsTrashSelected,
  );
  const gotoTrash = useExplorerStore((state) => state.gotoTrash);
  const selectTreeItem = useExplorerStore((state) => state.selectTreeItem);
  const unfoldTreeItem = useExplorerStore((state) => state.unfoldTreeItem);
  const getCurrentFolderId = useExplorerStore(
    (state) => state.getCurrentFolderId,
  );

  const queryClient = useQueryClient();

  const { removeQueries } = useInvalidateQueries(queryClient, gotoTrash);

  return (
    <>
      <TreeView
        data={treeData}
        selectedNodesIds={selectedNodeIds}
        onTreeItemSelect={selectTreeItem}
        onTreeItemFold={foldTreeItem}
        onTreeItemUnfold={unfoldTreeItem}
      />
      <TrashButton
        id={FOLDER.BIN}
        selected={getIsTrashSelected()}
        onSelect={removeQueries}
      />
      <div className="d-grid my-16">
        <Button
          disabled={getCurrentFolderId() === FOLDER.BIN}
          type="button"
          color="primary"
          variant="outline"
          leftIcon={<Plus />}
          onClick={() => toggle()}
        >
          {i18n("explorer.folder.new")}
        </Button>
      </div>
      <Suspense fallback={<LoadingScreen />}>
        {isCreateFolderModalOpen && (
          <CreateModal
            edit={false}
            isOpen={isCreateFolderModalOpen}
            onSuccess={() => toggle()}
            onCancel={() => toggle()}
          />
        )}
      </Suspense>
    </>
  );
};
