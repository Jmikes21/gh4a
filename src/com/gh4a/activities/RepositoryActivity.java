package com.gh4a.activities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryBranch;
import org.eclipse.egit.github.core.RepositoryContents;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.RepositoryTag;
import org.eclipse.egit.github.core.service.StarService;
import org.eclipse.egit.github.core.service.WatcherService;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.gh4a.BackgroundTask;
import com.gh4a.Constants;
import com.gh4a.Gh4Application;
import com.gh4a.LoadingFragmentPagerActivity;
import com.gh4a.R;
import com.gh4a.db.BookmarksProvider;
import com.gh4a.fragment.CommitListFragment;
import com.gh4a.fragment.ContentListFragment;
import com.gh4a.fragment.ContentListFragment.ParentCallback;
import com.gh4a.fragment.RepositoryFragment;
import com.gh4a.loader.BranchListLoader;
import com.gh4a.loader.GitModuleParserLoader;
import com.gh4a.loader.IsStarringLoader;
import com.gh4a.loader.IsWatchingLoader;
import com.gh4a.loader.LoaderCallbacks;
import com.gh4a.loader.LoaderResult;
import com.gh4a.loader.RepositoryLoader;
import com.gh4a.loader.TagListLoader;
import com.gh4a.utils.IntentUtils;

public class RepositoryActivity extends LoadingFragmentPagerActivity implements ParentCallback {
    private static final int LOADER_REPO = 0;
    private static final int LOADER_BRANCHES = 1;
    private static final int LOADER_TAGS = 2;
    private static final int LOADER_WATCHING = 3;
    private static final int LOADER_STARRING = 4;
    private static final int LOADER_MODULEMAP = 5;

    public static final String EXTRA_INITIAL_PAGE = "initial_page";
    public static final int PAGE_REPO_OVERVIEW = 0;
    public static final int PAGE_FILES = 1;
    public static final int PAGE_COMMITS = 2;

    private static final int[] TITLES = new int[] {
        R.string.about, R.string.repo_files, R.string.commits
    };

    private LoaderCallbacks<Repository> mRepoCallback = new LoaderCallbacks<Repository>() {
        @Override
        public Loader<LoaderResult<Repository>> onCreateLoader(int id, Bundle args) {
            return new RepositoryLoader(RepositoryActivity.this, mRepoOwner, mRepoName);
        }

        @Override
        public void onResultReady(LoaderResult<Repository> result) {
            boolean success = !result.handleError(RepositoryActivity.this);
            if (success) {
                mRepository = result.getData();
                updateTitle();
                setTabsEnabled(true);
                // Apply initial page selection first time the repo is loaded
                if (mInitialPage >= PAGE_REPO_OVERVIEW && mInitialPage <= PAGE_COMMITS) {
                    getPager().setCurrentItem(mInitialPage);
                    mInitialPage = -1;
                }
            }
            setContentEmpty(!success);
            setContentShown(true);
            supportInvalidateOptionsMenu();
        }
    };

    private LoaderCallbacks<Map<String, String>> mGitModuleCallback =
            new LoaderCallbacks<Map<String, String>>() {
        @Override
        public Loader<LoaderResult<Map<String, String>>> onCreateLoader(int id, Bundle args) {
            return new GitModuleParserLoader(RepositoryActivity.this, mRepoOwner,
                    mRepoName, ".gitmodules", mSelectedRef);
        }
        @Override
        public void onResultReady(LoaderResult<Map<String, String>> result) {
            mGitModuleMap = result.getData();
            if (mContentListFragment != null) {
                mContentListFragment.onSubModuleNamesChanged(getSubModuleNames(mContentListFragment));
            }
        }
    };

    private LoaderCallbacks<List<RepositoryBranch>> mBranchCallback =
            new LoaderCallbacks<List<RepositoryBranch>>() {
        @Override
        public Loader<LoaderResult<List<RepositoryBranch>>> onCreateLoader(int id, Bundle args) {
            return new BranchListLoader(RepositoryActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<List<RepositoryBranch>> result) {
            if (!result.handleError(RepositoryActivity.this)) {
                stopProgressDialog(mProgressDialog);
                mBranches = result.getData();
                showBranchesDialog();
                getSupportLoaderManager().destroyLoader(LOADER_BRANCHES);
            }
        }
    };

    private LoaderCallbacks<List<RepositoryTag>> mTagCallback =
            new LoaderCallbacks<List<RepositoryTag>>() {
        @Override
        public Loader<LoaderResult<List<RepositoryTag>>> onCreateLoader(int id, Bundle args) {
            return new TagListLoader(RepositoryActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<List<RepositoryTag>> result) {
            if (!result.handleError(RepositoryActivity.this)) {
                stopProgressDialog(mProgressDialog);
                mTags = result.getData();
                showTagsDialog();
                getSupportLoaderManager().destroyLoader(LOADER_TAGS);
            }
        }
    };

    private LoaderCallbacks<Boolean> mWatchCallback = new LoaderCallbacks<Boolean>() {
        @Override
        public Loader<LoaderResult<Boolean>> onCreateLoader(int id, Bundle args) {
            return new IsWatchingLoader(RepositoryActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<Boolean> result) {
            if (!result.handleError(RepositoryActivity.this)) {
                mIsWatching = result.getData();
                mIsFinishLoadingWatching = true;
                supportInvalidateOptionsMenu();
            }
        }
    };

    private LoaderCallbacks<Boolean> mStarCallback = new LoaderCallbacks<Boolean>() {
        @Override
        public Loader<LoaderResult<Boolean>> onCreateLoader(int id, Bundle args) {
            return new IsStarringLoader(RepositoryActivity.this, mRepoOwner, mRepoName);
        }
        @Override
        public void onResultReady(LoaderResult<Boolean> result) {
            if (!result.handleError(RepositoryActivity.this)) {
                mIsStarring = result.getData();
                mIsFinishLoadingStarring = true;
                supportInvalidateOptionsMenu();
            }
        }
    };

    private String mRepoOwner;
    private String mRepoName;
    private ActionBar mActionBar;
    private ProgressDialog mProgressDialog;
    private int mInitialPage;

    private Stack<String> mDirStack;
    private Repository mRepository;
    private List<RepositoryBranch> mBranches;
    private List<RepositoryTag> mTags;
    private String mSelectedRef;

    private boolean mIsFinishLoadingWatching;
    private boolean mIsWatching;
    private boolean mIsFinishLoadingStarring;
    private boolean mIsStarring;

    private RepositoryFragment mRepositoryFragment;
    private ContentListFragment mContentListFragment;
    private CommitListFragment mCommitListFragment;

    private Map<String, String> mGitModuleMap;
    private Map<String, ArrayList<RepositoryContents>> mContentCache;
    private static final int MAX_CACHE_ENTRIES = 100;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (hasErrorView()) {
            return;
        }

        mDirStack = new Stack<String>();
        mContentCache = new LinkedHashMap<String, ArrayList<RepositoryContents>>() {
            private static final long serialVersionUID = -2379579224736389357L;
            @Override
            protected boolean removeEldestEntry(Entry<String, ArrayList<RepositoryContents>> eldest) {
                return size() > MAX_CACHE_ENTRIES;
            }
        };

        Bundle bundle = getIntent().getExtras();
        mRepoOwner = bundle.getString(Constants.Repository.OWNER);
        mRepoName = bundle.getString(Constants.Repository.NAME);
        mSelectedRef = bundle.getString(Constants.Repository.SELECTED_REF);
        mInitialPage = bundle.getInt(EXTRA_INITIAL_PAGE, -1);

        mActionBar = getSupportActionBar();
        mActionBar.setTitle(mRepoOwner + "/" + mRepoName);
        mActionBar.setDisplayHomeAsUpEnabled(true);

        setContentShown(false);
        setTabsEnabled(false);

        getSupportLoaderManager().initLoader(LOADER_REPO, null, mRepoCallback);
        if (Gh4Application.get(this).isAuthorized()) {
            getSupportLoaderManager().initLoader(LOADER_WATCHING, null, mWatchCallback);
            getSupportLoaderManager().initLoader(LOADER_STARRING, null, mStarCallback);
        }
    }

    private void updateTitle() {
        mActionBar.setSubtitle(getCurrentRef());
        invalidateFragments();
    }

    private String getCurrentRef() {
        if (!TextUtils.isEmpty(mSelectedRef)) {
            return mSelectedRef;
        }
        return mRepository.getMasterBranch();
    }

    @Override
    protected int[] getTabTitleResIds() {
        return TITLES;
    }

    @Override
    protected Fragment getFragment(int position) {
        switch (position) {
            case 0:
                mRepositoryFragment = RepositoryFragment.newInstance(mRepository, mSelectedRef);
                return mRepositoryFragment;
            case 1:
                if (mDirStack.isEmpty()) {
                    mDirStack.push(null);
                }
                String path = mDirStack.peek();
                mContentListFragment = ContentListFragment.newInstance(mRepository, path,
                        mContentCache.get(path), mSelectedRef);
                return mContentListFragment;
            case 2:
                mCommitListFragment = CommitListFragment.newInstance(mRepository, mSelectedRef);
                return mCommitListFragment;
        }
        return null;
    }

    @Override
    protected boolean fragmentNeedsRefresh(Fragment fragment) {
        if (fragment instanceof ContentListFragment) {
            if (mContentListFragment == null) {
                return true;
            }
            ContentListFragment clf = (ContentListFragment) fragment;
            if (mDirStack.isEmpty() || !TextUtils.equals(mDirStack.peek(), clf.getPath())) {
                return true;
            }
        } else if (fragment instanceof CommitListFragment && mCommitListFragment == null) {
            return true;
        } else if (fragment instanceof RepositoryFragment && mRepositoryFragment == null) {
            return true;
        }
        return false;
    }

    @Override
    public void onContentsLoaded(ContentListFragment fragment, List<RepositoryContents> contents) {
        if (contents == null) {
            return;
        }
        mContentCache.put(fragment.getPath(), new ArrayList<RepositoryContents>(contents));
        if (fragment.getPath() == null) {
            for (RepositoryContents content : contents) {
                if (RepositoryContents.TYPE_FILE.equals(content.getType())) {
                    if (content.getName().equals(".gitmodules")) {
                        LoaderManager lm = getSupportLoaderManager();
                        lm.restartLoader(LOADER_MODULEMAP, null, mGitModuleCallback);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onTreeSelected(RepositoryContents content) {
        String path = content.getPath();
        if (RepositoryContents.TYPE_DIR.equals(content.getType())) {
            mDirStack.push(path);
            invalidateFragments();
        } else if (mGitModuleMap != null && mGitModuleMap.get(path) != null) {
            String[] userRepo = mGitModuleMap.get(path).split("/");
            startActivity(IntentUtils.getRepoActivityIntent(this, userRepo[0], userRepo[1], null));
        } else {
            startActivity(IntentUtils.getFileViewerActivityIntent(this, mRepoOwner, mRepoName,
                    getCurrentRef(), content.getPath()));
        }
    }

    @Override
    public Set<String> getSubModuleNames(ContentListFragment fragment) {
        if (mGitModuleMap != null && fragment.getPath() == null) {
            return mGitModuleMap.keySet();
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        if (mDirStack != null && mDirStack.size() > 1 && getPager().getCurrentItem() == 1) {
            mDirStack.pop();
            invalidateFragments();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.repo_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean authorized = Gh4Application.get(this).isAuthorized();
        MenuItem watchAction = menu.findItem(R.id.watch);
        watchAction.setVisible(authorized);
        if (authorized) {
            if (!mIsFinishLoadingWatching) {
                MenuItemCompat.setActionView(watchAction, R.layout.ab_loading);
                MenuItemCompat.expandActionView(watchAction);
            } else if (mIsWatching) {
                watchAction.setTitle(R.string.repo_unwatch_action);
            } else {
                watchAction.setTitle(R.string.repo_watch_action);
            }
        }

        MenuItem starAction = menu.findItem(R.id.star);
        starAction.setVisible(authorized);
        if (authorized) {
            if (!mIsFinishLoadingStarring) {
                MenuItemCompat.setActionView(starAction, R.layout.ab_loading);
                MenuItemCompat.expandActionView(starAction);
            } else if (mIsStarring) {
                starAction.setTitle(R.string.repo_unstar_action);
            } else {
                starAction.setTitle(R.string.repo_star_action);
            }
        }
        if (mRepository == null) {
            menu.removeItem(R.id.branches);
            menu.removeItem(R.id.tags);
            menu.removeItem(R.id.bookmark);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected Intent navigateUp() {
        return IntentUtils.getUserActivityIntent(this, mRepoOwner);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.watch:
                MenuItemCompat.setActionView(item, R.layout.ab_loading);
                MenuItemCompat.expandActionView(item);
                new UpdateWatchTask().execute();
                return true;
            case R.id.star:
                MenuItemCompat.setActionView(item, R.layout.ab_loading);
                MenuItemCompat.expandActionView(item);
                new UpdateStarTask().execute();
                return true;
            case R.id.branches:
                if (mBranches == null) {
                    mProgressDialog = showProgressDialog(getString(R.string.loading_msg), true);
                    getSupportLoaderManager().initLoader(LOADER_BRANCHES, null, mBranchCallback);
                } else {
                    showBranchesDialog();
                }
                return true;
            case R.id.tags:
                if (mTags == null) {
                    mProgressDialog = showProgressDialog(getString(R.string.loading_msg), true);
                    getSupportLoaderManager().initLoader(LOADER_TAGS, null, mTagCallback);
                } else {
                    showTagsDialog();
                }
                return true;
            case R.id.refresh:
                MenuItemCompat.setActionView(item, R.layout.ab_loading);
                MenuItemCompat.expandActionView(item);
                refreshFragments();
                return true;
            case R.id.share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, mRepoOwner + "/" + mRepoName);
                shareIntent.putExtra(Intent.EXTRA_TEXT,  "https://github.com/" + mRepoOwner + "/" + mRepoName);
                shareIntent = Intent.createChooser(shareIntent, getString(R.string.share_title));
                startActivity(shareIntent);
                return true;
            case R.id.bookmark:
                Intent bookmarkIntent = new Intent(this, getClass());
                bookmarkIntent.putExtra(Constants.Repository.OWNER, mRepoOwner);
                bookmarkIntent.putExtra(Constants.Repository.NAME, mRepoName);
                bookmarkIntent.putExtra(Constants.Repository.SELECTED_REF, mSelectedRef);
                saveBookmark(mActionBar.getTitle().toString(), BookmarksProvider.Columns.TYPE_REPO,
                        bookmarkIntent, mActionBar.getSubtitle().toString());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showBranchesDialog() {
        String[] branchList = new String[mBranches.size()];
        int current = -1, master = -1;
        for (int i = 0; i < mBranches.size(); i++) {
            RepositoryBranch branch = mBranches.get(i);
            branchList[i] = branch.getName();
            if (branch.getName().equals(mSelectedRef)
                    || branch.getCommit().getSha().equals(mSelectedRef)) {
                current = i;
            }
            if (branch.getName().equals(mRepository.getMasterBranch())) {
                master = i;
            }
        }

        if (mSelectedRef == null && current == -1) {
            current = master;
        }

        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.repo_branches)
                .setSingleChoiceItems(branchList, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setSelectedRef(mBranches.get(which).getName());
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showTagsDialog() {
        String[] tagList = new String[mTags.size()];
        int current = -1;
        for (int i = 0; i < mTags.size(); i++) {
            RepositoryTag tag = mTags.get(i);
            tagList[i] = tag.getName();
            if (tag.getName().equals(mSelectedRef)
                    || tag.getCommit().getSha().equals(mSelectedRef)) {
                current = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(R.string.repo_tags)
                .setSingleChoiceItems(tagList, current, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setSelectedRef(mTags.get(which).getName());
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setSelectedRef(String selectedRef) {
        mSelectedRef = selectedRef;
        clearRefDependentFragments();
        updateTitle();
    }

    private void clearRefDependentFragments() {
        if (mRepositoryFragment != null) {
            mRepositoryFragment.setRef(mSelectedRef);
        }
        mContentListFragment = null;
        mCommitListFragment = null;
        mGitModuleMap = null;
        mDirStack.clear();
        mContentCache.clear();
    }

    private void refreshFragments() {
        mRepositoryFragment = null;
        clearRefDependentFragments();
        setContentShown(false);
        getSupportLoaderManager().getLoader(LOADER_REPO).onContentChanged();
    }

    private class UpdateStarTask extends BackgroundTask<Void> {
        public UpdateStarTask() {
            super(RepositoryActivity.this);
        }

        @Override
        protected Void run() throws IOException {
            StarService starService = (StarService)
                    Gh4Application.get(mContext).getService(Gh4Application.STAR_SERVICE);
            RepositoryId repoId = new RepositoryId(mRepoOwner, mRepoName);
            if (mIsStarring) {
                starService.unstar(repoId);
            } else {
                starService.star(repoId);
            }
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            mIsStarring = !mIsStarring;
            if (mRepositoryFragment != null) {
                mRepositoryFragment.updateStargazerCount(mIsStarring);
            }
            supportInvalidateOptionsMenu();
        }
    }

    private class UpdateWatchTask extends BackgroundTask<Void> {
        public UpdateWatchTask() {
            super(RepositoryActivity.this);
        }

        @Override
        protected Void run() throws IOException {
            WatcherService watcherService = (WatcherService)
                    Gh4Application.get(mContext).getService(Gh4Application.WATCHER_SERVICE);
            RepositoryId repoId = new RepositoryId(mRepoOwner, mRepoName);
            if (mIsWatching) {
                watcherService.unwatch(repoId);
            } else {
                watcherService.watch(repoId);
            }
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            mIsWatching = !mIsWatching;
            supportInvalidateOptionsMenu();
        }
    }
}
