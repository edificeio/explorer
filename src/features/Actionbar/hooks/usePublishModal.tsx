import { useState } from "react";

import { useOdeClient } from "@ode-react-ui/core";
import useExplorerStore from "@store/index";
import {
  RESOURCE,
  type PublishParameters,
  type PublishResult,
} from "ode-ts-client";
import { type SubmitHandler, useForm } from "react-hook-form";
import { toast } from "react-hot-toast";

import {
  PublishModalError,
  PublishModalSuccess,
} from "../components/PublishModal";

interface ModalProps {
  onSuccess?: () => void;
}

interface InputProps {
  title: string;
  description: string;
  activityType: string;
  subjectArea: string;
  language: string;
  ageMin: string;
  ageMax: string;
  keyWords: string;
}

export default function usePublishModal({ onSuccess }: ModalProps) {
  const [cover, setCover] = useState<Record<string, string>>({
    name: "",
    image: "",
  });

  const { session, http, app } = useOdeClient();

  // * https://github.com/pmndrs/zustand#fetching-everything
  // ! https://github.com/pmndrs/zustand/discussions/913
  const getSelectedIResources = useExplorerStore(
    (state) => state.getSelectedIResources,
  );
  const selectedResources = useExplorerStore(
    (state) => state.selectedResources,
  );
  const publishApi = useExplorerStore((state) => state.publish);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
  } = useForm<InputProps>({ mode: "onChange" });

  function handleUploadImage(preview: Record<string, string>) {
    setCover(preview);
  }

  const publish: SubmitHandler<InputProps> = async (formData: InputProps) => {
    try {
      const userId = session ? session.user.userId : "";

      let coverBlob = new Blob();
      if (cover.image) {
        coverBlob = await http.get(cover.image, { responseType: "blob" });
      } else if (getSelectedIResources()[0].thumbnail) {
        coverBlob = await http.get(getSelectedIResources()[0].thumbnail, {
          responseType: "blob",
        });
      }

      const teacherAvatar: Blob = await http.get(
        `/userbook/avatar/${userId}?thumbnail=48x48`,
        { responseType: "blob" } as any,
      );

      const resAttachmentSchool = await http.get(
        `/directory/user/${userId}/attachment-school`,
        { responseType: "json" } as any,
      );

      const parameters: PublishParameters = {
        activityType: [formData.activityType],
        age: [formData.ageMin, formData.ageMax],
        application: app?.displayName || "",
        cover: coverBlob,
        description: formData.description,
        keyWords: formData.keyWords,
        language: formData.language,
        licence: "CC-BY",
        resourceId: selectedResources[0],
        subjectArea: [formData.subjectArea],
        teacherAvatar,
        title: formData.title,
        userId: session?.user.userId,
        userStructureName:
          resAttachmentSchool.name || session?.user.structureNames[0],
      };

      const resourceType = [RESOURCE.BLOG][0];
      const result: PublishResult = await publishApi(resourceType, parameters);

      if (result.success) {
        toast.success(<PublishModalSuccess result={result} />, {
          duration: Infinity,
        });
      } else {
        toast.error(<PublishModalError />, {
          duration: Infinity,
        });
      }
      onSuccess?.();
    } catch (e) {
      toast.error(<PublishModalError />, {
        duration: Infinity,
      });
    }
  };

  return {
    selectedResources: getSelectedIResources(),
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty, isValid },
    publish,
    handleUploadImage,
  };
}