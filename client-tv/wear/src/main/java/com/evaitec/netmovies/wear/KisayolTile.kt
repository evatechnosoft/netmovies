package com.evaitec.netmovies.wear

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture

// Watch shortcut: Samsung tile launchers are paid/removed, so the app ships its own
// tile. Whole tile is one tap target that opens MainActivity.
class KisayolTile : TileService() {

  override fun onTileRequest(
    requestParams: RequestBuilders.TileRequest,
  ): ListenableFuture<TileBuilders.Tile> = done(
    TileBuilders.Tile.Builder()
      .setResourcesVersion(RES_VERSION)
      .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
      .build(),
  )

  override fun onTileResourcesRequest(
    requestParams: RequestBuilders.ResourcesRequest,
  ): ListenableFuture<ResourceBuilders.Resources> = done(
    ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build(),
  )

  private fun layout(): LayoutElementBuilders.LayoutElement {
    val open = ActionBuilders.LaunchAction.Builder()
      .setAndroidActivity(
        ActionBuilders.AndroidActivity.Builder()
          .setPackageName(packageName)
          .setClassName(MainActivity::class.java.name)
          .build(),
      )
      .build()
    val click = ModifiersBuilders.Clickable.Builder().setId("ac").setOnClick(open).build()
    return LayoutElementBuilders.Box.Builder()
      .setWidth(expand())
      .setHeight(expand())
      .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(click).build())
      .addContent(text("NetMovies", 26f, 0xFFE50914.toInt()))
      .build()
  }

  private fun text(value: String, size: Float, color: Int) =
    LayoutElementBuilders.Text.Builder()
      .setText(value)
      .setFontStyle(
        LayoutElementBuilders.FontStyle.Builder().setSize(sp(size)).setColor(argb(color)).build(),
      )
      .build()

  private fun <T> done(value: T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { it.set(value) }

  private companion object {
    const val RES_VERSION = "1"
  }
}
