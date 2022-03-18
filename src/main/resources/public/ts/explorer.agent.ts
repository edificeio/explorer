import http from 'axios';
import { AbstractBusAgent, ACTION, GetContextParameters, GetContextResult, IActionParameters, IActionResult, IContext, IHttp, ISearchResults, ManagePropertiesParameters, ManagePropertiesResult, PROP_KEY, PROP_MODE, PROP_TYPE, RESOURCE, TransportFrameworkFactory } from 'ode-ts-client';
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
    protected http:IHttp = TransportFrameworkFactory.instance().newHttpInstance({
        // `paramsSerializer` is an optional function in charge of serializing `params`
        // (e.g. https://www.npmjs.com/package/qs, http://api.jquery.com/jquery.param/)
        paramsSerializer: function (params:any) {
            return Object.entries(params).map( p => {
                if( p[1] instanceof Array ) {
                    return p[1].map( value => `${p[0]}=${encodeURIComponent(value)}`).join('&')
                } else if( ['string','number','boolean'].indexOf(typeof p[1]) >= 0 ) {
                    return `${p[0]}=${encodeURIComponent(p[1] as any)}`
                }
                return '';
            }).join('&');
        },
    });

    protected registerHandlers(): void {
        this.setHandler( ACTION.INITIALIZE, this.onInit as unknown as IHandler );
        this.setHandler( ACTION.SEARCH,   	this.onSearch as unknown as IHandler );
        this.setHandler( ACTION.OPEN,   	this.onOpen as unknown as IHandler );
        this.setHandler( ACTION.CREATE,   	this.onCreate as unknown as IHandler );
        this.setHandler( ACTION.MANAGE,     this.onManage as unknown as IHandler );
    }
    private toQueryParams(p:GetContextParameters):any {
        let ret = {
            application:    p.app,
            start_idx:      p.pagination.startIdx,
            page_size:      p.pagination.pageSize,
            resource_type:  p.types
        } as any;
        if( p.orders ) {
            ret.order_by = Object.entries(p.orders).map( entry => `${entry[0]}:${entry[1]}` );
        }
        if( p.filters ) {
            Object.assign( ret, p.filters );
        }
        if( typeof p.search === 'string' ) {
            ret.search = p.search;
        }
        return ret;
    }

    onSearch( parameters:GetContextParameters ): Promise<ISearchResults> {
        return this.http.get<GetContextResult>('/explorer/resources', {
            queryParams: this.toQueryParams(parameters)
        });
/*
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
*/
    }

    onInit( parameters:GetContextParameters ): Promise<GetContextResult> {
        return this.http.get<GetContextResult>('/explorer/context', {
            queryParams: this.toQueryParams(parameters)
        });
/*
        return {
            actions:[{
                id: "create",
                available: true
            },{
                id: "open",
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
*/
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