## Youtube DOM documentation

# Player screen

The player and the way to see if an add is running  
val PATH_WATCH_PLAYER = NodeFunc.convert("$PACKAGE_NAME:id/watch_player") // The main player  
val PATH_AD_PROGRESS = NodeFunc.convert("$PACKAGE_NAME:id/ad_progress_text") // The best way to detect an ad  
val PATH_SKIP = NodeFunc.convert("$PACKAGE_NAME:id/skip_ad_button") // The skip ad button, in the player screen  

The various popup ads, they are always contained inside engagement_panel_wrapper (portrait) and fullscreen_engagement_panel_holder (landscape)  
In Portrait mode, the Close button can be at 2 locations:  
engagement_panel_wrapper > header_container > 0 > 0 > 1  
or  
engagement_panel_wrapper > header_container > 0 > 1 > 1  
In Landscape, it seems to always be  
fullscreen_engagement_panel_holder > header_container > 0 > 0 > 1  

val PATH_EXPANDED_AD_ROOT_PORTRAIT = NodeFunc.convert("$PACKAGE_NAME:id/engagement_panel_wrapper")  
val PATH_EXPANDED_AD_BUTTON_PORTRAIT = NodeFunc.convert(arrayOf("$PACKAGE_NAME:id/engagement_panel_wrapper", "$PACKAGE_NAME:id/header_container", 0, 0, 1)) // The giant ad below/on the side, it can sometimes appear when you pause the video without an ad video being triggered  
val PATH_EXPANDED_AD_BUTTON_PAUSE_PORTRAIT = NodeFunc.convert(arrayOf("$PACKAGE_NAME:id/engagement_panel_wrapper", "$PACKAGE_NAME:id/header_container", 0, 1, 1)) // There is an extra item  

val PATH_EXPANDED_AD_BUTTON_PAUSE_LANDSCAPE = NodeFunc.convert(arrayOf("$PACKAGE_NAME:id/fullscreen_engagement_panel_holder", "$PACKAGE_NAME:id/header_container", 0, 0, 1)) // Landscape left side ad
  
The minimised ad is the first of the recommendation list  
val PATH_RECOMMENDATION_LIST_ROOT = NodeFunc.convert("$PACKAGE_NAME:id/watch_list") // The recommendation list root  
val PATH_MINIMISED_AD = NodeFunc.convert("$PACKAGE_NAME:id/main_companion_container") // The small ad below the video and above the title  

# Front page feed

The floating player  
val PATH_FLOATING_WATCH_PLAYER = NodeFunc.convert("$PACKAGE_NAME:id/floaty_bar_time_bar_view")  
val PATH_FLOATING_AD_PRESENCE = NodeFunc.convert("$PACKAGE_NAME:id/modern_miniplayer_ad_badge")  
val PATH_FLOATING_SKIP = NodeFunc.convert("$PACKAGE_NAME:id/modern_miniplayer_skip_ad_button")  

For the skip button, it's not obvious to choose what to use as target for the click  
ViewGroup --- next_gen_watch_layout_no_player_fragment_container --- 8 children  
    [7]ViewGroup --- contentDescription="Lecteur réduit" --- 4 children  
        [0]ImageView --- modern_miniplayer_overlay_action_button - 0 child  
        [1]ImageView --- modern_miniplayer_close --- 1 child  
        [2]TextView --- modern_miniplayer_ad_badge --- text="Sponsorisé" --- 1 child     
        [3]FrameLayout --- modern_miniplayer_skip_ad_button --- 1 child
            [0]LinearLayout --- skip_ad_button_container --- contentDescription= "Ignorer l'annonce" --- 1 child  
                [0]TextView --- skip_ad_button_text --- text="Ignorer" --- 0 child  


For feed ads  
val PATH_FEED_LIST_ROOT = NodeFunc.convert("$PACKAGE_NAME:id/results")  
