import http from 'axios';
import { AbstractBusAgent, ACTION, CreateFolderParameters, CreateFolderResult, DeleteParameters, GetContextParameters, GetContextResult, GetSubFoldersResult, IActionParameters, IActionResult, IContext, ID, IHttp, IHttpResponse, ISearchResults, ManagePropertiesParameters, ManagePropertiesResult, MoveParameters, PROP_KEY, PROP_MODE, PROP_TYPE, RESOURCE, TransportFrameworkFactory } from 'ode-ts-client';
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
    protected checkHttpResponse:<R>(result:R)=>R = result => {
        if( this.http.latestResponse.status>=300 ) {
            throw this.http.latestResponse.statusText;
        }
        return result;
    }

    protected registerHandlers(): void {
        this.setHandler( ACTION.INITIALIZE, this.createContext as unknown as IHandler );
        this.setHandler( ACTION.SEARCH,     this.searchContext as unknown as IHandler );
        this.setHandler( ACTION.CREATE,     this.createFolder as unknown as IHandler );
        this.setHandler( ACTION.OPEN,       this.listSubfolders as unknown as IHandler );
        this.setHandler( ACTION.MOVE,       this.moveToFolder as unknown as IHandler );
        this.setHandler( ACTION.DELETE,     this.deleteFolders as unknown as IHandler );
        this.setHandler( ACTION.MANAGE,     this.onManage as unknown as IHandler );
    }

    /** Create a search context. */
    createContext( parameters:GetContextParameters ): Promise<GetContextResult> {
        return this.http.get<GetContextResult>('/explorer/context', {
            queryParams: this.toQueryParams(parameters)
        })
        .then( this.checkHttpResponse );;
    }

    /** Search / paginate within a search context. */
    searchContext( parameters:GetContextParameters ): Promise<ISearchResults> {
        return this.http.get<GetContextResult>('/explorer/resources', {
            queryParams: this.toQueryParams(parameters)
        })
        .then( this.checkHttpResponse );;
    }

    /** Create a new folder. */
    createFolder( parameters:CreateFolderParameters ): Promise<CreateFolderResult> {
        return this.http.post<CreateFolderResult>( '/explorer/folders', this.createFolderToBodyParams(parameters) )
        .then( this.checkHttpResponse );
    }

    /** Move resources/folders to a folder. */
    moveToFolder( parameters:MoveParameters ): Promise<IActionResult> {
        return this.http.post<IActionResult>( `/explorer/folders/${parameters.folderId}/move`, this.moveToBodyParams(parameters) )
        .then( this.checkHttpResponse );;
    }

    /** List subfolders of a parent folder. */
    listSubfolders( folderId:ID ): Promise<GetSubFoldersResult> {
        return this.http.get<GetSubFoldersResult>( `/explorer/folders/${folderId}/move` )
        .then( this.checkHttpResponse );;
    }

    /** Delete folders and/or resources. */
    deleteFolders( parameters:DeleteParameters ): Promise<IActionResult> {
        return this.http.deleteJson<IActionResult>( `/explorer/folders`, parameters )
        .then( this.checkHttpResponse );;
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
    private createFolderToBodyParams(p:CreateFolderParameters) {
        return {
            application:    p.app,
            resourceType:   p.type,
            parentId:       p.parentId,
            name:           p.name
        };
    }
    private moveToBodyParams(p:MoveParameters) {
        return {
            application:    p.application,
            resourceIds:    p.resourceIds,
            folderIds:      p.folderIds
        };
    }
}

let agent = new ExplorerAgent();