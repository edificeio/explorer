import { Card, useOdeClient } from "@ode-react-ui/core";
import { useSpring, animated } from "@react-spring/web";
import useExplorerStore from "@store/index";
import { type IFolder } from "ode-ts-client";

export default function FoldersList() {
  const { app } = useOdeClient();
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const isFolderSelected = useExplorerStore((state) => state.isFolderSelected);
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

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  return folders.length && !getIsTrashSelected() ? (
    <animated.ul className="grid ps-0 list-unstyled">
      {folders.map((folder: IFolder) => {
        const { id, name } = folder;
        return (
          <animated.li
            className="g-col-4"
            key={id}
            style={{
              ...springs,
            }}
          >
            <Card
              app={app}
              name={name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={async () => await openFolder(id)}
              onSelect={() => toggleSelect(folder)}
            />
          </animated.li>
        );
      })}
    </animated.ul>
  ) : null;
}
