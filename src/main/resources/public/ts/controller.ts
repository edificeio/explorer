import { IIdiom, IUserInfo } from 'ode-ts-client';
import { session, conf } from 'ode-ngjs-front';
import { IController } from 'angular';

export class AppController implements IController {
	me?: IUserInfo;
	currentLanguage?: string;
	lang?: IIdiom;

	// IController implementation
	$onInit(): void {
	}


};
