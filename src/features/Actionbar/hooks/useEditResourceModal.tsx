import { useId, useState } from "react";

import { type SubmitHandler, useForm } from "react-hook-form";
import { toast } from "react-hot-toast";

interface useEditResourceModalProps {
  edit: boolean;
  onSuccess: () => void;
  onCancel: () => void;
}

interface FormInputs {
  title: string;
  description: string;
  enablePublic: boolean;
  safeSlug: string;
}

export default function useEditResourceModal({
  edit,
  onSuccess,
  onCancel,
}: useEditResourceModalProps) {
  const [cover, setCover] = useState<Record<string, string>>({
    name: "",
    image: "",
  });

  const {
    reset,
    register,
    handleSubmit,
    setFocus,
    formState: { errors, isSubmitting, isValid },
  } = useForm<FormInputs>({
    mode: "onChange",
  });

  const id = useId();

  const handleUploadImage = (preview: Record<string, string>) => {
    console.log(cover);

    setCover(preview);
  };

  const onSubmit: SubmitHandler<FormInputs> = async function (
    formData: FormInputs,
  ) {
    try {
      console.log(formData);
      // TODO: call API
      toast.success(
        <>
          <h3>Coming Soon!</h3>
          <p>Titre: {formData.title}</p>
          <p>Description: {formData.description}</p>
          <p>Public: {formData.enablePublic}</p>
        </>,
      );
      onSuccess?.();
    } catch (error) {
      console.error(error);
      toast.error(`Error: ${error}`);
    }
  };

  function onFormCancel() {
    reset();
    onCancel();
  }

  const formId = `resource_edit_modal_${id}`;

  return {
    formId,
    errors,
    isSubmitting,
    isValid,
    register,
    setFocus,
    handleSubmit,
    onFormCancel,
    onSubmit,
    handleUploadImage,
  };
}
