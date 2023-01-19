import { useExplorerContext } from "@contexts/index";
import useTreeView from "@features/TreeView/hooks/useTreeView";
import { Card } from "@ode-react-ui/core";
import { IFolder } from "ode-ts-client";

function FoldersList() {
  const {
    state: { folders },
    selectFolder,
    deselectFolder,
    isFolderSelected,
  } = useExplorerContext();

  const { handleTreeItemSelect } = useTreeView();

  function toggleSelect(item: IFolder) {
    if (isFolderSelected(item)) {
      deselectFolder(item);
    } else {
      selectFolder(item);
    }
  }
  return folders.length ? (
    <ul className="grid ps-0 list-unstyled">
      {folders.map((folder: IFolder) => {
        return (
          <li
            className="g-col-4"
            key={folder.id}
            onClick={() => handleTreeItemSelect(folder.id)}
          >
            <Card
              name={folder.name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onClick={() => toggleSelect(folder)}
            />
          </li>
        );
      })}
    </ul>
  ) : (
    <p>Aucun dossier</p>
  );
}

export default FoldersList;
