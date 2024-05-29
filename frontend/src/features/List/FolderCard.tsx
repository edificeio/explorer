import { useDraggable, useDroppable } from "@dnd-kit/core";
import { Files } from "@edifice-ui/icons";
import { CardProps, Card } from "@edifice-ui/react";
import { IFolder, IWebApp } from "edifice-ts-client";

export interface FolderCardProps extends Omit<CardProps, "children"> {
  /**
   * IWeb App
   */
  app?: IWebApp | undefined;
  /**
   * Folder's name
   */
  folder: IFolder;
}

const FolderCard = ({
  app,
  folder,
  isSelected = false,
  isSelectable = true,
  onClick,
  onSelect,
}: FolderCardProps) => {
  const { setNodeRef: setDroppableRef } = useDroppable({
    id: folder.id,
    data: {
      folder: folder,
      accepts: ["folder", "resource"],
    },
  });

  const {
    attributes,
    listeners,
    setNodeRef: setDraggableRef,
    transform,
  } = useDraggable({
    id: folder.id,
    data: {
      type: "folder",
    },
  });

  const combinedRef = (element: HTMLElement | null) => {
    setDraggableRef(element);
    setDroppableRef(element);
  };

  return (
    <div
      {...listeners}
      {...attributes}
      style={
        {
          transform: `translate3d(${(transform?.x ?? 0) / 1}px, ${
            (transform?.y ?? 0) / 1
          }px, 0)`,
        } as React.CSSProperties
      }
    >
      <Card
        ref={combinedRef}
        app={app}
        isSelectable={isSelectable}
        isSelected={isSelected}
        onClick={onClick}
        onSelect={onSelect}
      >
        {(appCode) => (
          <Card.Body>
            <Files width="48" height="48" className={`color-app-${appCode}`} />
            <Card.Title>{folder.name}</Card.Title>
          </Card.Body>
        )}
      </Card>
    </div>
  );
};

FolderCard.displayName = "FolderCard";

export default FolderCard;
