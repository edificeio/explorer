import { useCallback, useEffect, useState } from "react";

import { useOdeClient } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import {
  odeServices,
  type ShareRight,
  type ShareRightAction,
} from "ode-ts-client";
import { toast } from "react-hot-toast";

interface useShareResourceModalProps {
  onSuccess: () => void;
  onCancel: () => void;
}

export default function useShareResourceModal({
  onSuccess,
  onCancel,
}: useShareResourceModalProps) {
  const [shareRights, setShareRights] = useState<ShareRight[]>([]);
  const [shareRightActions, setShareRightActions] = useState<
    ShareRightAction[]
  >([]);
  const [showBookmarkInput, toggleBookmarkInput] = useState(false);
  const [radioPublicationValue, setRadioPublicationValue] =
    useState<string>("now");

  const getSelectedIResources = useExplorerStore(
    (state) => state.getSelectedIResources,
  );

  const { appCode } = useOdeClient();

  useEffect(() => {
    initShareRightsAndActions();
  }, []);

  /**
   * Init share data for the sharing table:
   * 1/ Initialize actions for the sharing table header
   * 2/ Initialize shareRights (users/groups) for the sharing table rows
   */
  const initShareRightsAndActions = useCallback(async () => {
    const shareRightActions: ShareRightAction[] = await odeServices
      .share()
      .getActionsForApp(appCode);
    console.log(shareRightActions);
    setShareRightActions(shareRightActions);

    const shareRights: ShareRight[] = await odeServices
      .share()
      .getRightsForResource(appCode, getSelectedIResources()[0]?.assetId);
    console.log(shareRights);
    setShareRights(shareRights);
  }, []);

  const handleRadioPublicationChange = (event: any) => {
    setRadioPublicationValue(event.target.value);
  };

  // TODO @dcau: type item
  const handleActionCheckbox = (item: any, actionName: string) => {
    // TODO @dcau: type prevItems
    setShareRights((prevItems: any[]) => {
      const newItems = [...prevItems];
      const index = newItems.findIndex((x) => x.id === item.id);
      const previousValue = newItems[index].actions[actionName];
      newItems[index] = {
        ...newItems[index],
        actions: { ...newItems[index].actions, [actionName]: !previousValue },
      };
      return newItems;
    });
  };

  const handleShare = () => {
    // TODO
    console.log("Sharing...");
    onSuccess?.();
    toast.success(<h3>Coming Soon :)</h3>);
  };

  const handleDeleteRow = (shareRightId: string) => {
    setShareRights(
      shareRights.filter((shareRight) => shareRight.id !== shareRightId),
    );
  };

  const hasRight = (
    shareRight: ShareRight,
    shareAction: ShareRightAction,
  ): boolean => {
    return shareRight.actions.includes(shareAction);
  };

  return {
    shareRightsModel: shareRights,
    shareActions: shareRightActions,
    showBookmarkInput,
    radioPublicationValue,
    toggleBookmarkInput,
    handleRadioPublicationChange,
    handleActionCheckbox,
    handleShare,
    handleDeleteRow,
    hasRight,
  };
}
