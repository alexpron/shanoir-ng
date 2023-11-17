/**
 * Shanoir NG - Import, manage and share neuroimaging data
 * Copyright (C) 2009-2019 Inria - https://www.inria.fr/
 * Contact us on https://project.inria.fr/shanoir/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see https://www.gnu.org/licenses/gpl-3.0.html
 */

import {formatDate} from '@angular/common';
import {HttpResponse} from '@angular/common/http';
import {ComponentRef, Injectable} from '@angular/core';
import {Observable, Subscription} from 'rxjs-compat';
import {take} from 'rxjs/operators';
import {Task, TaskState} from 'src/app/async-tasks/task.model';
import {Dataset} from 'src/app/datasets/shared/dataset.model';
import {DatasetService, Format} from 'src/app/datasets/shared/dataset.service';
import {ServiceLocator} from 'src/app/utils/locator.service';
import {SuperPromise} from 'src/app/utils/super-promise';
import {ConfirmDialogService} from '../components/confirm-dialog/confirm-dialog.service';
import {ConsoleService} from '../console/console.service';
import {NotificationsService} from '../notifications/notifications.service';
import {DownloadSetupAltComponent} from './download-setup-alt/download-setup-alt.component';
import {DownloadSetupComponent} from './download-setup/download-setup.component';
import {Queue} from './queue.model';
import {DatasetType} from "../../datasets/shared/dataset-type.model";

declare var JSZip: any;

export type Report = {
    taskId: number,
    folderName: string,
    requestedDatasetIds: number[],
    startTime: number,
    list?: {
        [key: number]: {
            status: 'QUEUED' | 'ERROR' | 'SUCCESS',
            error?: any,
            errorTime?: number
        }
    }
    nbSuccess?: number;
    nbError?: number;
    duration?: number;
    format: Format;
    nbQueues: number;
    unzip: boolean;
    converter: number;
};

@Injectable()
export class MassDownloadService {

    private downloadQueue: Queue = new Queue();
    readonly BROWSER_COMPAT_ERROR_MSG: string = 'browser not compatible';
    readonly REPORT_FILENAME: string = 'downloadReport.json';

    constructor(
        private datasetService: DatasetService,
        private notificationService: NotificationsService,
        private consoleService: ConsoleService,
        private dialogService: ConfirmDialogService) {
    }

    downloadAllByStudyId(studyId: number, format?: Format, downloadState?: TaskState): Promise<void> {
        return this.datasetService.getByStudyId(studyId).then(datasets => this.downloadByDatasets(datasets, format, downloadState));
    }

    downloadAllByExaminationId(examinationId: number, format?: Format, downloadState?: TaskState): Promise<void> {
        return this.datasetService.getByExaminationId(examinationId).then(datasets => this.downloadByDatasets(datasets, format, downloadState));
    }

    downloadAllByAcquisitionId(acquisitionId: number, format?: Format, downloadState?: TaskState) {
        return this.datasetService.getByAcquisitionId(acquisitionId).then(datasets => this.downloadByDatasets(datasets, format, downloadState));
    }

    downloadAllByStudyIdAndSubjectId(studyId: number, subjectId: number, format?: Format, downloadState?: TaskState): Promise<void> {
        return this.datasetService.getByStudyIdAndSubjectId(studyId, subjectId).then(datasets => this.downloadByDatasets(datasets, format, downloadState));
    }

    downloadByIds(datasetIds: number[], format?: Format, downloadState?: TaskState): Promise<void> {
        return this.datasetService.getByIds(new Set(datasetIds)).then(datasets => this.downloadByDatasets(datasets, format, downloadState));
    }

    /**
     * This method is the generic entry to download multiple datasets.
     */
    private downloadByDatasets(datasets: Dataset[], format?: Format, downloadState?: TaskState): Promise<void> {

        // Here check if we have DICOM datasets or not
        let hasDicoms: boolean = this.hasDicom(datasets);
        if (!hasDicoms || format == "dcm") {

            // Directly download if there are no dicom
            // And do not zip by default
            return this._downloadDatasets(datasets, format, 4, null , false, downloadState);
        }

        // Otherwise, we have dicom, we have to choose
        return this.openModal(format).then(ret => {
            if (ret != 'cancel') {
                return this._downloadDatasets(datasets, ret.format, ret.nbQueues, ret.converter ,ret.unzip, downloadState);
            } else return Promise.resolve();
        }).catch(error => {
            if (error == this.BROWSER_COMPAT_ERROR_MSG) {
                return this.openAltModal(format).then(ret => {
                    if (ret != 'cancel') {
                        return this._downloadAlt(datasets.map(ds => ds.id), ret, downloadState);
                    } else return Promise.resolve();
                });
            } else throw error;
        });
    }

    makeRootSubdirectory(handle: FileSystemDirectoryHandle, nbDatasets: number): Promise<FileSystemDirectoryHandle> {
        const dirName: string = 'Shanoir-download_' + nbDatasets + 'ds_' + formatDate(new Date(), 'dd-MM-YYYY_HH\'h\'mm', 'en-US');
        return handle.getDirectoryHandle(dirName, { create: true })
    }

    // This method is used to download in
    private _downloadAlt(input: number | number[], format: Format, downloadState?: TaskState): any {
        let task: Task = this.createTask((input as number[]).length);

        downloadState = new TaskState();
        downloadState.status = task.status;
        downloadState.progress = 0;

        return this.downloadQueue.waitForTurn().then(releaseQueue => {
            try {
                task.status = 2;
                task.lastUpdate = new Date();
                const start: number = Date.now();
                let downloadObs: Observable<TaskState> = this.datasetService.downloadDatasets(input as number[], format);

                let endPromise: SuperPromise<void> = new SuperPromise();

                let errorFunction = error => {
                    task.lastUpdate = new Date();
                    task.status = -1;
                    task.message = 'error while downloading : ' + (error?.message || error?.toString() || 'see logs');
                    this.notificationService.pushLocalTask(task);
                    releaseQueue();
                    endPromise.reject(error);
                }

                const flowSubscription: Subscription = downloadObs.subscribe(state => {
                    task.lastUpdate = new Date();
                    task.progress = state?.progress;
                    if (downloadState) {
                        downloadState.progress = task?.progress;
                    }
                    task.status = state?.status;
                    this.notificationService.pushLocalTask(task);
                }, errorFunction);

                const endSubscription: Subscription = downloadObs.last().subscribe(state => {
                    flowSubscription.unsubscribe();
                    let duration: number = Date.now() - start;
                    task.message = 'download completed in ' + duration + 'ms for ' + (input as number[]).length + ' datasets';
                    task.lastUpdate = new Date();
                    task.status = 1;
                    task.progress = 1;
                    downloadState.progress = task.progress;
                    this.notificationService.pushLocalTask(task);
                    endPromise.resolve();
                }, errorFunction);

                return endPromise.finally(() => {
                    flowSubscription.unsubscribe();
                    endSubscription.unsubscribe();
                    releaseQueue();
                });
            } catch (error) {
                releaseQueue();
                throw error;
            }
        });
    }

    /**
     * This method is the main entrypoint to download initially datasets
     */
    private _downloadDatasets(datasets: Dataset[], format: Format, nbQueues: number = 4, converter: number, unzip: boolean = false, downloadState?: TaskState, task?: Task, report?: Report, parentHandle?: FileSystemDirectoryHandle): Promise<void> {
        if (datasets.length == 0) return;
        let datasetIds = datasets.map(ds => ds.id); // copy array
        let directoryHandlePromise: Promise<FileSystemDirectoryHandle>;
        if (parentHandle) {
            directoryHandlePromise = Promise.resolve(parentHandle);
        } else {
            directoryHandlePromise = this.getFolderHandle()
                // add a subdirectory
                .then(handle => this.makeRootSubdirectory(handle, datasetIds.length));
        }
        return directoryHandlePromise.then(parentFolderHandle => { // ask the user's parent directory
            if (!task) task = this.createTask(datasetIds.length);
            if (downloadState) downloadState.status = task.status;
            return this.downloadQueue.waitForTurn().then(releaseQueue => {
                try {
                    task.status = 2;
                    task.lastUpdate = new Date();
                    this.notificationService.pushLocalTask(task);
                    const start: number = Date.now();
                    let ids = [...datasets.map(ds => ds.id)];
                    if (!report) report = this.initReport(datasetIds, task.id, parentFolderHandle.name, format, nbQueues, unzip, converter);
                    let promises: Promise<void>[] = [];
                    for (let queueIndex = 0; queueIndex < nbQueues; queueIndex++) { // build the dl queues
                        promises.push(
                            this.recursiveSave(ids.shift(), format, parentFolderHandle, ids, report, task, unzip, datasets, converter)
                        );
                    }
                    return Promise.all(promises).then(() => {
                        this.handleEnd(task, report, start);
                        this.writeMyFile(this.REPORT_FILENAME, JSON.stringify(report), parentFolderHandle);
                    }).catch(reason => {
                        task.message = 'download error : ' + reason;
                        this.notificationService.pushLocalTask(task);
                    }).finally(() => {
                        releaseQueue();
                    });
                } catch (error) {
                    releaseQueue();
                    throw error;
                }
            });
        }).catch(error => { /* the user clicked 'cancel' in the choose directory window */ });
    }

    private handleEnd(task: Task, report: Report, start: number) {
        task.lastUpdate = new Date();
        report.duration = Date.now() - start;
        task.report = JSON.stringify(report, null, 4);
        if (report.nbError > 0) {
            task.status = 3;
            const tab: string = '- ';
            task.message = (report.nbSuccess > 0 ? 'download partially succeed in ' : 'download failed in ') + report.duration + 'ms.\n'
                + tab + report.nbSuccess + ' datasets were successfully downloaded\n'
                + tab + report.nbError + ' datasets are (at least partially) in error and files could be missing.\n';
            JSON.stringify(report);
        } else {
            task.status = task.status == -1 ? -1 : 1;
            task.message = 'download completed in ' + report.duration + 'ms, ' + report.nbSuccess + ' datasets saved in the selected directory';
        }

        this.notificationService.pushLocalTask(task);
    }

    private recursiveSave(id: number, format: Format, userFolderHandle: FileSystemDirectoryHandle, remainingIds: number[], report: Report, task: Task, unzip: boolean = false, datasets?: Dataset[], converter?: number): Promise<void> {
        if (!id) return Promise.resolve();
        return this.saveDataset(id, format, userFolderHandle, report, task, unzip, datasets?.find(ds => ds.id == id), converter).then(() => {
            if (remainingIds.length > 0) {
                return this.recursiveSave(remainingIds.shift(), format, userFolderHandle, remainingIds, report, task, unzip, datasets, converter);
            } else {
                return Promise.resolve();
            }
        });
    }

    private saveDataset(id: number, format: Format, userFolderHandle: FileSystemDirectoryHandle, report: Report, task: Task, unzip: boolean = false, dataset?: Dataset, converter ? : number): Promise<void> {
        const metadataPromise: Promise<Dataset> = (dataset?.id == id && dataset.datasetAcquisition?.examination?.subject) ? Promise.resolve(dataset) : this.datasetService.get(id, 'lazy');
        const downloadPromise: Promise<HttpResponse<Blob>> = this.datasetService.downloadToBlob(id, format, converter);
        return Promise.all([metadataPromise, downloadPromise]).then(([dataset, httpResponse]) => {
            const blob: Blob = httpResponse.body;
            const filename: string = this.getFilename(httpResponse) || 'dataset_' + id;

            // Check ERRORS file in zip
            var zip: any = new JSZip();
            const unzipPromise: Promise<any> = zip.loadAsync(httpResponse.body).then(dataFiles => {
                if (dataFiles.files['ERRORS.json']) {
                    return dataFiles.files['ERRORS.json'].async('string').then(content => {
                        const errorsJson: any = JSON.parse(content);
                        report.list[id].status = 'ERROR';
                        report.list[id].error = errorsJson;
                        report.list[id].errorTime = Date.now();
                        task.lastUpdate = new Date();
                        task.status = 5;
                    });
                } else {
                    report.list[id].status = 'SUCCESS';
                    delete report.list[id].error;
                    delete report.list[id].errorTime;
                }
                return dataFiles;
            });

            if (unzip) {
                return unzipPromise.then(data => {
                    if (data) {
                        return Promise.all(
                            Object.entries(data.files)?.map(([name, file]) => {
                                task.message = 'unzipping file ' + name + ' from dataset n°' + id;
                                this.notificationService.pushLocalTask(task);
                                const path: string = this.buildAcquisitionPath(dataset) + filename.replace('.zip', '') + '/' + name;
                                let type: string;
                                if (name.endsWith('.json') || name.endsWith('.txt')) type = 'string';
                                else type = 'blob';
                                return (file as {async: (string) => Promise<Blob>}).async(type).then(blob => {
                                    task.message = 'saving file ' + name + ' from dataset n°' + id;
                                    this.notificationService.pushLocalTask(task);
                                    return this.writeMyFile(path, blob, userFolderHandle);
                                });
                            })
                        );
                    }
                });
            } else {
                const path: string = this.buildAcquisitionPath(dataset) + filename;
                task.message = 'saving dataset n°' + id;
                this.notificationService.pushLocalTask(task);
                return Promise.all([unzipPromise, this.writeMyFile(path, blob, userFolderHandle)]).then(() => null);
            }
        }).catch(reason => {
            report.list[id].status = 'ERROR';
            report.list[id].error = reason;
            report.list[id].errorTime = Date.now();
            task.lastUpdate = new Date();
            task.message = 'saving dataset n°' + id + ' failed';
            task.status = 5;
        }).finally(() => {
            if (report.list[id].status == 'SUCCESS') {
                task.lastUpdate = new Date();
                task.message = '(' + report.nbSuccess + '/' + report.requestedDatasetIds.length + ') dataset n°' + id + ' successfully saved';
                report.nbSuccess++;
            } else if (report.list[id].status == 'ERROR') {
                task.message = 'saving dataset n°' + id + ' failed';
                report.nbError++;
            }
            task.report = JSON.stringify(report, null, 4);
            this.writeMyFile(this.REPORT_FILENAME, task.report, userFolderHandle);
            task.lastUpdate = new Date();
            task.progress = (report.nbSuccess + report.nbError) / report.requestedDatasetIds.length;
            this.notificationService.pushLocalTask(task);
        });
    }

    private buildAcquisitionPath(dataset: Dataset): string {
        return dataset.datasetAcquisition?.examination?.subject?.name
                + '_' + dataset.datasetAcquisition?.examination?.subject?.id
                + '/'
                + dataset.datasetAcquisition?.examination?.comment
                + '_' + dataset.datasetAcquisition?.examination?.id
                + '/';
    }

    private writeMyFile(path: string, content: any, userFolderHandle: FileSystemDirectoryHandle): Promise<void> {
        path = path.trim();
        if (path.startsWith('/')) path = path.substring(1); // remove 1st '/'
        let splitted: string[];
        if (path.includes('/')) {
            splitted = path.split('/');
        } else {
            splitted = [path];
        }
        const filename = splitted.pop(); // separate filename from dir path
        if (splitted.length > 0) { // if dirs to create
            return this.createDirectoriesIn(splitted, userFolderHandle).then(lastFolderHandle => { // create the sub directories
                lastFolderHandle.getFileHandle(filename, { create: true } // create the file handle
                ).then(fileHandler => {
                    this.writeFile(fileHandler, content); // write the file
                });
            });
        } else { // if no dir to create
            userFolderHandle.getFileHandle(filename, { create: true }).then(fileHandler => {
                this.writeFile(fileHandler, content);
            });
        }
    }

    private async getFolderHandle(): Promise<FileSystemDirectoryHandle> {
        const options = {
            mode: 'readwrite'
        };
        // @ts-ignore
        const handle: FileSystemDirectoryHandle = await window.showDirectoryPicker(options);
        return handle;
    }

    private async writeFile(fileHandle: FileSystemFileHandle, contents) {
        // Create a FileSystemWritableFileStream to write to.
        const writable: FileSystemWritableFileStream = await fileHandle.createWritable();
        // Write the contents of the file to the stream.
        await writable.write({type: 'write', data: contents});
        // Close the file and write the contents to disk.
        await writable.close();
    }

    private createDirectoriesIn(dirs: string[], parentFolderHandle: FileSystemDirectoryHandle): Promise<FileSystemDirectoryHandle> {
        if (dirs.length == 0) return;
        const dirToCreate: string = dirs.shift(); // separate the first element
        return parentFolderHandle.getDirectoryHandle(dirToCreate, { create: true })
            .then(handle => {
                if (dirs.length > 0) {
                    return this.createDirectoriesIn(dirs, handle);
                } else return handle;
            });
    }

    private getFilename(response: HttpResponse<any>): string {
        const prefix = 'attachment;filename=';
        let contentDispHeader: string = response.headers.get('Content-Disposition');
        return contentDispHeader?.slice(contentDispHeader.indexOf(prefix) + prefix.length, contentDispHeader.length).replace('/', '_');
    }

    private initReport(datasetIds: number[], taskId: number, folderName: string, format: Format, nbQueues: number, unzip: boolean, converter: number): Report {
        let report: Report = {
            taskId: taskId,
            folderName: folderName,
            requestedDatasetIds: datasetIds,
            startTime: Date.now(),
            list: {},
            nbError: 0,
            nbSuccess: 0,
            format : format,
            nbQueues: nbQueues,
            unzip: unzip,
            converter: converter
        };
        datasetIds.forEach(id => report.list[id] = { status: 'QUEUED' });
        return report;
    }

    private createTask(nbDatasets: number): Task {
        return this._createTask('Download launched for ' + nbDatasets + ' datasets');
    }

    private _createTask(message: string): Task {
        let task: Task = new Task();
        task.id = Date.now();
        task.creationDate = new Date();
        task.lastUpdate = task.creationDate;
        task.message = message;
        task.progress = 0;
        task.status = 2;
        task.eventType = 'downloadDataset.event';
        this.notificationService.pushLocalTask(task);
        return task;
    }

    private openModal(format?: Format): Promise<{format: Format, nbQueues: number, converter: number, unzip: boolean} | 'cancel'> {
        // @ts-ignore
        if (window.showDirectoryPicker) { // test compatibility
            let modalRef: ComponentRef<DownloadSetupComponent> = ServiceLocator.rootViewContainerRef.createComponent(DownloadSetupComponent);
            modalRef.instance.format = format;
            return this.waitForEnd(modalRef);
        } else {
            return Promise.reject(this.BROWSER_COMPAT_ERROR_MSG);
        }
    }

    private openAltModal(format?: Format, compatibilityMsg: boolean = true): Promise<Format | 'cancel'> {
        let modalRef: ComponentRef<DownloadSetupAltComponent> = ServiceLocator.rootViewContainerRef.createComponent(DownloadSetupAltComponent);
        modalRef.instance.format = format;
        modalRef.instance.compatibilityMessage = compatibilityMsg;
        return this.waitForEnd(modalRef);
    }

    private waitForEnd(modalRef: ComponentRef<any>): Promise<any | 'cancel'> {
        let resPromise: SuperPromise<any | 'cancel'> = new SuperPromise();
        let result: Observable<any> = Observable.race([
            modalRef.instance.go,
            modalRef.instance.close.map(() => 'cancel')
        ]);
        result.pipe(take(1)).subscribe(ret => {
            modalRef.destroy();
            resPromise.resolve(ret);
        }, error => {
            modalRef.destroy();
            resPromise.reject(error);
        });
        return resPromise;
    }

    retry(task: Task): Promise<void> {
        // @ts-ignore
        if (!window.showDirectoryPicker) {
            throw new Error(this.BROWSER_COMPAT_ERROR_MSG);
        }
        let report: Report = this.getReportFromTask(task);
        let msg: string = 'Please now select the directory of the download you want to resume or retry. ';
        if (report) msg += 'Recorded directory name : ' + report.folderName;

        return this.dialogService.confirm('Select data directory', msg)
            .then(agreed => {
                if (agreed) {
                    return this.getFolderHandle().then(parentFolderHandle => {
                        return parentFolderHandle.getFileHandle(this.REPORT_FILENAME).then(fileHandle => {
                            return fileHandle.getFile().then(file => {
                                return file.text().then(text => {
                                    let reportFromFile: Report = JSON.parse(text);
                                    reportFromFile.nbError = 0;
                                    let noSuccessIds: number[] = Object.keys(report.list).filter(key => report.list[key].status != 'SUCCESS').map(key => parseInt(key));

                                    this.datasetService.getByIds(new Set(noSuccessIds)).then(datasets =>
                                        this._downloadDatasets(datasets, report.format, report.nbQueues, report.converter, report.unzip, null, task, report, parentFolderHandle)
                                    );
                                });
                            });
                        });
                    });
                }
            });
    }

    private getReportFromTask(task: Task): Report {
        try {
            return JSON.parse(task?.report);
        } catch (e) {
            this.consoleService.log('error', 'Can\'t parse the status from the recorded message', [e, task?.report]);
            return null;
        }
    }

    // This method checks if the list of given datasets has dicom or not.
    private hasDicom(datasets: Dataset[]) {
        for (let dataset of datasets) {
            if (dataset.type != DatasetType.Eeg && dataset.type != DatasetType.BIDS && dataset.datasetProcessing == null) {
                return true;
            }
        }
        return false;
    }
}

