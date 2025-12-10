package com.tyron.code;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.tyron.code.ui.main.HomeFragment;
import org.codeassist.unofficial.R;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED) {
    ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // SDK 34+
    if (ContextCompat.checkSelfPermission(this,
            Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC)
        != PackageManager.PERMISSION_GRANTED) {

        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC},
            1001);
    }
    }
    HomeFragment homeFragment = new HomeFragment();
    if (getSupportFragmentManager().findFragmentByTag(HomeFragment.TAG) == null) {
      getSupportFragmentManager()
          .beginTransaction()
          .replace(R.id.fragment_container, homeFragment, HomeFragment.TAG)
          .commit();
    }

    // 🔋 Request battery optimization exemption
    if (!isIgnoringBatteryOptimizations(this)) {
      requestIgnoreBatteryOptimizations(this);
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean onKeyShortcut(int keyCode, KeyEvent event) {
    return super.onKeyShortcut(keyCode, event);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    return super.onKeyUp(keyCode, event);
  }

  // ============================================================
  // Battery Optimization helpers
  // ============================================================

  private static boolean isIgnoringBatteryOptimizations(Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true; // Pre-Doze devices
    PowerManager pm = (PowerManager) activity.getSystemService(POWER_SERVICE);
    return pm != null && pm.isIgnoringBatteryOptimizations(activity.getPackageName());
  }

  private static void requestIgnoreBatteryOptimizations(Activity activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
    if (isIgnoringBatteryOptimizations(activity)) return;

    try {
      Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
      intent.setData(Uri.parse("package:" + activity.getPackageName()));
      activity.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      // Fallback: open system settings page
      try {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        activity.startActivity(intent);
      } catch (Exception ignored) {
        // Last resort: app settings
        Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        appDetails.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivity(appDetails);
      }
    }
  }
}
