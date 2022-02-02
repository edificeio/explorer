import { IAttributes, IController, IDirective, IScope } from "angular";
import { L10n, conf, http, session, TrackingService, TrackedAction, TrackedActionFromWidget } from "ode-ngjs-front";
declare var require:(file:string)=>any;
declare var console:any;
/* Controller for the directive */
export class ExplorerController implements IController {

}

interface ExplorerScope extends IScope {
}

class Directive implements IDirective<ExplorerScope,JQLite,IAttributes,IController[]> {
    restrict = 'E';
	template = require("./explorer.directive.html");
    scope = {
		pickTheme: "="
    };
	bindToController = true;
	controller = ["odeTracking", ExplorerController];
	controllerAs = 'ctrl';
	require = ['explorer'];

    link(scope:ExplorerScope, elem:JQLite, attr:IAttributes, controllers:IController[]|undefined) {
        console.log("[ExplorerDirective] link...")
    }
}

export function DirectiveFactory() {
	return new Directive();
}