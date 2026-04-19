package com.nutomic.syncthingandroid;

import android.content.SharedPreferences;
import androidx.room.Room;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.webdav.persistence.WebDAVDatabase;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVFolderConfigDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVServerConfigDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVTransferCheckpointDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncEntryDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncRunDao;
import com.nutomic.syncthingandroid.webdav.persistence.repository.SyncStateRepository;
import com.nutomic.syncthingandroid.webdav.persistence.repository.WebDAVConfigRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SyncthingModule {

    private final SyncthingApp mApp;

    public SyncthingModule(SyncthingApp app) {
        mApp = app;
    }

    @Provides
    @Singleton
    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mApp);
    }

    @Provides
    @Singleton
    public NotificationHandler getNotificationHandler(SharedPreferences preferences) {
        return new NotificationHandler(mApp, preferences);
    }

    @Provides
    @Singleton
    public WebDAVDatabase getWebDAVDatabase() {
        return Room.databaseBuilder(
                mApp,
                WebDAVDatabase.class,
                WebDAVDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration().build();
    }

    @Provides
    @Singleton
    public WebDAVServerConfigDao getWebDAVServerConfigDao(WebDAVDatabase database) {
        return database.serverConfigDao();
    }

    @Provides
    @Singleton
    public WebDAVFolderConfigDao getWebDAVFolderConfigDao(WebDAVDatabase database) {
        return database.folderConfigDao();
    }

    @Provides
    @Singleton
    public WebDAVTransferCheckpointDao getWebDAVTransferCheckpointDao(WebDAVDatabase database) {
        return database.transferCheckpointDao();
    }

    @Provides
    @Singleton
    public WebDAVSyncEntryDao getWebDAVSyncEntryDao(WebDAVDatabase database) {
        return database.syncEntryDao();
    }

    @Provides
    @Singleton
    public WebDAVSyncRunDao getWebDAVSyncRunDao(WebDAVDatabase database) {
        return database.syncRunDao();
    }

    @Provides
    @Singleton
    public WebDAVConfigRepository getWebDAVConfigRepository(
            WebDAVServerConfigDao serverConfigDao,
            WebDAVFolderConfigDao folderConfigDao
    ) {
        return new WebDAVConfigRepository(serverConfigDao, folderConfigDao);
    }

    @Provides
    @Singleton
    public SyncStateRepository getSyncStateRepository(
            WebDAVTransferCheckpointDao transferCheckpointDao,
            WebDAVSyncEntryDao syncEntryDao,
            WebDAVSyncRunDao syncRunDao
    ) {
        return new SyncStateRepository(transferCheckpointDao, syncEntryDao, syncRunDao);
    }
}
