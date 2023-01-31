import { Toaster, resolveValue } from "react-hot-toast";

export default function HotToast() {
  return (
    <Toaster>
      {(toastElement) => (
        <div style={{ opacity: toastElement.visible ? 1 : 0 }}>
          {resolveValue(toastElement.message, toastElement)}
        </div>
      )}
    </Toaster>
  );
}
