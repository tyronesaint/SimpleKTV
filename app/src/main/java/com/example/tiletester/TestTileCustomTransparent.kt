package com.example.tiletester

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TestTileCustomTransparent : TileService() {
    companion object {
        var customText: String = "字"
        var customSize: Int = 48
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.label = "透${customSize}"
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.icon = IconHelper.transparentBitmap(this, customText, customSize)
        qsTile?.updateTile()
        Log.d("TileTest", "CustomTransparent: text='$customText' size=${customSize}dp")
    }
    override fun onClick() {}
}
