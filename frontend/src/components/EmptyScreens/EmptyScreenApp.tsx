import {
  EmptyScreen,
  useOdeClient,
  useOdeTheme,
  usePaths,
} from '@edifice-ui/react';
import { type IAction } from 'edifice-ts-client';
import { useTranslation } from 'react-i18next';

import { useActions } from '~/services/queries';
import { useStoreContext } from '~/store';

export default function EmptyScreenApp(): JSX.Element {
  const [imagePath] = usePaths();

  const { appCode } = useOdeClient();
  const { theme } = useOdeTheme();
  const { t } = useTranslation();

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
      imageSrc={`${imagePath}/emptyscreen/illu-${appCode}.svg`}
      imageAlt={t('explorer.emptyScreen.app.alt', { ns: appCode })}
      title={labelEmptyScreenTitleApp()}
      text={labelEmptyScreenApp()}
    />
  );
}
