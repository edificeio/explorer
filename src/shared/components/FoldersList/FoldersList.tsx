import { Card } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import { type IFolder } from "ode-ts-client";

function FoldersList() {
  const isFolderSelected = useExplorerStore((state) => state.isFolderSelected);

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const folders = useExplorerStore((state) => state.folders);
  const { deselect, select, openFolder, getIsTrashSelected } = useExplorerStore(
    (state) => state,
  );

  function toggleSelect(folder: IFolder) {
    if (isFolderSelected(folder)) {
      deselect([folder.id], "folder");
    } else {
      select([folder.id], "folder");
    }
  }

  return folders.length && !getIsTrashSelected() ? (
    <ul className="grid ps-0 list-unstyled">
      {folders.map((folder: IFolder) => {
        const { id, name } = folder;
        return (
          <li className="g-col-4" key={id}>
            <Card
              name={name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={() => openFolder(id)}
              onSelect={() => toggleSelect(folder)}
            />
          </li>
        );
      })}
    </ul>
  ) : null;
}

export default FoldersList;
