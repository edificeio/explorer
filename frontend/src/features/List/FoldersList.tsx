import { useOdeClient, useScrollToTop } from "@edifice-ui/react";
import { animated, useSpring } from "@react-spring/web";
import { InfiniteData, useQueryClient } from "@tanstack/react-query";
import { ISearchResults, type ID, type IFolder } from "edifice-ts-client";

import {
  useFolderIds,
  useResourceOrFolderIsDraggable,
  useSelectedFolders,
  useStoreActions,
} from "~/store";
import FolderCard from "./FolderCard";

const FoldersList = ({
  data,
}: {
  data: InfiniteData<ISearchResults> | undefined;
  isFetching: boolean;
}) => {
  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const queryClient = useQueryClient();
  const selectedFolders = useSelectedFolders();
  const folderIds = useFolderIds();
  const resourceOrFolderIsDraggable = useResourceOrFolderIsDraggable();
  const scrollToTop = useScrollToTop();

  const { currentApp } = useOdeClient();
  const { setSelectedFolders, setFolderIds, openFolder } = useStoreActions();

  function handleOnSelectToggle(folder: IFolder) {
    if (folderIds.includes(folder.id)) {
      setFolderIds(
        folderIds.filter((selectedFolder: ID) => selectedFolder !== folder.id),
      );
      setSelectedFolders(
        selectedFolders.filter(
          (selectedFolder: { id: string }) => selectedFolder.id !== folder.id,
        ),
      );
    } else {
      setFolderIds([folder.id, ...folderIds]);
      setSelectedFolders([folder, ...selectedFolders]);
    }
  }

  function handleOnFolderClick(folder: IFolder) {
    scrollToTop();
    openFolder({ folder, folderId: folder.id, queryClient });
  }

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  return data?.pages[0]?.folders.length ? (
    <animated.ul className="grid ps-0 list-unstyled mb-24">
      {data?.pages[0]?.folders.map((folder: IFolder) => {
        const { id, name } = folder;
        const isDrag = resourceOrFolderIsDraggable.elementDrag === id;

        return (
          <animated.li
            className={`g-col-4 ${isDrag ? "z-2000" : "z-1"}`}
            key={id}
            style={{
              position: "relative",
              ...springs,
            }}
          >
            <FolderCard
              name={name}
              idFolder={id}
              app={currentApp}
              isSelected={folderIds.includes(folder.id)}
              onClick={() => handleOnFolderClick(folder)}
              onSelect={() => handleOnSelectToggle(folder)}
            />
          </animated.li>
        );
      })}
    </animated.ul>
  ) : null;
};

export default FoldersList;
