import { useEffect, useState } from "react";

import { type BlogResource, type BlogUpdate } from "ode-ts-client";

import { useSelectedResources } from "~/store";

export type PublicationType = "RESTRAINT" | "IMMEDIATE";

export default function useShareResourceModalFooterBlog() {
  const selectedResources = useSelectedResources();
  // const { setPayloadUpdatePublishType } = useStoreActions();

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
  const [payloadUpdatePublishType, setPayloadUpdatePublishType] = useState({
    description,
    entId: assetId,
    name,
    public: !!pub,
    slug: slug || "",
    thumbnail,
    trashed,
  } satisfies BlogUpdate);

  useEffect(() => {
    if (radioPublicationValue) {
      setPayloadUpdatePublishType((prevPayload: BlogUpdate) => ({
        ...prevPayload,
        "publish-type": radioPublicationValue,
      }));
    }
  }, [radioPublicationValue]);

  const handleRadioPublicationChange = async (value: PublicationType) => {
    setRadioPublicationValue(value);
  };
  return {
    radioPublicationValue,
    payloadUpdatePublishType,
    handleRadioPublicationChange,
  };
}
