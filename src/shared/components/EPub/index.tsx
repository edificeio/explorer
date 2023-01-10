import { Button } from "@ode-react-ui/core";
import { ArrowRight } from "@ode-react-ui/icons";

interface EPubProps<T> {
  src: T;
  alt: T;
  url: T;
  text: T;
  linkText: T;
}
export const EPub = ({
  src = "",
  alt = "",
  url = "",
  text = "",
  linkText = "Découvrir",
}: EPubProps<string>) => {
  return (
    <div className="p-16">
      <img src={src} alt={alt} />
      <p className="m-12">
        Découvrez plein d'activités à réutiliser dans la bibliothèque !
      </p>
      <Button rightIcon={<ArrowRight />} variant="ghost" color="primary">
        {linkText}
      </Button>
    </div>
  );
};
