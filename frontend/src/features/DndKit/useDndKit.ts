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
    foldTreeItem,
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
    if (over && active.data.current?.id !== over.data.current?.id) {
      try {
        await moveItem.mutate(over.data.current?.id);
        if (active.data.current?.type === "resource") {
          toast.success(
            `Resource déplacée dans le dossier ${over.data.current?.name ?? rootName}`,
          );
        } else {
          toast.success(
            `Dossier déplacé dans le dossier ${over.data.current?.name ?? rootName}`,
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
    setResourceOrFolderIsDraggable({
      isDrag: true,
      elementDrag: active.data.current?.id,
    });
    if (active.data.current?.type === "resource") {
      setResourceIds([active.data.current?.id]);
    } else if (active.data.current?.type === "folder") {
      setFolderIds([active.data.current?.id]);
    }
  };

  const handleDragOver = (event: DragOverEvent) => {
    const { over } = event;

    if (over) {
      overTreeItem(over.data.current?.id, queryClient);
      foldTreeItem(over.data.current?.id);
      setElementDragOver({ isOver: true, overId: over.data.current?.id });
    } else {
      setElementDragOver({ isOver: false, overId: undefined });
    }
  };

  return {
    handleDragEnd,
    handleDragStart,
    handleDragOver,
    sensors,
  };
}
