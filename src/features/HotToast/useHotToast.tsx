import { ReactNode } from "react";

import { Alert } from "@ode-react-ui/core";
import toast from "react-hot-toast";

export default function useHotToast() {
  const hotToast = {
    success: (message: string | ReactNode) =>
      toast.custom(
        <Alert type="success" isToast={true} className="mb-12">
          {message}
        </Alert>,
      ),
    error: (message: string | ReactNode) =>
      toast.custom(
        <Alert type="danger" isToast={true} className="mb-12">
          {message}
        </Alert>,
      ),
    info: (message: string | ReactNode) =>
      toast.custom(
        <Alert type="info" isToast={true} className="mb-12">
          {message}
        </Alert>,
      ),
    warning: (message: string | ReactNode) =>
      toast.custom(
        <Alert type="warning" isToast={true} className="mb-12">
          {message}
        </Alert>,
      ),
    loading: toast.loading,
  };

  return { hotToast };
}
