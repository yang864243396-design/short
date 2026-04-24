package com.hongguo.theater;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hongguo.theater.ui.home.HomeFragment;
import com.hongguo.theater.ui.play.PlayFeedFragment;
import com.hongguo.theater.ui.profile.ProfileFragment;
import com.hongguo.theater.utils.AdSkipCache;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private Fragment activeFragment;
    private HomeFragment homeFragment;
    private PlayFeedFragment playFeedFragment;
    private ProfileFragment profileFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        setupFragments();
        setupBottomNav();
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        AdSkipCache.prefetchIfStale(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) return;
        if (!intent.getBooleanExtra("open_play_tab", false)) return;
        long afterDramaId = intent.getLongExtra("after_drama_id", 0L);
        bottomNav.setSelectedItemId(R.id.nav_play);
        if (afterDramaId > 0L) {
            playFeedFragment.setPendingScrollAfterDrama(afterDramaId);
        }
    }

    private void setupFragments() {
        homeFragment = new HomeFragment();
        playFeedFragment = new PlayFeedFragment();
        profileFragment = new ProfileFragment();

        activeFragment = homeFragment;

        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, profileFragment, "profile").hide(profileFragment)
                .add(R.id.fragment_container, playFeedFragment, "play").hide(playFeedFragment)
                .add(R.id.fragment_container, homeFragment, "home")
                .commit();
    }

    private void setupBottomNav() {
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment target;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                target = homeFragment;
            } else if (id == R.id.nav_play) {
                target = playFeedFragment;
            } else if (id == R.id.nav_profile) {
                target = profileFragment;
            } else {
                return false;
            }

            switchFragment(target);
            return true;
        });
    }

    private void switchFragment(Fragment target) {
        if (target == activeFragment) return;
        if (target == playFeedFragment) {
            playFeedFragment.ensureInitialFeedLoaded();
        }
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    public void showBottomNav(boolean show) {
        bottomNav.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    /** 切换到「我的」Tab（用于免广告解锁成功后回到个人中心）。 */
    public void openProfileTab() {
        bottomNav.setSelectedItemId(R.id.nav_profile);
    }
}
