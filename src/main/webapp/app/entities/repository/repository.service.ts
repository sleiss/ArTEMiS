import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { HttpClient, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { SERVER_API_URL } from '../../app.constants';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

@Injectable()
export class RepositoryService {

    private resourceUrl =  SERVER_API_URL + 'api/repository';

    constructor(private http: HttpClient) { }

    isClean(participationId: number): Observable<any> {
        return this.http.get<any>(`${this.resourceUrl}/${participationId}`)
            .map(data => ({isClean: data.isClean}));
    }

    commit(participationId: number): Observable<any> {
        return this.http.post<any>(`${this.resourceUrl}/${participationId}/commit`, {});
    }

    pull(participationId: number): Observable<any> {
        return this.http.post<any>(`${this.resourceUrl}/${participationId}/pull`, {});
    }

    buildlogs(participationId: number): Observable<Array<any>> {
        return this.http.get<any[]>(`${this.resourceUrl}/${participationId}/buildlogs`);
    }
}

@Injectable()
export class RepositoryFileService {

    private resourceUrl =  SERVER_API_URL + 'api/repository';

    constructor(private http: HttpClient) { }

    query(participationId: number): Observable<Array<any>> {
        return this.http.get<any[]>(`${this.resourceUrl}/${participationId}/files`);
    }

    get(participationId: number): Observable<any> {
        return this.http.get<any>(`${this.resourceUrl}/${participationId}/file`)
            .map(data => ({fileContent: data}));
    }

    update(participationId: number): Observable<any> {
        return this.http.put<any>(`${this.resourceUrl}/${participationId}/file`, {});
    }

    create(participationId: number): Observable<any> {
        return this.http.post<any>(`${this.resourceUrl}/${participationId}/file`, {});
    }

    delete(participationId: number): Observable<any> {
        return this.http.delete<any>(`${this.resourceUrl}/${participationId}/file`);
    }
}

@Injectable()
export class RepositoryInterceptor implements HttpInterceptor {
    constructor(private localStorage: LocalStorageService, private sessionStorage: SessionStorageService) {}

    // TODO: check why the auth.interceptor.ts does not add the authorization header
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        const token = this.localStorage.retrieve('authenticationToken') || this.sessionStorage.retrieve('authenticationToken');
        if (!!token) {
            const authReq = req.clone({
                headers: req.headers.set('Authorization', 'Bearer ' + token)
            });
            return next.handle(authReq);
        }
        return next.handle(req);
    }
}
