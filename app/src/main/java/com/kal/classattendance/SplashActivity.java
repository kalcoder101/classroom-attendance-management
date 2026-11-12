package com.kal.classattendance;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000L;

    // Kept so we can cancel the infinite float loop when the activity is destroyed
    private ObjectAnimator logoFloatAnim;
    private final Handler splashHandler = new Handler(Looper.getMainLooper());

    // Delayed jump to Login; cancel it so backing out of the splash is final
    private final Runnable openLogin = () -> {
        // Cross-fade: LoginActivity fades in while the splash fades out
        ActivityOptions options = ActivityOptions.makeCustomAnimation(
                SplashActivity.this, R.anim.fade_in, R.anim.fade_out);
        startActivity(new Intent(SplashActivity.this, LoginActivity.class), options.toBundle());
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.splash);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        startSplashAnimations();

        splashHandler.postDelayed(openLogin, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        splashHandler.removeCallbacks(openLogin);
        if (logoFloatAnim != null) {
            logoFloatAnim.cancel();
        }
        super.onDestroy();
    }

    // Plays the logo pop-in, text slide-in and progress bar fill
    private void startSplashAnimations() {
        ImageView logo = findViewById(R.id.imgLogo);
        TextView appName = findViewById(R.id.tvAppName);
        TextView tagline = findViewById(R.id.tvTagline);
        ProgressBar progress = findViewById(R.id.progressSplash);

        // Logo pops in with a slight bounce
        logo.setAlpha(0f);
        logo.setScaleX(0.6f);
        logo.setScaleY(0.6f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(700L)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        // Then the logo floats gently up and down while the splash is showing
        logoFloatAnim = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, 0f, -8f);
        logoFloatAnim.setStartDelay(900L);
        logoFloatAnim.setDuration(1200L);
        logoFloatAnim.setRepeatCount(ValueAnimator.INFINITE);
        logoFloatAnim.setRepeatMode(ValueAnimator.REVERSE);
        logoFloatAnim.setInterpolator(new DecelerateInterpolator());
        logoFloatAnim.start();

        // App name and tagline slide up and fade in, slightly staggered
        appName.setAlpha(0f);
        appName.setTranslationY(24f);
        appName.animate().alpha(1f).translationY(0f)
                .setStartDelay(250L).setDuration(500L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        tagline.setAlpha(0f);
        tagline.setTranslationY(24f);
        tagline.animate().alpha(1f).translationY(0f)
                .setStartDelay(380L).setDuration(500L)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Progress bar fades in, then fills to 100% just before the transition fires
        progress.setAlpha(0f);
        progress.setProgress(0);
        progress.animate().alpha(1f)
                .setStartDelay(300L).setDuration(400L)
                .start();

        ObjectAnimator fillAnim = ObjectAnimator.ofInt(progress, "progress", 0, 100);
        fillAnim.setDuration(SPLASH_DELAY_MS - 300L);
        fillAnim.setInterpolator(new DecelerateInterpolator());
        fillAnim.start();
    }
}
