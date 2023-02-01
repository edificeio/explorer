import { useExplorerContext } from "@contexts/index";
import CreateModal from "@features/Actionbar/components/FolderFormModal";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { Button, TreeView } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";

import TrashButton from "./TrashButton";
export const TreeViewContainer = () => {
  const {
    treeData,
    selectedNodeIds,
    getIsTrashSelected,
    i18n,
    foldTreeItem,
    selectTreeItem,
    gotoTrash,
    unfoldTreeItem,
  } = useExplorerContext();
  /* feature treeview @hook */
  const { isOpenedModal, trashId, onClose, onCreateSuccess, onOpen } =
    useTreeView();
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
        id={trashId}
        selected={getIsTrashSelected()}
        onSelect={gotoTrash}
      />
      <div className="d-grid my-16">
        <Button
          type="button"
          color="primary"
          variant="outline"
          leftIcon={<Plus />}
          onClick={onOpen}
        >
          {i18n("explorer.folder.new")}
        </Button>
      </div>
      <CreateModal
        edit={false}
        isOpen={isOpenedModal}
        onSuccess={onCreateSuccess}
        onCancel={onClose}
      />
    </>
  );
};
