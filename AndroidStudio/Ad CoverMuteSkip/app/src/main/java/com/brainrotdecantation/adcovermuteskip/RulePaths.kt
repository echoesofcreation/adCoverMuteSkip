package com.brainrotdecantation.adcovermuteskip

class RulePaths {
    object BluetoothMidiRecorderPaths {
        const val PACKAGE_NAME = "com.brainrotdecantation.bluetoothmidirecorder"
        val PATH_TV_N_EVENT = RulePathNodeTools.convert("$PACKAGE_NAME:id/tvNEvent")
    }

    object YoutubePaths {
        const val PACKAGE_NAME = "com.google.android.youtube"
        /*
         * Video player page
         */
        val WATCH_PLAYER = RulePathNodeTools.convert("$PACKAGE_NAME:id/watch_player") // Full player area, in portrait it doesn't contain the panel, in landscape it does
        val AD_PRESENCE = RulePathNodeTools.convert("$PACKAGE_NAME:id/ad_progress_text") // The best way to detect an ad
        val SKIP_BUTTON = RulePathNodeTools.convert("$PACKAGE_NAME:id/skip_ad_button") // The skip ad button, main video

        val EXPANDED_AD_PORTRAIT_BUTTON = RulePathNodeTools.convert(arrayOf("$PACKAGE_NAME:id/engagement_panel_wrapper", "$PACKAGE_NAME:id/header_container", 0, 0, 1)) // The giant ad below/on the side, it can sometimes appear when you pause the video without an ad video being triggered
        val EXPANDED_AD_PORTRAIT_BUTTON_PAUSE = RulePathNodeTools.convert(arrayOf("$PACKAGE_NAME:id/engagement_panel_wrapper", "$PACKAGE_NAME:id/header_container", 0, 1, 1)) // There is an extra item
        val EXPANDED_AD_LANDSCAPE_BUTTON_PAUSE = RulePathNodeTools.convert(arrayOf("$PACKAGE_NAME:id/fullscreen_engagement_panel_holder", "$PACKAGE_NAME:id/header_container", 0, 0, 1)) // Landscape left side ad

        val MINIMISED_AD = RulePathNodeTools.convert("$PACKAGE_NAME:id/main_companion_container") // The small ad below the video and above the title

        val RECOMMENDATION_LIST_ROOT = RulePathNodeTools.convert("$PACKAGE_NAME:id/watch_list") // The recommendation list root

        /*
         * Front page
         */
        val WATCH_PLAYER_FLOATING = RulePathNodeTools.convert("$PACKAGE_NAME:id/floaty_bar_controls_view")
        val AD_PRESENCE_FLOATING = RulePathNodeTools.convert("$PACKAGE_NAME:id/modern_miniplayer_ad_badge")
        val SKIP_BUTTON_FLOATING = RulePathNodeTools.convert("$PACKAGE_NAME:id/modern_miniplayer_skip_ad_button")

        val FEED_LIST_ROOT = RulePathNodeTools.convert("$PACKAGE_NAME:id/results")
    }
}