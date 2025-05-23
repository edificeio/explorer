import { useEffect, useState } from 'react';

import { ACTION, type IAction } from '@edifice.io/client';

import { goToEdit } from '~/services/api';
import { useActions, useCopyResource, useRestore } from '~/services/queries';
import {
  useCurrentFolder,
  useFolderIds,
  useIsTrash,
  useResourceIds,
  useResourceIsTrash,
  useResourceOrFolderIsDraggable,
  useSearchParams,
  useSelectedFolders,
  useSelectedResources,
  useStoreActions,
  useStoreContext,
} from '~/store';

type ModalName =
  | 'move'
  | 'delete'
  | 'publish'
  | 'edit_folder'
  | 'edit_resource'
  | 'share'
  | 'export'
  | 'void';

export default function useActionBar() {
  const [isActionBarOpen, setIsActionBarOpen] = useState<boolean>(false);
  const [openedModalName, setOpenedModalName] = useState<ModalName>('void');
  const [clickedAction, setClickedAction] = useState<IAction>();

  const config = useStoreContext((state) => state.config);

  const currentFolder = useCurrentFolder();
  const resourceIds = useResourceIds();
  const selectedResources = useSelectedResources();
  const selectedFolders = useSelectedFolders();
  const folderIds = useFolderIds();
  const isTrashFolder = useIsTrash();
  const restoreItem = useRestore();
  const isTrashResource = useResourceIsTrash();
  const searchParams = useSearchParams();
  const copyResource = useCopyResource();
  const resourceOrFolderIsDraggable = useResourceOrFolderIsDraggable();

  const {
    openResource,
    printSelectedResource,
    openFolder,
    clearSelectedItems,
    clearSelectedIds,
  } = useStoreActions();

  const { data: actions } = useActions(config?.actions as IAction[]);

  useEffect(() => {
    if (resourceIds.length === 0 && folderIds.length === 0) {
      setIsActionBarOpen(false);
      return;
    }
    if (isTrashResource) {
      setIsActionBarOpen(false);
      return;
    }
    if (resourceOrFolderIsDraggable.isDrag) {
      setIsActionBarOpen(false);
      return;
    }
    setIsActionBarOpen(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resourceIds, folderIds]);

  async function handleClick(action: IAction) {
    // A11Y: fix Screen readers can read parent page content outside the modal
    // https://docs.deque.com/issue-help/1.0.0/en/reading-order-browse-outside-modal
    document.getElementById('root')?.setAttribute('aria-hidden', 'true');

    // A11Y: save clicked action to set focus to clicked button after modal is dismissed
    setClickedAction(action);

    switch (action.id) {
      case ACTION.OPEN:
        if (resourceIds.length > 0) {
          return openResource(selectedResources[0]);
        } else {
          return openFolder({
            folder: selectedFolders[0],
            folderId: selectedFolders[0].id,
          });
        }
      case ACTION.COPY:
        return onCopy();
      case ACTION.MOVE:
        return setOpenedModalName('move');
      case ACTION.PRINT:
        return printSelectedResource();
      case ACTION.DELETE:
        return setOpenedModalName('delete');
      case ACTION.RESTORE:
        return await onRestore();
      case ACTION.PUBLISH:
        return setOpenedModalName('publish');
      // TODO fix in ode-ts
      case ACTION.UPD_PROPS:
      case 'edit' as any:
        return onEdit();
      case ACTION.SHARE:
        return setOpenedModalName('share');
      // case ACTION.MANAGE:
      //   return explorer.onManage();
      case ACTION.EXPORT:
        return setOpenedModalName('export');
      default:
        throw Error(`Unknown action: ${action.id}`);
    }
  }

  /**
   * Visibility rules for the action buttons.
   * @param action action to check
   * @returns true if the action button must be visible
   */
  function isActivable(action: IAction): boolean {
    const all = resourceIds.length + folderIds.length;
    const onlyOneItemSelected =
      resourceIds.length === 1 || folderIds.length === 1;
    const onlyOneSelected = all === 1;
    const noFolderSelected = folderIds.length === 0;
    switch (action.id) {
      case ACTION.OPEN:
        return onlyOneSelected;
      case ACTION.MANAGE:
        return onlyOneItemSelected;
      case ACTION.COPY:
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.PUBLISH:
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.UPD_PROPS:
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.SHARE:
        return noFolderSelected && onlyOneItemSelected;
      case 'export':
        return onlyOneItemSelected && noFolderSelected;
      case ACTION.PRINT:
        return onlyOneItemSelected && noFolderSelected;
      case 'edit' as any:
        return onlyOneSelected;
      default:
        return true;
    }
  }
  const isActivableForTrash = () => true;

  async function onRestore() {
    try {
      if (isTrashFolder) {
        await restoreItem.mutate();
      } else {
        throw new Error('Cannot restore untrashed resources');
      }
      onClearActionBar();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  }

  function onClearActionBar() {
    setOpenedModalName('void');
    // a11y: set focus to action button after modal is dismissed
    if (clickedAction?.id) {
      document.getElementById(clickedAction?.id)?.focus();
    }
  }

  const onFinish = (modalName: ModalName) => () => {
    if (openedModalName === modalName) {
      onClearActionBar();
      clearSelectedItems();
      clearSelectedIds();
      // queryClient.invalidateQueries({ queryKey: ["context"] });
      /* setTimeout(() => {
        queryClient.refetchQueries({ queryKey: ["context"] });
      }, 1000); */
    }
  };

  const onMoveCancel = onFinish('move');
  const onMoveSuccess = onFinish('move');
  const onDeleteSuccess = onFinish('delete');
  const onDeleteCancel = onFinish('delete');
  const onPublishSuccess = onFinish('publish');
  const onPublishCancel = onFinish('publish');
  const onEditFolderSuccess = onFinish('edit_folder');
  const onEditFolderCancel = onFinish('edit_folder');
  const onEditResourceSuccess = onFinish('edit_resource');
  const onEditResourceCancel = onFinish('edit_resource');
  const onShareResourceSuccess = onFinish('share');
  const onShareResourceCancel = onFinish('share');
  const onExportCancel = onFinish('export');
  const onExportSuccess = onFinish('export');

  async function onCopy() {
    if (selectedResources && selectedResources.length > 0) {
      const selectedResource = selectedResources[0];
      await copyResource.mutate(selectedResource);
      clearSelectedItems();
      clearSelectedIds();
    }
  }

  function onEdit() {
    if (resourceIds && resourceIds.length > 0) {
      const selectedResource = selectedResources[0].assetId;
      if (config?.app === 'scrapbook') {
        goToEdit({ searchParams, assetId: selectedResource });
      } else {
        setOpenedModalName('edit_resource');
      }
    } else {
      setOpenedModalName('edit_folder');
    }
  }

  function overrideLabel(action: IAction) {
    if ((action.id as any) === 'edit' && folderIds.length > 0) {
      return 'explorer.rename';
    }

    return `explorer.actions.${action.id}`;
  }

  return {
    onRestore,
    actions: isTrashFolder ? config?.trashActions : actions,
    selectedElement: [...selectedResources, ...selectedFolders],
    currentFolderId: currentFolder?.id,
    overrideLabel,
    handleClick,
    isActivable: isTrashFolder ? isActivableForTrash : isActivable,
    isActionBarOpen,
    isMoveModalOpen: openedModalName === 'move',
    onMoveCancel,
    onMoveSuccess,
    isDeleteModalOpen: openedModalName === 'delete',
    onDeleteCancel,
    onDeleteSuccess,
    isPublishModalOpen: openedModalName === 'publish',
    onPublishCancel,
    onPublishSuccess,
    isEditFolderOpen: openedModalName === 'edit_folder',
    onEditFolderCancel,
    onEditFolderSuccess,
    isEditResourceOpen: openedModalName === 'edit_resource',
    onEditResourceCancel,
    onEditResourceSuccess,
    isShareResourceOpen: openedModalName === 'share',
    onShareResourceCancel,
    onShareResourceSuccess,
    isExportModalOpen: openedModalName === 'export',
    onExportCancel,
    onExportSuccess,
    onClearActionBar,
  };
}
