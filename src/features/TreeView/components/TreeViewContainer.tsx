import { useExplorerContext } from "@contexts/index";
import CreateModal from "@features/Actionbar/components/FolderFormModal";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { Button, TreeView } from "@ode-react-ui/core";
import { Plus } from "@ode-react-ui/icons";

import TrashButton from "./TrashButton";
export const TreeViewContainer = () => {
  const { i18n } = useExplorerContext();
  /* feature treeview @hook */
  const {
    trashId,
    treeData,
    trashSelected,
    handleTreeItemSelect,
    handleTreeItemFold,
    handleTreeItemUnfold,
    isOpenedModal,
    onOpen,
    onClose,
    onCreateSuccess,
  } = useTreeView();

  return (
    <>
      <TreeView
        data={treeData}
        onTreeItemSelect={handleTreeItemSelect}
        onTreeItemFold={handleTreeItemFold}
        onTreeItemUnfold={handleTreeItemUnfold}
      />
      <TrashButton
        id={trashId}
        selected={trashSelected}
        onSelect={() => handleTreeItemSelect(trashId)}
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
