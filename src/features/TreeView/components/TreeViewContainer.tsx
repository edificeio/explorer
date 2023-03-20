import { lazy, Suspense } from "react";

import { TreeView } from "@ode-react-ui/advanced";
import { Button, LoadingScreen, useOdeClient } from "@ode-react-ui/core";
import { useModal } from "@ode-react-ui/hooks";
import { Plus } from "@ode-react-ui/icons";
import useExplorerStore from "@store/index";
import { FOLDER } from "ode-ts-client";

import TrashButton from "./TrashButton";

const CreateModal = lazy(
  async () => await import("@features/Actionbar/components/EditFolderModal"),
);

export const TreeViewContainer = () => {
  const { i18n } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const {
    foldTreeItem,
    getIsTrashSelected,
    gotoTrash,
    selectedNodeIds,
    selectTreeItem,
    treeData,
    unfoldTreeItem,
  } = useExplorerStore((state) => state);

  const [isCreateFolderModalOpen, toggle] = useModal();

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
        onSelect={gotoTrash}
      />
      <div className="d-grid my-16">
        <Button
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
