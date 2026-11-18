package com.example.stickerwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import com.example.stickerwidget.R

object WidgetPinHelper {

    fun isPinningSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return appWidgetManager.isRequestPinAppWidgetSupported
        }
        return false
    }

    /**
     * Triggers the Android system dialog asking the user to pin this sticker as a Home Screen widget
     */
    fun pinStickerAsWidget(
        context: Context,
        stickerId: String,
        stickerBitmap: Bitmap?
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            if (!appWidgetManager.isRequestPinAppWidgetSupported) {
                Toast.makeText(context, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
                return false
            }

            val myProvider = ComponentName(context, StickerWidgetProvider::class.java)
            val successCallback = Intent(context, StickerWidgetProvider::class.java).apply {
                action = StickerWidgetProvider.ACTION_PIN_SUCCESS
                putExtra(StickerWidgetProvider.EXTRA_STICKER_ID, stickerId)
            }

            val successPendingIntent = PendingIntent.getBroadcast(
                context,
                stickerId.hashCode(),
                successCallback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val bundle = Bundle()
            if (stickerBitmap != null) {
                val previewViews = RemoteViews(context.packageName, R.layout.widget_sticker).apply {
                    setImageViewBitmap(R.id.widget_sticker_image, stickerBitmap)
                }
                bundle.putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, previewViews)
            }

            return appWidgetManager.requestPinAppWidget(myProvider, bundle, successPendingIntent)
        } else {
            Toast.makeText(context, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
            return false
        }
    }
}
