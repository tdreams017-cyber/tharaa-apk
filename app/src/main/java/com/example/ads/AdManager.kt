package com.example.ads

import android.content.Context
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.DeepForestCard
import com.example.ui.theme.DeepForestBorder
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.TextWhite
import com.example.ui.theme.TextGray
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    
    // Official Google AdMob Test Unit IDs (Replace these with your real AdMob unit IDs for release)
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun initialize(context: Context) {
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "AdMob SDK Initialized: ${status.adapterStatusMap}")
                loadInterstitial(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AdMob SDK: ${e.message}")
        }
    }

    fun loadInterstitial(context: Context) {
        if (isAdLoading || interstitialAd != null) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            TEST_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad Loaded successfully.")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad failed to load: ${adError.message}")
                }
            }
        )
    }

    fun showInterstitial(context: Context, onAdClosed: () -> Unit) {
        val activity = context as? android.app.Activity
        if (activity != null && interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial Ad dismissed.")
                    interstitialAd = null
                    loadInterstitial(context) // Preload the next one
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Interstitial Ad failed to show: ${adError.message}")
                    interstitialAd = null
                    onAdClosed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial Ad not ready yet or invalid activity context.")
            onAdClosed()
        }
    }
}

/**
 * A beautiful Compose Composable that integrates the AdMob Banner View.
 * If AdMob cannot load, it falls back to a gorgeous custom promotional premium banner
 * which increases user trust, informs them about your premium features, and showcases where
 * the ad will be placed.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    var showAdMobView by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DeepForestCard)
            .border(1.dp, DeepForestBorder, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (showAdMobView) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdManager.TEST_BANNER_ID
                        adListener = object : AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                Log.e("AdBanner", "Ad failed to load: ${error.message}")
                                showAdMobView = false // Switch to premium promotional fallback
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                },
                update = { adView ->
                    // No-op or update settings if needed
                }
            )
        } else {
            // Elegant premium fallback banner showing financial wisdom
            val marketingWisdom = listOf(
                "💸 الأصول هي التي تضع المال في جيبك - طريقك نحو الثراء",
                "📈 ابدأ صغيراً، فكر كبيراً، وتحرك بسرعة البرق!",
                "💡 استثمر في عقلك ومعرفتك الماليّة فهي المنجَم الحقيقي",
                "📊 صافي الربح هو المقياس الحقيقي لنجاح تجارتك"
            )
            val randomWisdom = remember { marketingWisdom.random() }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Premium Insight",
                        tint = GoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = randomWisdom,
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        maxLines = 2
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(GoldAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .border(1.dp, GoldAccent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "نصيحة اليوم",
                        color = GoldAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
