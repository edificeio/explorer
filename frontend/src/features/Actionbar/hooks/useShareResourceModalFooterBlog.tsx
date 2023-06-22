import { useEffect, useState } from "react";

import { type BlogResource, type BlogUpdate } from "ode-ts-client";

import { useSelectedResources } from "~/store";

export type PublicationType = "RESTRAINT" | "IMMEDIATE";

export default function useShareResourceModalFooterBlog() {
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
  } = selectedResources.length > 0
    ? (selectedResources[0] as BlogResource)
    : {
        "publish-type": "",
        assetId: "",
        description: "",
        name: "",
        public: false,
        slug: "",
        thumbnail: "",
        trashed: false,
      };

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
    "publish-type": publishType,
  } satisfies BlogUpdate);

  useEffect(() => {
    if (radioPublicationValue) {
      setPayloadUpdatePublishType((prevPayload) => ({
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
