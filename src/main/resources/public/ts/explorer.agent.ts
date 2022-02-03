import http from 'axios';
import { AbstractBusAgent, ACTION, GetContextParameters, GetContextResult, IActionParameters, IActionResult, IContext, ManagePropertiesParameters, ManagePropertiesResult, PROP_KEY, PROP_MODE, PROP_TYPE, RESOURCE } from 'ode-ts-client';
import { IHandler } from 'ode-ts-client/dist/ts/explore/Agent';
declare var console:any;
console.log("explorer agent loading....")
class ExplorerAgent extends AbstractBusAgent {
    constructor() {
        super( RESOURCE.FOLDER );
		this.registerHandlers();	
        console.log("explorer agent initialized....")	
    }

    protected ctx:IContext|null = null;

    protected registerHandlers(): void {
        this.setHandler( ACTION.INITIALIZE,   	this.onInit as unknown as IHandler );
        this.setHandler( ACTION.OPEN,   	this.onOpen as unknown as IHandler );
        this.setHandler( ACTION.CREATE,   	this.onCreate as unknown as IHandler );
        this.setHandler( ACTION.MANAGE,     this.onManage as unknown as IHandler );
    }

    onInit( parameters:GetContextParameters ): GetContextResult {
        // TODO folder info
        return {
            actions:[],
            filters:[],
            folders:[],
            orders:[],
            pagination:{
                pageSize: 10,
                startIdx: 0
            },
            preferences:{
                view:"card"
            },
            resources:[]
        };
    }

    onOpen( parameters:GetContextParameters ): void {
        // TODO navigate to the correct URL. 
    }

    onCreate( parameters:IActionParameters ): Promise<IActionResult> {
        const res:IActionResult = "/explorer#/edit/new";
        return Promise.resolve().then( () => res );
    }

    onManage( parameters:ManagePropertiesParameters ): Promise<ManagePropertiesResult> {
        const res:ManagePropertiesResult = {
            genericProps:[{
                key:PROP_KEY.TITLE
            },{
                key:PROP_KEY.IMAGE
            },{
                key:PROP_KEY.URL
            }]
        }
        return Promise.resolve().then( () => res );
    }
}

let agent = new ExplorerAgent();