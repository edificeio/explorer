import { useScrollToTop, Card, useOdeClient } from "@edifice-ui/react";
import { useSpring, animated } from "@react-spring/web";
import { InfiniteData } from "@tanstack/react-query";
import { type ID, type IFolder, ISearchResults } from "edifice-ts-client";

import { useStoreActions, useFolderIds, useSelectedFolders } from "~/store";

const FoldersList = ({
  data,
  isFetching,
}: {
  data: InfiniteData<ISearchResults> | undefined;
  isFetching: boolean;
}): JSX.Element | null => {
  const { currentApp } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const selectedFolders = useSelectedFolders();
  const folderIds = useFolderIds();
  const { setSelectedFolders, setFolderIds, openFolder } = useStoreActions();

  function toggleSelect(folder: IFolder) {
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
      setFolderIds([...folderIds, folder.id]);
      setSelectedFolders([...selectedFolders, folder]);
    }
  }

  const springs = useSpring({
    from: { opacity: 0 },
    to: { opacity: 1 },
  });

  const scrollToTop = useScrollToTop();

  return data?.pages[0]?.folders.length ? (
    <animated.ul className="grid ps-0 list-unstyled mb-24">
      {data?.pages[0]?.folders.map((folder: IFolder) => {
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
              app={currentApp!}
              options={{
                type: "folder",
                name,
              }}
              isLoading={isFetching}
              isSelected={folderIds.includes(folder.id)}
              onOpen={() => {
                scrollToTop();
                openFolder({ folder, folderId: folder.id });
              }}
              onSelect={() => toggleSelect(folder)}
            />
          </animated.li>
        );
      })}
    </animated.ul>
  ) : null;
};

export default FoldersList;
