import { useState } from "react";

import { toast } from "react-hot-toast";

// TODO replace by dynamic model
const sharingModel: any = [
  {
    id: 0,
    hide: false,
    type: "group",
    username: "Enseignants du groupe CLG-DENIS POISSON",
    name: "Enseignants du groupe CLG-DENIS POISSON",
    avatarUrl:
      "https://media.istockphoto.com/id/1322277517/fr/photo/herbe-sauvage-dans-les-montagnes-au-coucher-du-soleil.jpg?s=612x612&w=0&k=20&c=tQ19uZQLlIFy8J6QWMyOL6lPt3pdSHBSDFHoXr1K_g0=",
    actions: {
      read: true,
      write: true,
      manage: true,
      comment: 0,
    },
  },
  {
    id: 2,
    hide: false,
    type: "group",
    username: "Eleves du groupe CLG-DENIS POISSON",
    name: "Eleves du groupe CLG-DENIS POISSON",
    actions: {
      read: true,
      write: true,
      manage: true,
      comment: 0,
    },
  },
];

// TODO replace by dynamic actions
const actions = [
  {
    displayName: "read",
  },
  {
    displayName: "write",
  },
  {
    displayName: "manage",
  },
  {
    displayName: "comment",
  },
];

interface useShareResourceModalProps {
  onSuccess: () => void;
  onCancel: () => void;
}

export default function useShareResourceModal({
  onSuccess,
  onCancel,
}: useShareResourceModalProps) {
  const [items, setItems] = useState<any>(sharingModel);
  const [showBookmarkInput, toggleBookmarkInput] = useState(false);
  const [radioPublicationValue, setRadioPublicationValue] =
    useState<string>("now");

  const handleRadioPublicationChange = (event: any) => {
    setRadioPublicationValue(event.target.value);
  };

  const handleActionCheckbox = (item: any, actionName: string) => {
    setItems((prevItems: any[]) => {
      const newItems = [...prevItems];
      const index = newItems.findIndex((x) => x.id === item.id);
      newItems[index].actions[actionName] =
        !newItems[index].actions[actionName];
      return newItems;
    });
  };

  const handleShare = () => {
    // TODO
    console.log("Sharing...");
    onSuccess?.();
    toast.success(<h3>Coming Soon :)</h3>);
  };

  return {
    items,
    actions,
    showBookmarkInput,
    radioPublicationValue,
    toggleBookmarkInput,
    handleRadioPublicationChange,
    handleActionCheckbox,
    handleShare,
  };
}
