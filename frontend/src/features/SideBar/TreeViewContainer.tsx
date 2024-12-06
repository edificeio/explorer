import { lazy, Suspense } from 'react';

import { FOLDER, type ID } from '@edifice.io/client';
import {
  Button,
  findNodeById,
  LoadingScreen,
  TreeView,
  useEdificeClient,
  useScrollToTop,
  useToggle,
} from '@edifice.io/react';
import { IconPlus } from '@edifice.io/react/icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import TrashButton from '~/features/SideBar/TrashButton';
import {
  useElementDragOver,
  useIsTrash,
  useSelectedNodeId,
  useStoreActions,
  useTreeData,
} from '~/store';

const CreateFolderModal = lazy(
  async () => await import('../ActionBar/Folder/FolderModal'),
);

export const TreeViewContainer = () => {
  const [isModalOpen, toggle] = useToggle();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const queryClient = useQueryClient();
  const treeData = useTreeData();
  const isTrashFolder = useIsTrash();
  const selectedNodeId = useSelectedNodeId();
  const elementDragOver = useElementDragOver();
  const scrollToTop = useScrollToTop();
  const currentSelectedNodeId = !isTrashFolder ? selectedNodeId : FOLDER.BIN;

  const { appCode } = useEdificeClient();
  const { t } = useTranslation(['common', appCode]);

  const data = {
    ...treeData,
    name: t('explorer.filters.mine', { ns: appCode }),
  };

  const {
    goToTrash,
    selectTreeItem,
    clearSelectedItems,
    clearSelectedIds,
    fetchTreeData,
  } = useStoreActions();

  const handleTreeItemClick = (folderId: ID) => {
    selectTreeItem(folderId, queryClient);
    scrollToTop();
  };

  const handleOnTreeItemUnfold = (nodeId: string) => {
    const folder = findNodeById(treeData, nodeId);
    const hasSomeChildrenWithChildren = folder?.children?.some(
      (child) => Array.isArray(child?.children) && child.children?.length > 0,
    );

    folder?.children?.forEach((child) => {
      if (hasSomeChildrenWithChildren) return;
      fetchTreeData(child.id as string, queryClient);
    });
  };

  const handleOnFolderCreate = () => {
    clearSelectedItems();
    clearSelectedIds();
    toggle();
  };

  return (
    <>
      <TreeView
        data={data}
        selectedNodeId={currentSelectedNodeId}
        draggedNode={elementDragOver?.isTreeview ? elementDragOver : undefined}
        onTreeItemClick={handleTreeItemClick}
        onTreeItemUnfold={handleOnTreeItemUnfold}
      />
      <TrashButton
        id={FOLDER.BIN}
        selected={isTrashFolder}
        onSelect={goToTrash}
      />
      <div className="d-grid my-16">
        <Button
          disabled={isTrashFolder}
          type="button"
          color="primary"
          variant="outline"
          leftIcon={<IconPlus />}
          onClick={handleOnFolderCreate}
        >
          {t('explorer.folder.new')}
        </Button>
      </div>
      <Suspense fallback={<LoadingScreen />}>
        {isModalOpen && (
          <CreateFolderModal
            edit={false}
            isOpen={isModalOpen}
            onSuccess={toggle}
            onCancel={toggle}
          />
        )}
      </Suspense>
    </>
  );
};
