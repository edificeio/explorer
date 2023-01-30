import { useExplorerContext } from "@contexts/index";
import CreateModal from "@features/Actionbar/components/FolderFormModal";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { Button, TreeView } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";
import { useOdeStore } from "@store/useOdeStore";

import TrashButton from "./TrashButton";
export const TreeViewContainer = () => {
  const {
    state: { treeData },
    i18n,
  } = useExplorerContext();
  /* feature treeview @hook */
  const {
    handleTreeItemFold,
    isOpenedModal,
    trashId,
    trashSelected,
    handleTreeItemSelect,
    handleTreeItemTrash,
    handleTreeItemUnfold,
    onClose,
    onCreateSuccess,
    onOpen,
  } = useTreeView();

  const selectedNodesIds = useOdeStore((state) => state.selectedNodesIds);

  return (
    <>
      <TreeView
        data={treeData}
        selectedNodesIds={selectedNodesIds}
        onTreeItemSelect={handleTreeItemSelect}
        onTreeItemFold={handleTreeItemFold}
        onTreeItemUnfold={handleTreeItemUnfold}
      />
      <TrashButton
        id={trashId}
        selected={trashSelected}
        onSelect={() => handleTreeItemTrash(trashId)}
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
