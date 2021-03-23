import { AbstractBusAgent, ACTION, APP, CreateFolderParameters, CreateFolderResult, ERROR_CODE, GetContextParameters, GetContextResult, GetResourcesParameters, GetResourcesResult, IContext, ID, RESOURCE, ResourceType } from 'ode-ts-client';
import { IHandler } from 'ode-ts-client/dist/ts/foundation/Agent';
import * as ContextData from "./mocked/GetContext.json";
import * as SearchBlogFolder000Data from "./mocked/GetSearchBlogFolder000.json";

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
            return ContextData as GetContextResult;
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
        //console.log( parameters );
        // Only Blog and exercizer are supported by the prototype
        if( parameters.app!==APP.BLOG && parameters.app!==APP.EXERCIZER ) {
            throw new Error(ERROR_CODE.NOT_SUPPORTED);
        }
        if( parameters.types.findIndex((res)=>(res===RESOURCE.BLOG || res===RESOURCE.EXERCISE)) === -1) {
            throw new Error(ERROR_CODE.NOT_SUPPORTED);
        }
        return Promise.resolve().then( () => {
        // Simulate querying the server.
            switch(parameters.filters.folder) {
                case "blogFolder000": return SearchBlogFolder000Data as unknown as GetResourcesResult;
                default: throw new Error(ERROR_CODE.NOT_SUPPORTED);
            }
        });        
    }
}

let agent = new ExplorerAgent();