import { ReactNode } from "react";

interface AppHeaderProps {
  children: ReactNode;
}
export default function AppHeader({ children }: AppHeaderProps) {
  return (
    <div className="d-flex justify-content-between p-16 mx-n16 border-bottom">
      {children}
    </div>
  );
}
