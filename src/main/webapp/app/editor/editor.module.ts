import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ArTEMiSSharedModule } from '../shared';
import { editorRoute } from './editor.route';
import { JhiAlertService } from 'ng-jhipster';
import { EditorComponent } from './editor.component';
import { RepositoryService } from '../entities/repository';
import { ResultService } from '../entities/result';
import { HomeComponent } from '../home';
import { ParticipationService } from '../entities/participation';
import { MomentModule } from 'angular2-moment';
import { JhiMainComponent } from '../layouts';
import { EditorAceComponent } from './ace/editor-ace.component';

const ENTITY_STATES = [
    ...editorRoute
];

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        MomentModule,
        RouterModule.forChild(ENTITY_STATES)
    ],
    declarations: [
        EditorComponent,
        EditorAceComponent
    ],
    exports: [
        EditorComponent
    ],
    entryComponents: [
        HomeComponent,
        EditorComponent,
        JhiMainComponent
    ],
    providers: [
        JhiAlertService,
        RepositoryService,
        ResultService,
        ParticipationService
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSEditorModule {}