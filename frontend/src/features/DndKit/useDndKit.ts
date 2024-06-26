import {
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { useOdeClient, useToast } from "@edifice-ui/react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";

import { useMoveItem } from "~/services/queries";
import { useStoreActions } from "~/store";

export default function useDndKit() {
  const queryClient = useQueryClient();
  const moveItem = useMoveItem();
  const toast = useToast();
  const { appCode } = useOdeClient();
  const { t } = useTranslation(["common", appCode]);
  const rootName: string = t("explorer.filters.mine", {
    ns: appCode,
  });

  const {
    setResourceOrFolderIsDraggable,
    setElementDragOver,
    setResourceIds,
    setFolderIds,
    overTreeItem,
  } = useStoreActions();

  const activationConstraint = {
    delay: 200,
    tolerance: 5,
  };

  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint,
  });
  const touchSensor = useSensor(TouchSensor, {
    activationConstraint,
  });
  const keyboardSensor = useSensor(KeyboardSensor);
  const sensors = useSensors(mouseSensor, touchSensor, keyboardSensor);

  const handleDragEnd = async (event: DragEndEvent) => {
    const { over, active } = event;
    const elementOver = over?.data.current;
    const elementActive = active.data.current;

    if (over && elementActive?.id !== elementOver?.id) {
      try {
        await moveItem.mutate(elementOver?.id);
        if (active.data.current?.type === "resource") {
          toast.success(
            `Resource déplacée dans le dossier ${elementOver?.name ?? rootName}`,
          );
        } else {
          toast.success(
            `Dossier déplacé dans le dossier ${elementOver?.name ?? rootName}`,
          );
        }
      } catch (e) {
        console.error(e);
      }
    } else {
      setResourceIds([]);
      setFolderIds([]);
    }
    setResourceOrFolderIsDraggable({ isDrag: false, elementDrag: undefined });
  };

  const handleDragStart = (event: DragStartEvent) => {
    const { active } = event;
    const elementActive = active.data.current;

    if (elementActive?.type === "resource") {
      setResourceIds([elementActive?.id]);
    } else if (elementActive?.type === "folder") {
      setFolderIds([elementActive?.id]);
    }

    setResourceOrFolderIsDraggable({
      isDrag: true,
      elementDrag: elementActive?.id,
    });
  };

  const handleDragOver = (event: DragOverEvent) => {
    const { over } = event;
    const elementOver = over?.data.current;

    if (over) {
      overTreeItem(elementOver?.id, queryClient);
      setElementDragOver({
        isOver: true,
        overId: elementOver?.id,
        isTreeview: elementOver?.folderTreeview,
      });
    } else {
      setElementDragOver({
        isOver: false,
        overId: undefined,
        isTreeview: false,
      });
    }
  };

  return {
    handleDragEnd,
    handleDragStart,
    handleDragOver,
    sensors,
  };
}
