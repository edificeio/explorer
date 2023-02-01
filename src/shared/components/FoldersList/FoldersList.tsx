import { useExplorerContext } from "@contexts/index";
import { Card } from "@ode-react-ui/core";
import { IFolder } from "ode-ts-client";

function FoldersList() {
  const {
    folderList,
    getIsTrashSelected,
    select,
    deselect,
    isFolderSelected,
    openFolder,
  } = useExplorerContext();

  function toggleSelect(folder: IFolder) {
    if (isFolderSelected(folder)) {
      deselect([folder.id], "folder");
    } else {
      select([folder.id], "folder");
    }
  }

  return folderList.length && !getIsTrashSelected() ? (
    <ul className="grid ps-0 list-unstyled">
      {folderList.map((folder: IFolder) => {
        const { id, name } = folder;
        return (
          <li className="g-col-4" key={id}>
            <Card
              name={name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={() => {
                openFolder(id);
              }}
              onSelect={() => toggleSelect(folder)}
            />
          </li>
        );
      })}
    </ul>
  ) : null;
}

export default FoldersList;
