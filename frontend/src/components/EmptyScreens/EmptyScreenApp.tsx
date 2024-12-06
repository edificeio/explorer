import { type IAction } from '@edifice.io/client';
import {
  EmptyScreen,
  useEdificeClient,
  useEdificeTheme,
} from '@edifice.io/react';
import { useTranslation } from 'react-i18next';

import { useEffect, useState } from 'react';
import { useActions } from '~/services/queries';
import { useStoreContext } from '~/store';

export default function EmptyScreenApp(): JSX.Element {
  const { appCode } = useEdificeClient();
  const { theme } = useEdificeTheme();
  const { t } = useTranslation();

  const [imageFullURL, setImageFullURL] = useState('');

  useEffect(() => {
    const getImageLibrary = async () => {
      const imageLibrary = await import(
        `@images/emptyscreen/illu-${appCode}.svg`
      );
      setImageFullURL(imageLibrary.default);
    };
    getImageLibrary();
  }, [appCode]);

  const config = useStoreContext((state) => state.config);
  const { data: actions } = useActions(config?.actions as IAction[]);

  const canCreate = actions?.find((action: IAction) => action.id === 'create');
  const labelEmptyScreenApp = () => {
    if (canCreate?.available && theme?.is1d) {
      // TODO should not have specific app i18n
      return t('explorer.emptyScreen.txt1d.create', { ns: appCode });
    } else if (canCreate?.available && !theme?.is1d) {
      return t('explorer.emptyScreen.txt2d.create', { ns: appCode });
    } else if (!canCreate?.available && theme?.is1d) {
      return t('explorer.emptyScreen.txt1d.consultation', { ns: appCode });
    } else {
      return t('explorer.emptyScreen.txt2d.consultation', { ns: appCode });
    }
  };

  const labelEmptyScreenTitleApp = () => {
    if (canCreate?.available && theme?.is1d) {
      return t('explorer.emptyScreen.title1d.create', { ns: appCode });
    } else if (canCreate?.available && !theme?.is1d) {
      return t('explorer.emptyScreen.title2d.create', { ns: appCode });
    } else if (!canCreate?.available && theme?.is1d) {
      return t('explorer.emptyScreen.title1d.consultation', { ns: appCode });
    } else {
      return t('explorer.emptyScreen.title2d.consultation', { ns: appCode });
    }
  };

  return (
    <EmptyScreen
      imageSrc={imageFullURL}
      imageAlt={t('explorer.emptyScreen.app.alt', { ns: appCode })}
      title={labelEmptyScreenTitleApp()}
      text={labelEmptyScreenApp()}
    />
  );
}
