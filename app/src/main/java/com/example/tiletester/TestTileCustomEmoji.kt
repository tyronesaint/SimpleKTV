package com.example.tiletester

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class TestTileCustomEmoji : TileService() {
    companion object {
        var customEmoji: String = "🚀"
        var customSize: Int = 48
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.label = "E${customSize}"
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.icon = IconHelper.emojiIcon(this, customEmoji, customSize)
        qsTile?.updateTile()
        Log.d("TileTest", "CustomEmoji: emoji='$customEmoji' size=${customSize}dp")
    }
    override fun onClick() {}
}
