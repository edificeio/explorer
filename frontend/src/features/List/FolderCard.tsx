import { useDraggable, useDroppable } from "@dnd-kit/core";
import { Files } from "@edifice-ui/icons";
import { CardProps, Card } from "@edifice-ui/react";
import { ID, IWebApp } from "edifice-ts-client";

import { ElementDraggable } from "./ElementDraggable";
import { useElementDragOver, useResourceOrFolderIsDraggable } from "~/store";

export interface FolderCardProps extends Omit<CardProps, "children"> {
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
  const { setNodeRef: setDroppableRef } = useDroppable({
    id: idFolder + "1",
    data: {
      id: idFolder,
      accepts: ["folder", "resource"],
    },
  });

  const {
    attributes,
    listeners,
    setNodeRef: setDraggableRef,
    transform,
  } = useDraggable({
    id: idFolder + "1",
    data: {
      id: idFolder,
      type: "folder",
    },
  });

  const resourceOrFolderIsDraggable = useResourceOrFolderIsDraggable();
  const elementDragOver = useElementDragOver();

  const combinedRef = (element: HTMLElement | null) => {
    setDraggableRef(element);
    setDroppableRef(element);
  };

  const folderIsDrag = resourceOrFolderIsDraggable.elementDrag === idFolder;
  const folderIsOver = elementDragOver.overId === idFolder;

  const styles = {
    transform: `translate3d(${(transform?.x ?? 0) / 1}px, ${
      (transform?.y ?? 0) / 1
    }px, 0)`,
  } as React.CSSProperties;

  return (
    <div ref={combinedRef} {...listeners} {...attributes} style={{ ...styles }}>
      {!folderIsDrag ? (
        <Card
          app={app}
          isSelectable={!resourceOrFolderIsDraggable.isDrag && isSelectable}
          isSelected={
            (!resourceOrFolderIsDraggable.isDrag && isSelected) || folderIsOver
          }
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
        <ElementDraggable name={name} app={app} elementType={"folder"} />
      )}
    </div>
  );
};

FolderCard.displayName = "FolderCard";

export default FolderCard;
