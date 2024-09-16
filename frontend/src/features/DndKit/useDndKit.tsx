import {
  Active,
  DragEndEvent,
  DragOverEvent,
  DragStartEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import { getAncestors, useOdeClient, useToast } from "@edifice-ui/react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { DELAY, TOLERANCE } from "~/config";

import { useMoveItem } from "~/services/queries";
import { useStoreActions, useTreeData } from "~/store";
import { getChildrenIds } from "~/utils/getChildrenIds";

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
    fetchTreeData,
  } = useStoreActions();

  const treeData = useTreeData();

  const activationConstraint = {
    delay: DELAY,
    tolerance: TOLERANCE,
  };

  const mouseSensor = useSensor(MouseSensor, {
    activationConstraint,
  });
  const touchSensor = useSensor(TouchSensor, {
    activationConstraint,
  });
  const keyboardSensor = useSensor(KeyboardSensor);
  const sensors = useSensors(mouseSensor, touchSensor, keyboardSensor);

  const notifySuccess = (active: Active, folderName: string) => {
    if (active.data.current?.type === "resource") {
      toast.success(
        <>
          {t("explorer.dragged.resource")} <strong>{folderName}</strong>
        </>,
      );
    } else {
      toast.success(
        <>
          {t("explorer.dragged.folder")} <strong>{folderName}</strong>
        </>,
      );
    }
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    const { over, active } = event;

    const elementOver = over?.data.current;
    const elementActive = active.data.current;
    const ancestors = getAncestors(treeData, elementOver?.id);
    const childrenIds = getChildrenIds(treeData, elementOver?.id);

    if (
      elementActive?.id == elementOver?.id ||
      ancestors.includes(elementActive?.id) ||
      childrenIds.includes(elementActive?.id)
    ) {
      setResourceIds([]);
      setFolderIds([]);
    } else {
      const folderName = elementOver?.name ?? rootName;

      if (over) {
        try {
          await moveItem.mutate(elementOver?.id);
          notifySuccess(active, folderName);
        } catch (e) {
          console.error(e);
        } finally {
          setElementDragOver({
            isOver: false,
            overId: undefined,
            canMove: true,
            isTreeview: false,
          });
        }
      }
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
    const { over, active } = event;

    const elementOver = over?.data.current;
    const elementActive = active?.data.current;
    const ancestors = getAncestors(treeData, elementOver?.id);
    const childrenIds = getChildrenIds(treeData, elementOver?.id);

    if (over) {
      const dragOver = {
        isOver: true,
        canMove: false,
        overId: elementOver?.id,
        isTreeview: elementOver?.isTreeview,
      };

      if (
        elementActive?.id === elementOver?.id ||
        ancestors.includes(elementActive?.id) ||
        childrenIds.includes(elementActive?.id)
      ) {
        setElementDragOver({ ...dragOver, canMove: false });
      } else {
        fetchTreeData(elementOver?.id, queryClient);
        setElementDragOver({ ...dragOver, canMove: true });
      }
    } else {
      setElementDragOver({
        isOver: false,
        overId: undefined,
        canMove: true,
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