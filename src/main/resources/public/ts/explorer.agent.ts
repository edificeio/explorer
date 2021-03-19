import { AbstractBusAgent, ACTION, CreateFolderParameters, CreateFolderResult, GetContextParameters, GetContextResult, GetResourcesParameters, GetResourcesResult, IContext, ID, RESOURCE } from 'ode-ts-client';
import { IHandler } from 'ode-ts-client/dist/ts/foundation/Agent';

let ContextData = {
	"folders":[]
   ,"filters":[]
   ,"orders":[{
	   "id": "name"
	  ,"defaultValue": "asc"
	  ,"name": "fake.key.order.name.asc"
	}
   ]
   ,"actions":[
		{"id":"comment",  "available":false}
	   ,{"id":"copy",     "available":false}
	   ,{"id":"create",   "available":false}
	   ,{"id":"delete",   "available":false}
	   ,{"id":"export",   "available":false}
	   ,{"id":"initialize", "available":true}
	   ,{"id":"manage",   "available":false}
	   ,{"id":"move",     "available":false}
	   ,{"id":"open",     "available":false}
	   ,{"id":"print",    "available":false}
	   ,{"id":"publish",  "available":false}
	   ,{"id":"search",   "available":true}
	   ,{"id":"share",    "available":false}
	  ]
   ,"pagination":{
	   "startIdx": 0
	  ,"maxIdx": 22
	  ,"pageSize": 10
   }
   ,"resources":[]
   ,"preferences":{
	   "view": "list"
   }
  };

/* Ce code est une recopie du code de l'agent mocké pour les tests unitaires.
 * Il s'agit d'un agent de gestion de ressources de type Folder.
 */
class ExplorerAgent extends AbstractBusAgent {
    constructor() {
        super( RESOURCE.FOLDER );
		this.registerHandlers();		
    }

    protected ctx:IContext|null = null;
    protected folders:any = { "default": {} };

    protected registerHandlers(): void {
        this.setHandler( ACTION.INITIALIZE,	this.onInitialize as unknown as IHandler );
        this.setHandler( ACTION.CREATE,     this.onCreate as unknown as IHandler );
        this.setHandler( ACTION.SEARCH,     this.onSearch as unknown as IHandler );
    }

    onInitialize( parameters:GetContextParameters ): Promise<GetContextResult> {
        return Promise.resolve().then( () => {
            return ContextData as GetContextResult
        }).then( ctx => {
            return this.ctx = ctx;
        });
    }

    onCreate( parameters:CreateFolderParameters ): Promise<CreateFolderResult> {
        let newFolderId:ID = "folder_" + Object.keys(this.folders).length;
        this.folders[newFolderId] = {id:newFolderId, name:parameters.name, type:"default", childNumber:0, createAt: new Date().toUTCString() };
        return Promise.resolve().then( () => this.folders[newFolderId] );
    }

    onSearch( parameters:GetResourcesParameters ): Promise<GetResourcesResult> {
        throw new Error("Method not implemented.");
    }
}

let agent = new ExplorerAgent();