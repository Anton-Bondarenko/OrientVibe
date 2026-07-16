package com.orientvibe.app

import android.view.View

class PanelStateManager(
    private val mapLoadingPanel: View,
    private val navigationPanel: View,
    private val navigationModePanel: View,
    private val leftArrowButton: View,
    private val rightArrowButton: View,
    private val onPanelChanged: (PanelState) -> Unit
) {
    private var currentPanel = PanelState.MAP_LOADING
    
    fun getCurrentPanel(): PanelState = currentPanel
    
    fun setPanelState(state: PanelState) {
        currentPanel = state
        
        when (state) {
            PanelState.MAP_LOADING -> {
                mapLoadingPanel.visibility = View.VISIBLE
                navigationPanel.visibility = View.GONE
                navigationModePanel.visibility = View.GONE
                leftArrowButton.visibility = View.GONE
                rightArrowButton.visibility = View.VISIBLE
            }
            PanelState.NAVIGATION -> {
                mapLoadingPanel.visibility = View.GONE
                navigationPanel.visibility = View.VISIBLE
                navigationModePanel.visibility = View.GONE
                leftArrowButton.visibility = View.VISIBLE
                rightArrowButton.visibility = View.VISIBLE
            }
            PanelState.NAVIGATION_MODE -> {
                mapLoadingPanel.visibility = View.GONE
                navigationPanel.visibility = View.GONE
                navigationModePanel.visibility = View.VISIBLE
                leftArrowButton.visibility = View.VISIBLE
                rightArrowButton.visibility = View.GONE
            }
        }
        
        onPanelChanged(state)
    }
    
    fun switchToMapLoadingPanel() {
        setPanelState(PanelState.MAP_LOADING)
    }
    
    fun switchToNavigationPanel() {
        setPanelState(PanelState.NAVIGATION)
    }
    
    fun switchToNavigationModePanel() {
        setPanelState(PanelState.NAVIGATION_MODE)
    }
    
    fun handleLeftArrow() {
        when (currentPanel) {
            PanelState.NAVIGATION -> switchToMapLoadingPanel()
            PanelState.NAVIGATION_MODE -> switchToNavigationPanel()
            else -> {}
        }
    }
    
    fun handleRightArrow(navigationReady: Boolean) {
        when (currentPanel) {
            PanelState.MAP_LOADING -> switchToNavigationPanel()
            PanelState.NAVIGATION -> {
                if (navigationReady) {
                    switchToNavigationModePanel()
                }
            }
            else -> {}
        }
    }
}
