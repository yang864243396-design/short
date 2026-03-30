package com.hongguo.theater;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hongguo.theater.ui.home.HomeFragment;
import com.hongguo.theater.ui.play.PlayFeedFragment;
import com.hongguo.theater.ui.profile.ProfileFragment;

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
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    public void showBottomNav(boolean show) {
        bottomNav.setVisibility(show ? android.view.View.VISIBLE : android.view.View.GONE);
    }
}
