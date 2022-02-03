import http from 'axios';
import { AbstractBusAgent, ACTION, GetContextParameters, GetContextResult, IActionParameters, IActionResult, IContext, ISearchResults, ManagePropertiesParameters, ManagePropertiesResult, PROP_KEY, PROP_MODE, PROP_TYPE, RESOURCE } from 'ode-ts-client';
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
        this.setHandler( ACTION.SEARCH,   	this.onSearch as unknown as IHandler );
        this.setHandler( ACTION.OPEN,   	this.onOpen as unknown as IHandler );
        this.setHandler( ACTION.CREATE,   	this.onCreate as unknown as IHandler );
        this.setHandler( ACTION.MANAGE,     this.onManage as unknown as IHandler );
    }
    onSearch( parameters:GetContextParameters ): ISearchResults {
        return {
            folders:[0,1,2,3,4,5,6,7,8,9].map((e,index)=>({
                childNumber: index,
                id: "folder-"+index,
                name:"Folder "+index,
                type: ""
            })),
            pagination:{
                pageSize: 10,
                startIdx: 0
            },
            resources:[0,1,2,3,4,5,6,7,8,9].map((e,index)=>({
                id: "blog-0"+index,
                name:"Blog 0"+index,
                application: "blog",
                authorId: "auth"+index,
                authorName:" Auteur "+index,
                createdAt: "01/01/1990",
                modifiedAt: "01/01/1990",
                modifierId: "auth"+index,
                modifierName: "Auteur "+index,
                thumbnail: "https://loremflickr.com/240/160/",
                comments: 10,
                favorite: true,
                folderId: ""+index,
                public: true,
                shared: true,
                views: 100
            }))
        }
    }

    onInit( parameters:GetContextParameters ): GetContextResult {
        // TODO folder info
        return {
            actions:[{
                id: "create",
                available: true
            },{
                id: "delete",
                available: true
            }],
            filters:[],
            folders:[0,1,2,3,4,5,6,7,8,9].map((e,index)=>({
                childNumber: index,
                id: "folder-"+index,
                name:"Folder "+index,
                type: ""
            })),
            orders:[{i18n:"name", id:"name"},{i18n:"views", id:"views"},{i18n:"modifiedAt", id:"modifiedAt"}],
            pagination:{
                pageSize: 10,
                startIdx: 0
            },
            preferences:{
                view:"card"
            },
            resources:[0,1,2,3,4,5,6,7,8,9].map((e,index)=>({
                id: "blog-0"+index,
                name:"Blog 0"+index,
                application: "blog",
                authorId: "auth"+index,
                authorName:" Auteur "+index,
                createdAt: "01/01/1990",
                modifiedAt: "01/01/1990",
                modifierId: "auth"+index,
                modifierName: "Auteur "+index,
                thumbnail: "https://loremflickr.com/240/160/",
                comments: 10,
                favorite: true,
                folderId: ""+index,
                public: true,
                shared: true,
                views: 100
            }))
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