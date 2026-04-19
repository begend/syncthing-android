package com.nutomic.syncthingandroid.webdav.persistence.repository

import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVFolderConfigDao
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVServerConfigDao
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVFolderConfigEntity
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVServerConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDAVConfigRepository @Inject constructor(
    private val serverConfigDao: WebDAVServerConfigDao,
    private val folderConfigDao: WebDAVFolderConfigDao,
) {
    fun observeServerConfigs(): Flow<List<WebDAVServerConfigEntity>> = serverConfigDao.observeAll()

    suspend fun getServerConfig(id: String): WebDAVServerConfigEntity? = serverConfigDao.getById(id)

    suspend fun saveServerConfig(config: WebDAVServerConfigEntity) {
        serverConfigDao.insertOrReplace(config)
    }

    suspend fun deleteServerConfig(id: String) {
        serverConfigDao.deleteById(id)
    }

    fun observeAllFolders(): Flow<List<WebDAVFolderConfigEntity>> = folderConfigDao.observeAll()

    fun observeEnabledFolders(): Flow<List<WebDAVFolderConfigEntity>> = folderConfigDao.observeEnabledFolders()

    suspend fun getEnabledFolders(): List<WebDAVFolderConfigEntity> = folderConfigDao.getEnabledFolders()

    suspend fun getFolderConfig(id: String): WebDAVFolderConfigEntity? = folderConfigDao.getById(id)

    suspend fun saveFolderConfig(config: WebDAVFolderConfigEntity) {
        folderConfigDao.insertOrReplace(config)
    }

    suspend fun deleteFolderConfig(id: String) {
        folderConfigDao.deleteById(id)
    }
}
