import { Card } from "@ode-react-ui/components";
import { useOdeClient } from "@ode-react-ui/core";
import { useSpring, animated } from "@react-spring/web";
import { type ID, type IFolder } from "ode-ts-client";

import { useSearchContext } from "~/services/queries";
import { refScrollTo } from "~/shared/utils/scrollToTop";
import { useStoreActions, useFolderIds, useSelectedFolders } from "~/store";

const FoldersList = (): JSX.Element | null => {
  const { currentApp } = useOdeClient();

  const { data, isFetching } = useSearchContext();

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
          (selectedFolder) => selectedFolder.id !== folder.id,
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

  const scrollToTop = refScrollTo();

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
              app={currentApp}
              name={name}
              isFolder
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
