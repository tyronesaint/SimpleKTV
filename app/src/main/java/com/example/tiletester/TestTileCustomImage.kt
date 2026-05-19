package com.example.tiletester

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TestTileCustomImage : TileService() {
    companion object {
        var customPath: String = ""
        var customSize: Int = 48
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.label = "图${customSize}"
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.icon = if (customPath.isNotEmpty()) {
            IconHelper.customImageIcon(this, customPath, customSize)
        } else {
            IconHelper.transparentBitmap(this, "?", customSize)
        }
        qsTile?.updateTile()
        Log.d("TileTest", "CustomImage: path='$customPath' size=${customSize}dp")
    }
    override fun onClick() {}
}
