import { useState } from "react";

import { type BlogResource, type BlogUpdate } from "ode-ts-client";

import { useUpdateResource } from "~services/queries/index";
import { useSelectedResources } from "~store/store";

export type PublicationType = "RESTRAINT" | "IMMEDIATE";

export default function useShareResourceModalFooterBlog() {
  const updateResource = useUpdateResource();
  const selectedResources = useSelectedResources();
  const {
    assetId,
    description,
    thumbnail,
    name,
    public: pub,
    trashed,
    slug,
    "publish-type": publishType,
  } = selectedResources[0] as BlogResource;
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
    await updateResource.mutate(payload);
  };
  return {
    radioPublicationValue,
    handleRadioPublicationChange,
  };
}
