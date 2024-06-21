import {
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";

import { useMoveItem } from "~/services/queries";
import { useStoreActions } from "~/store";

export default function useDndKit() {
  const moveItem = useMoveItem();

  const {
    setResourceOrFolderIsDraggable,
    setElementDragOver,
    setResourceIds,
    setFolderIds,
    foldTreeItem,
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

  const handleDragEnd = async (event: any) => {
    const { over, active } = event;
    if (over && active.data.current.id !== over.data.current.id) {
      try {
        await moveItem.mutate(over.data.current.id);
      } catch (e) {
        console.error(e);
      }
    } else {
      setResourceIds([]);
      setFolderIds([]);
    }
    setResourceOrFolderIsDraggable({ isDrag: false, elementDrag: undefined });
  };

  const handleDragStart = (event: any) => {
    const { active } = event;
    setResourceOrFolderIsDraggable({ isDrag: true, elementDrag: active.id });
    if (active.data.current.type === "resource") {
      setResourceIds([active.data.current.id]);
    } else if (active.data.current.type === "folder") {
      setFolderIds([active.data.current.id]);
    }
  };

  const handleDragOver = (event: any) => {
    const { over } = event;
    if (over) {
      foldTreeItem(over.data.current.id);
      setElementDragOver({ isOver: true, overId: over.data.current.id });
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