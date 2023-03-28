import { useState } from "react";

import useExplorerStore from "@store/index";
import { type BlogResource, type BlogUpdate } from "ode-ts-client";

export type PublicationType = "RESTRAINT" | "IMMEDIATE";

export default function useShareResourceModalFooterBlog() {
  const updateResource = useExplorerStore((state) => state.updateResource);
  const getSelectedIResources = useExplorerStore(
    (state) => state.getSelectedIResources,
  );
  const {
    assetId,
    description,
    thumbnail,
    name,
    public: pub,
    trashed,
    slug,
    "publish-type": publishType,
  } = getSelectedIResources()[0] as BlogResource;
  const [radioPublicationValue, setRadioPublicationValue] =
    useState<PublicationType>(publishType || "IMMEDIATE");
  const handleRadioPublicationChange = async (value: PublicationType) => {
    setRadioPublicationValue(value);
    const payload: BlogUpdate = {
      description,
      entId: assetId,
      name,
      public: !!pub,
      slug: slug || "",
      thumbnail,
      trashed,
      "publish-type": value,
    };
    await updateResource(payload);
  };
  return {
    radioPublicationValue,
    handleRadioPublicationChange,
  };
}
