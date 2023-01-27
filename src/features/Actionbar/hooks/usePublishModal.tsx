import { useState } from "react";

import { useExplorerContext } from "@contexts/index";
import { PublishParameters } from "ode-ts-client";
import { SubmitHandler, useForm } from "react-hook-form";

interface PublishModalArg {
  onSuccess?: () => void;
}

interface PublishFormInputs {
  title: string;
  description: string;
  activityType: string;
  subjectArea: string;
  language: string;
  ageMin: string;
  ageMax: string;
  keyWords: string;
}

export default function usePublishModal({ onSuccess }: PublishModalArg) {
  const [cover, setCover] = useState<Record<string, string>>({
    name: "",
    image: "",
  });
  const { contextRef, selectedResources, selectedFolders, session, http, app } =
    useExplorerContext();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<PublishFormInputs>({ mode: "onChange" });

  function handleUploadImage(preview: Record<string, string>) {
    setCover(preview);
  }

  const publish: SubmitHandler<PublishFormInputs> = async (formData) => {
    console.log("formData=", formData);
    try {
      console.log("Publishing...");

      const userId = session ? session.user.userId : "";

      let coverBlob = new Blob();
      if (cover.image) {
        coverBlob = await http.get(cover.image, { responseType: "blob" });
      } else if (selectedResources[0].thumbnail) {
        coverBlob = await http.get(selectedResources[0].thumbnail, {
          responseType: "blob",
        });
      }

      console.log(coverBlob);

      const teacherAvatar: Blob = await http.get(
        `/userbook/avatar/${userId}?thumbnail=48x48`,
        { responseType: "blob" } as any,
      );

      const resAttachmentSchool = await http.get(
        `/directory/user/${userId}/attachment-school`,
        { responseType: "json" } as any,
      );

      const parameters: PublishParameters = {
        userId: session?.user.userId,
        title: formData.title,
        cover: coverBlob,
        language: formData.language,
        activityType: [formData.activityType],
        subjectArea: [formData.subjectArea],
        age: [formData.ageMin, formData.ageMax],
        description: formData.description,
        keyWords: formData.keyWords,
        application: app?.displayName || "",
        licence: "CC-BY",
        teacherAvatar,
        resourceId: selectedResources[0].id,
        userStructureName:
          resAttachmentSchool.name || session?.user.structureNames[0],
      };

      const resourceType = contextRef.current.getSearchParameters().types[0];

      const result = await contextRef.current.publish(resourceType, parameters);
      console.log(result);

      onSuccess?.();
    } catch (e) {
      // TODO display an alert?
      console.error(e);
    }
  };

  return {
    selectedResources,
    selectedFolders,
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
    publish,
    handleUploadImage,
  };
}
