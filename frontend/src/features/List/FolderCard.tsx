import { useEffect, useId, useState } from 'react';

import { useDraggable, useDroppable } from '@dnd-kit/core';
import { Files } from '@edifice-ui/icons';
import { Card, CardProps, useBreakpoint } from '@edifice-ui/react';
import { ID, IWebApp } from 'edifice-ts-client';

import { useElementDragOver, useResourceOrFolderIsDraggable } from '~/store';
import { DraggableCard } from './DraggableCard';

export interface FolderCardProps extends Omit<CardProps, 'children'> {
  /**
   * IWeb App
   */
  app?: IWebApp | undefined;
  /**
   * Folder's name
   */
  name: string;
  /**
   * Folder's id
   */
  idFolder: ID;
}

const FolderCard = ({
  app,
  name,
  idFolder,
  isSelected = false,
  isSelectable = true,
  onClick,
  onSelect,
}: FolderCardProps) => {
  const [folderIsDrag, setFolderIsDrag] = useState<boolean>(false);
  const newId = useId();

  const { lg } = useBreakpoint();

  const { setNodeRef: setDroppableRef } = useDroppable({
    id: newId,
    data: {
      id: idFolder,
      name: name,
      isTreeview: false,
      accepts: ['folder', 'resource'],
    },
  });

  const {
    attributes,
    listeners,
    setNodeRef: setDraggableRef,
    transform,
  } = useDraggable({
    id: newId,
    data: {
      id: idFolder,
      type: 'folder',
    },
    disabled: !lg,
  });

  const resourceOrFolderIsDraggable = useResourceOrFolderIsDraggable();
  const elementDragOver = useElementDragOver();

  const combinedRef = (element: HTMLElement | null) => {
    setDraggableRef(element);
    setDroppableRef(element);
  };

  const folderIsOver = elementDragOver.overId === idFolder;

  const cursor =
    !elementDragOver.canMove && elementDragOver.isTreeview
      ? 'no-drop'
      : folderIsDrag
        ? 'grabbing'
        : 'default';

  const styles = {
    transform: `translate3d(${(transform?.x ?? 0) / 1}px, ${
      (transform?.y ?? 0) / 1
    }px, 0)`,
    touchAction: 'none',
    cursor,
  } as React.CSSProperties;

  useEffect(() => {
    const isDrag = resourceOrFolderIsDraggable.elementDrag === idFolder;
    setFolderIsDrag(isDrag);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resourceOrFolderIsDraggable]);

  return (
    <div ref={combinedRef} {...listeners} {...attributes} style={{ ...styles }}>
      {!folderIsDrag ? (
        <Card
          app={app}
          isSelectable={!folderIsDrag && isSelectable}
          isSelected={
            (!folderIsDrag && isSelected) || (folderIsOver && folderIsDrag)
          }
          isFocused={folderIsOver}
          onClick={onClick}
          onSelect={onSelect}
        >
          {(appCode) => (
            <Card.Body>
              <Files
                width="48"
                height="48"
                className={`color-app-${appCode}`}
              />
              <Card.Title>{name}</Card.Title>
            </Card.Body>
          )}
        </Card>
      ) : (
        <DraggableCard name={name} app={app} type="folder" />
      )}
    </div>
  );
};

FolderCard.displayName = 'FolderCard';

export default FolderCard;
