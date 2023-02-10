import { Card } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import { motion } from "framer-motion";
import { type IFolder } from "ode-ts-client";

export default function FoldersList() {
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

  const list = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
      transition: {
        staggerChildren: 0.1,
      },
    },
  };

  const item = {
    hidden: { opacity: 0 },
    show: {
      opacity: 1,
    },
  };

  return folders.length && !getIsTrashSelected() ? (
    <motion.ul
      className="grid ps-0 list-unstyled"
      initial="hidden"
      animate="show"
      variants={list}
    >
      {folders.map((folder: IFolder) => {
        const { id, name } = folder;
        return (
          <motion.li className="g-col-4" key={id} variants={item}>
            <Card
              name={name}
              isFolder
              isSelected={isFolderSelected(folder)}
              onOpen={async () => await openFolder(id)}
              onSelect={() => toggleSelect(folder)}
            />
          </motion.li>
        );
      })}
    </motion.ul>
  ) : null;
}
