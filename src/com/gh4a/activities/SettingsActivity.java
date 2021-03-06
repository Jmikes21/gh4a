package com.gh4a.activities;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;

import com.gh4a.Gh4Application;
import com.gh4a.R;
import com.gh4a.fragment.SettingsFragment;

public class SettingsActivity extends ActionBarActivity implements
        SettingsFragment.OnStateChangeListener {
    public static final String RESULT_EXTRA_THEME_CHANGED = "theme_changed";
    public static final String RESULT_EXTRA_AUTH_CHANGED = "auth_changed";

    private static final String STATE_KEY_RESULT = "result";

    private Intent mResultIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Gh4Application.THEME);
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.settings);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null) {
            mResultIntent = new Intent();

            getSupportFragmentManager()
                    .beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        } else {
            mResultIntent = savedInstanceState.getParcelable(STATE_KEY_RESULT);
        }

        setResult(RESULT_OK, mResultIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_KEY_RESULT, mResultIntent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onThemeChanged() {
        mResultIntent.putExtra(RESULT_EXTRA_THEME_CHANGED, true);
        recreate();
    }

    @Override
    public void onAuthStateChanged() {
        mResultIntent.putExtra(RESULT_EXTRA_AUTH_CHANGED, true);
    }

    @Override
    public void recreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            super.recreate();
        } else {
            final Intent intent = getIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            startActivity(intent);
            overridePendingTransition(0, 0);

            finish();
            overridePendingTransition(0, 0);
        }
    }
}
