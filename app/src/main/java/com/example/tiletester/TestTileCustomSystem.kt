package com.example.tiletester

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TestTileCustomSystem : TileService() {
    companion object {
        var customResId: Int = android.R.drawable.ic_menu_preferences
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.label = "系统"
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.icon = Icon.createWithResource(this, customResId)
        qsTile?.updateTile()
        Log.d("TileTest", "CustomSystem: resId=$customResId")
    }
    override fun onClick() {}
}
