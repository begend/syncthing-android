package com.nutomic.syncthingandroid.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.FolderActivity;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.settings.SettingsActivity;
import com.nutomic.syncthingandroid.webdav.WebDAVSyncAction;
import com.nutomic.syncthingandroid.webdav.WebDAVSyncService;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVFolderConfigDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVServerConfigDao;
import com.nutomic.syncthingandroid.webdav.persistence.dao.WebDAVSyncRunDao;
import com.nutomic.syncthingandroid.webdav.persistence.entity.WebDAVSyncRunEntity;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.AppPrefs;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.util.ConfigXml.OpenConfigException;
import com.nutomic.syncthingandroid.views.FoldersAdapter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

/**
 * Displays a list of all existing folders.
 */
public class FolderListFragment extends ListFragment implements SyncthingService.OnServiceStateChangeListener,
        AdapterView.OnItemClickListener {

    private static final String TAG = "FolderListFragment";

    private Boolean ENABLE_DEBUG_LOG = false;
    private Boolean ENABLE_VERBOSE_LOG = false;

    private ConfigRouter mConfigRouter = null;

    @Inject SharedPreferences mPreferences;
    @Inject WebDAVServerConfigDao mWebDAVServerConfigDao;
    @Inject WebDAVFolderConfigDao mWebDAVFolderConfigDao;
    @Inject WebDAVSyncRunDao mWebDAVSyncRunDao;

    private Runnable mUpdateListRunnable = new Runnable() {
        @Override
        public void run() {
            onTimerEvent();
            mUpdateListHandler.postDelayed(this, Constants.GUI_UPDATE_INTERVAL);
        }
    };

    private final Handler mUpdateListHandler = new Handler();
    private Boolean mLastVisibleToUser = false;
    private FoldersAdapter mAdapter;
    private SyncthingService.State mServiceState = SyncthingService.State.INIT;
    private final ExecutorService mWebDAVCardExecutor = Executors.newSingleThreadExecutor();
    private View mWebDAVHeaderView;
    private TextView mWebDAVSummaryView;
    private Button mWebDAVOpenButton;
    private Button mWebDAVSyncAllButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((SyncthingApp) getActivity().getApplication()).component().inject(this);
        ENABLE_VERBOSE_LOG = AppPrefs.getPrefVerboseLog(mPreferences);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser)
    {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // User switched to the current tab, start handler.
            startUpdateListHandler();
        } else {
            // User switched away to another tab, stop handler.
            stopUpdateListHandler();
        }
        mLastVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onPause() {
        stopUpdateListHandler();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mWebDAVCardExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mLastVisibleToUser) {
            startUpdateListHandler();
        }
    }

    private void startUpdateListHandler() {
        LogV("startUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
        mUpdateListHandler.post(mUpdateListRunnable);
    }

    private void stopUpdateListHandler() {
        LogV("stopUpdateListHandler");
        mUpdateListHandler.removeCallbacks(mUpdateListRunnable);
    }

    @Override
    public void onServiceStateChange(SyncthingService.State currentState) {
        mServiceState = currentState;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setHasOptionsMenu(true);
        setEmptyText(getString(R.string.folder_list_empty));
        ensureWebDAVHeader(view);
        getListView().setOnItemClickListener(this);
        refreshWebDAVHeaderSummary();
    }

    /**
     * Invokes updateList which polls the REST API for folder status updates
     *  while the user is looking at the current tab.
     */
    private void onTimerEvent() {
        MainActivity mainActivity = (MainActivity) getActivity();
        if (mainActivity == null) {
            return;
        }
        if (mainActivity.isFinishing()) {
            return;
        }
        if (ENABLE_DEBUG_LOG) {
            LogV("Invoking updateList on UI thread");
        }
        mainActivity.runOnUiThread(FolderListFragment.this::updateList);
    }

    /**
     * Refreshes ListView by updating folders and info.
     *
     * Also creates adapter if it doesn't exist yet.
     */
    private void updateList() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }
        if (mConfigRouter == null) {
            mConfigRouter = new ConfigRouter(activity);
        }
        List<Folder> folders;
        RestApi restApi = activity.getApi();
        try {
            folders = mConfigRouter.getFolders(restApi);
        } catch (OpenConfigException e) {
            Log.e(TAG, "Failed to parse existing config. You will need support from here ...");
            return;
        }
        if (folders == null) {
            return;
        }
        if (mAdapter == null) {
            mAdapter = new FoldersAdapter(activity);
            setListAdapter(mAdapter);
        }
        mAdapter.setRestApi(restApi);

        // Prevent scroll position reset due to list update from clear().
        mAdapter.setNotifyOnChange(false);
        mAdapter.clear();
        mAdapter.addAll(folders);
        mAdapter.notifyDataSetChanged();
        setListShown(true);
        refreshWebDAVHeaderSummary();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (mWebDAVHeaderView != null) {
            int headerCount = getListView().getHeaderViewsCount();
            if (i < headerCount) {
                return;
            }
            i -= headerCount;
        }
        Intent intent = new Intent(getActivity(), FolderActivity.class)
                .putExtra(FolderActivity.EXTRA_IS_CREATE, false)
                .putExtra(FolderActivity.EXTRA_FOLDER_ID, mAdapter.getItem(i).id);
        startActivity(intent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.folder_list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.add_folder) {
            Intent intent = new Intent(getActivity(), FolderActivity.class)
                    .putExtra(FolderActivity.EXTRA_IS_CREATE, true);
            startActivity(intent);
            return true;
        } else if (itemId == R.id.rescan_all) {
            rescanAll();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void rescanAll() {
        SyncthingActivity activity = (SyncthingActivity) getActivity();
        if (activity == null || getView() == null || activity.isFinishing()) {
            return;
        }
        RestApi restApi = activity.getApi();
        if (restApi == null || !restApi.isConfigLoaded()) {
            Log.e(TAG, "rescanAll skipped because Syncthing is not running.");
            return;
        }
        restApi.rescanAll();
    }

    private void LogV(String logMessage) {
        if (ENABLE_VERBOSE_LOG) {
            Log.v(TAG, logMessage);
        }
    }

    private void ensureWebDAVHeader(View rootView) {
        if (mWebDAVHeaderView != null || getListView() == null) {
            return;
        }
        mWebDAVHeaderView = LayoutInflater.from(getActivity())
                .inflate(R.layout.header_webdav_home_card, getListView(), false);
        mWebDAVSummaryView = mWebDAVHeaderView.findViewById(R.id.webdavHomeSummary);
        mWebDAVOpenButton = mWebDAVHeaderView.findViewById(R.id.webdavHomeOpen);
        mWebDAVSyncAllButton = mWebDAVHeaderView.findViewById(R.id.webdavHomeSyncAll);
        mWebDAVOpenButton.setOnClickListener(v -> openWebDAVSyncScreen());
        mWebDAVSyncAllButton.setOnClickListener(v -> triggerWebDAVSyncAll());
        getListView().addHeaderView(mWebDAVHeaderView, null, false);
    }

    private void openWebDAVSyncScreen() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(activity, SettingsActivity.class);
        intent.putExtra(SettingsActivity.EXTRA_START_DESTINATION, "WebDAVSync");
        startActivity(intent);
    }

    private void triggerWebDAVSyncAll() {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) {
            return;
        }
        Intent intent = new Intent(activity, WebDAVSyncService.class);
        intent.setAction(WebDAVSyncAction.ACTION_SYNC_ALL);
        intent.putExtra(WebDAVSyncAction.EXTRA_TRIGGER_REASON, "manual_home_card");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            activity.startForegroundService(intent);
        } else {
            activity.startService(intent);
        }
    }

    private void refreshWebDAVHeaderSummary() {
        if (mWebDAVSummaryView == null || getActivity() == null) {
            return;
        }
        mWebDAVCardExecutor.execute(() -> {
            int serverCount = mWebDAVServerConfigDao.getCount();
            int folderCount = mWebDAVFolderConfigDao.getCount();
            int enabledFolderCount = mWebDAVFolderConfigDao.getEnabledFolderCount();
            WebDAVSyncRunEntity latestRun = mWebDAVSyncRunDao.getLatestRun();
            String latestRunSummary = buildLatestRunSummary(latestRun);
            String summary = getString(
                    R.string.webdav_home_summary_format,
                    serverCount,
                    enabledFolderCount,
                    folderCount,
                    latestRunSummary
            );
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (mWebDAVSummaryView != null) {
                        mWebDAVSummaryView.setText(summary);
                    }
                    if (mWebDAVSyncAllButton != null) {
                        mWebDAVSyncAllButton.setEnabled(enabledFolderCount > 0);
                    }
                });
            }
        });
    }

    private String buildLatestRunSummary(WebDAVSyncRunEntity latestRun) {
        if (latestRun == null) {
            return getString(R.string.webdav_status_never_synced);
        }
        switch (latestRun.getState()) {
            case "COMPLETED":
                return getString(R.string.webdav_status_completed);
            case "FAILED":
                return getString(R.string.webdav_status_failed);
            case "RUNNING":
                return getString(R.string.webdav_status_running);
            case "SKIPPED":
                return getString(R.string.webdav_status_skipped);
            case "CANCELLED":
                return getString(R.string.webdav_status_cancelled);
            default:
                return latestRun.getState();
        }
    }
}
